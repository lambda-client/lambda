/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.mixin.entity;

import com.lambda.Lambda;
import com.lambda.event.EventFlow;
import com.lambda.event.events.MovementEvent;
import com.lambda.event.events.PlayerEvent;
import com.lambda.event.events.TickEvent;
import com.lambda.interaction.PlayerPacketManager;
import com.lambda.interaction.request.rotating.RotationManager;
import com.lambda.module.modules.player.PortalGui;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lambda.module.modules.render.ViewModel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(value = ClientPlayerEntity.class, priority = Integer.MAX_VALUE)
public abstract class ClientPlayerEntityMixin extends EntityMixin {

    @Shadow
    public Input input;
    @Shadow
    private boolean autoJumpEnabled;

    @Shadow
    protected abstract void autoJump(float dx, float dz);

    @Shadow @Final protected MinecraftClient client;

    /**
     * Post movement events and applies the modified player velocity
     */
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    void onMove(MovementType movementType, Vec3d movement, CallbackInfo ci) {
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        if (self != Lambda.getMc().player) return;

        ci.cancel();

        float prevX = (float) self.getX();
        float prevZ = (float) self.getZ();

        EventFlow.post(new MovementEvent.Player.Pre(movementType, movement));
        super.move(movementType, self.getVelocity());
        EventFlow.post(new MovementEvent.Player.Post(movementType, movement));

        float currX = (float) self.getX();
        float currZ = (float) self.getZ();

        this.autoJump(currX - prevX, currZ - prevZ);
    }

    @Redirect(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick(ZF)V"))
    void processMovement(Input input, boolean slowDown, float slowDownFactor) {
        input.tick(slowDown, slowDownFactor);
        RotationManager.processRotations();
        RotationManager.BaritoneProcessor.processPlayerMovement(input, slowDown, slowDownFactor);
        EventFlow.post(new MovementEvent.InputUpdate(input, slowDown, slowDownFactor));
    }

    /**
     * Posts the {@link MovementEvent.Sprint} event
     * <pre>{@code
     * if (this.isSprinting()) {
     *     boolean bl8 = !this.input.hasForwardMovement() || !this.canSprint();
     *     boolean bl9 = bl8 || this.horizontalCollision && !this.collidedSoftly || this.isTouchingWater() && !this.isSubmergedInWater();
     *     if (this.isSwimming()) {
     *         if (!this.isOnGround() && !this.input.sneaking && bl8 || !this.isTouchingWater()) {
     *             this.setSprinting(false);
     *         }
     *     } else if (bl9) {
     *         this.setSprinting(false);
     *     }
     * }
     * }</pre>
     */
    @Redirect(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isSprinting()Z"))
    boolean isSprinting(ClientPlayerEntity entity) {
        return EventFlow.post(new MovementEvent.Sprint(entity.isSprinting())).getSprint();
    }

    @Inject(method = "isSneaking", at = @At(value = "HEAD"), cancellable = true)
    void redirectSneaking(CallbackInfoReturnable<Boolean> cir) {
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        if (self != Lambda.getMc().player) return;

        if (self.input == null) return;
        cir.setReturnValue(EventFlow.post(new MovementEvent.Sneak(self.input.sneaking)).getSneak());
    }

    /**
     * Overwrites the movement packet update function to use our code
     */
    @Inject(method = "sendMovementPackets", at = @At(value = "HEAD"), cancellable = true)
    void sendBegin(CallbackInfo ci) {
        ci.cancel();
        PlayerPacketManager.sendPlayerPackets();
        autoJumpEnabled = Lambda.getMc().options.getAutoJump().getValue();
    }

    @WrapMethod(method = "tick")
    void onTick(Operation<Void> original) {
        EventFlow.post(TickEvent.Player.Pre.INSTANCE);
        original.call();
        EventFlow.post(TickEvent.Player.Post.INSTANCE);
    }

    @Redirect(method = "tickNewAi", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
    float fixHeldItemYaw(ClientPlayerEntity instance) {
        return Objects.requireNonNullElse(RotationManager.getHandYaw(), instance.getYaw());
    }

    @Redirect(method = "tickNewAi", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"))
    float fixHeldItemPitch(ClientPlayerEntity instance) {
        return Objects.requireNonNullElse(RotationManager.getHandPitch(), instance.getPitch());
    }

    @Redirect(method = "swingHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;swingHand(Lnet/minecraft/util/Hand;)V"))
    private void adjustSwing(AbstractClientPlayerEntity instance, Hand hand) {
        ViewModel viewModel = ViewModel.INSTANCE;

        if (!viewModel.isEnabled()) {
            instance.swingHand(hand, false);
            return;
        }

        viewModel.adjustSwing(hand, instance);
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    public void damage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (EventFlow.post(new PlayerEvent.Damage(source, amount)).isCanceled()) cir.setReturnValue(false);
    }

    /**
     * Prevents the game from closing Guis when the player is in a nether portal
     * <pre>{@code
     * if (this.client.currentScreen != null && !this.client.currentScreen.shouldPause() && !(this.client.currentScreen instanceof DeathScreen)) {
     *     if (this.client.currentScreen instanceof HandledScreen) {
     *         this.closeHandledScreen();
     *     }
     *
     *     this.client.setScreen((Screen)null);
     * }
     * }</pre>
     */
    @Redirect(method = "updateNausea", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;currentScreen:Lnet/minecraft/client/gui/screen/Screen;"))
    Screen keepScreensInPortal(MinecraftClient instance) {
        if (PortalGui.INSTANCE.isEnabled()) return null;
        else return client.currentScreen;
    }
}
