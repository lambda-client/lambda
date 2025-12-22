/*
 * Copyright 2025 Lambda
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
import com.lambda.interaction.PlayerPacketHandler;
import com.lambda.interaction.managers.rotating.RotationManager;
import com.lambda.module.modules.player.PortalGui;
import com.lambda.module.modules.render.ViewModel;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.MovementType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(value = ClientPlayerEntity.class, priority = Integer.MAX_VALUE)
public abstract class ClientPlayerEntityMixin extends AbstractClientPlayerEntity {
    @Shadow public Input input;
    @Shadow @Final protected MinecraftClient client;
    @Shadow private boolean autoJumpEnabled;

    public ClientPlayerEntityMixin(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    @WrapOperation(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"))
    private void emitMovementEvents(ClientPlayerEntity instance, MovementType movementType, Vec3d vec3d, Operation<Void> original) {
        EventFlow.post(new MovementEvent.Player.Pre(movementType, vec3d));
        original.call(instance, movementType, vec3d);
        EventFlow.post(new MovementEvent.Player.Post(movementType, vec3d));
    }

    @WrapOperation(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick()V"))
    void processMovement(Input input, Operation<Void> original) {
        original.call(input);
        RotationManager.processRotations();
        RotationManager.redirectStrafeInputs(input);
        EventFlow.post(new MovementEvent.InputUpdate(input));
    }

    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void injectSendMovementPackets(CallbackInfo ci) {
        PlayerPacketHandler.sendPlayerPackets();
        autoJumpEnabled = Lambda.getMc().options.getAutoJump().getValue();
    }
    
    @WrapWithCondition(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;sendSprintingPacket()V"))
    private boolean wrapSendSprintingPackets(ClientPlayerEntity instance) { return false; }

    @ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isCamera()Z"))
    private boolean wrapIsCamera(boolean original) { return false; }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;sendSneakingPacket()V"))
    void sendSneakingPacket(ClientPlayerEntity entity, Operation<Void> original) {
        PlayerPacketHandler.sendSneakPackets();
    }

    @ModifyExpressionValue(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isSprinting()Z"))
    boolean isSprinting(boolean original) {
        return EventFlow.post(new MovementEvent.Sprint(original)).getSprint();
    }

    @Inject(method = "isSneaking", at = @At(value = "HEAD"), cancellable = true)
    void injectSneakingInput(CallbackInfoReturnable<Boolean> cir) {
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        if (self != Lambda.getMc().player) return;

        if (self.input == null) return;
        cir.setReturnValue(EventFlow.post(new MovementEvent.Sneak(self.input.playerInput.sneak())).getSneak());
    }

    @WrapMethod(method = "tick")
    void onTick(Operation<Void> original) {
        EventFlow.post(TickEvent.Player.Pre.INSTANCE);
        original.call();
        EventFlow.post(TickEvent.Player.Post.INSTANCE);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
    float fixHeldItemYaw(ClientPlayerEntity instance, Operation<Float> original) {
        return Objects.requireNonNullElse(RotationManager.getHandYaw(), original.call(instance));
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"))
    float fixHeldItemPitch(ClientPlayerEntity instance, Operation<Float> original) {
        return Objects.requireNonNullElse(RotationManager.getHandPitch(), original.call(instance));
    }

    @Inject(method = "swingHand", at = @At("HEAD"), cancellable = true)
    void onSwing(Hand hand, CallbackInfo ci) {
        if (EventFlow.post(new PlayerEvent.SwingHand(hand)).isCanceled()) ci.cancel();
    }

    @WrapOperation(method = "swingHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;swingHand(Lnet/minecraft/util/Hand;)V"))
    private void adjustSwing(ClientPlayerEntity instance, Hand hand, Operation<Void> original) {
        ViewModel viewModel = ViewModel.INSTANCE;

        if (!viewModel.isEnabled()) {
            original.call(instance, hand);
            return;
        }

        viewModel.adjustSwing(hand, instance);
    }

    @Inject(method = "updateHealth", at = @At("HEAD"))
    public void damage(float health, CallbackInfo ci) {
        EventFlow.post(new PlayerEvent.Health(health));
    }

    /**
     * Prevents the game from closing Guis when the player is in a nether portal
     * <pre>{@code
     * if (this.client.currentScreen != null
     *         && !this.client.currentScreen.shouldPause()
     *         && !(this.client.currentScreen instanceof DeathScreen)
     *         && !(this.client.currentScreen instanceof CreditsScreen)) {
     *     if (this.client.currentScreen instanceof HandledScreen) {
     *         this.closeHandledScreen();
     *     }
     *
     *     this.client.setScreen(null);
     * }
     * }</pre>
     */
    @ModifyExpressionValue(method = "tickNausea", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;currentScreen:Lnet/minecraft/client/gui/screen/Screen;"))
    Screen keepScreensInPortal(Screen original) {
        if (PortalGui.INSTANCE.isEnabled()) return null;
        else return original;
    }
}
