/*
 * Copyright 2026 Lambda
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

import com.lambda.event.EventFlow;
import com.lambda.event.events.*;
import com.lambda.interaction.handler.handlers.BaritoneHandler;
import com.lambda.interaction.manager.managers.rotating.RotationManager;
import com.lambda.module.modules.movement.NoJumpCooldown;
import com.lambda.module.modules.movement.elytrafly.ElytraFly;
import com.lambda.module.modules.player.PortalGui;
import com.lambda.module.modules.render.ViewModel;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.MovementType;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

import static com.lambda.Lambda.getMc;
import static com.lambda.interaction.managers.rotating.Rotation.dist;

@Mixin(value = ClientPlayerEntity.class, priority = Integer.MAX_VALUE)
public abstract class ClientPlayerEntityMixin extends AbstractClientPlayerEntity {
    public ClientPlayerEntityMixin(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    @WrapMethod(method = "tick")
    void onTick(Operation<Void> original) {
        EventFlow.post(TickEvent.Player.Pre.INSTANCE);
        original.call();
        EventFlow.post(TickEvent.Player.Post.INSTANCE);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void injectTick(CallbackInfo ci, @Share(namespace = "shared_rotations", value = "target_rotation") final LocalRef<Vec2f> targetRotation) {
        if (BaritoneHandler.isActive()) return;

        if (RotationManager.getRequests().stream().anyMatch(Objects::nonNull)) {
            final var activeRotation = RotationManager.getActiveRotation();
            targetRotation.set(new Vec2f(activeRotation.getYawF(), activeRotation.getPitchF()));
        }
    }

    @Inject(method = "openEditSignScreen", at = @At("HEAD"), cancellable = true)
    private void onOpenEditSignScreen(SignBlockEntity sign, boolean front, CallbackInfo ci) {
        if (EventFlow.post(new GuiEvent.SignEditorOpen(sign, front)).isCanceled()) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"))
    private void wrapMove(ClientPlayerEntity instance, MovementType movementType, Vec3d vec3d, Operation<Void> original) {
        MovementEvent.Player.Pre preEvent = new MovementEvent.Player.Pre(movementType, vec3d);
        EventFlow.post(preEvent);
        vec3d = preEvent.getMovement();
        original.call(instance, movementType, vec3d);
        MovementEvent.Player.Post postEvent = new MovementEvent.Player.Post(movementType, vec3d);
        EventFlow.post(postEvent);
    }

    @WrapOperation(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick()V"))
    void wrapTick(Input input, Operation<Void> original) {
        original.call(input);
        RotationManager.processRotations();
        if (!BaritoneHandler.isActive()) {
            RotationManager.redirectStrafeInputs(input);
        }
        EventFlow.post(new MovementEvent.InputUpdate(input));
    }

    @Inject(method = "tickMovement", at = @At("RETURN"))
    private void injectTickMovement(CallbackInfo ci) {
        if (NoJumpCooldown.INSTANCE.isEnabled() || ElytraFly.getBounceMode().isEnabled()) jumpingCooldown = 0;
    }

    @ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
    private float modifyGetYaw(float original) {
        if (BaritoneHandler.isActive()) return original;

        final var yaw = RotationManager.getHeadYaw();
        return yaw != null ? yaw : original;
    }

    @ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"))
    private float modifyGetPitch(float original) {
        if (BaritoneHandler.isActive()) return original;

        final var pitch = RotationManager.getHeadPitch();
        return pitch != null ? pitch : original;
    }

    @WrapOperation(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private void wrapSendPacket(ClientPlayNetworkHandler instance, Packet<?> packet, Operation<Void> original) {
        var event = EventFlow.post(new PlayerPacketEvent.Send((PlayerMoveC2SPacket) packet));
        if (event.isCanceled()) return;
        original.call(instance, event.getPacket());
    }

    @ModifyVariable(method = "sendMovementPackets", at = @At(value = "STORE"), ordinal = 1)
    private boolean modifyBl2(boolean original) {
        boolean rotationMismatch = dist(RotationManager.getActiveRotation(), RotationManager.getServerRotation()) > 0.00001;
        return original || rotationMismatch;
    }

    @Inject(method = "sendMovementPackets", at = @At("TAIL"))
    private void injectSendMovementPacketsReturn(CallbackInfo ci) {
        if (!BaritoneHandler.isActive()) { RotationManager.onRotationSend(); }
        EventFlow.post(new PlayerPacketEvent.Post());
    }

    @ModifyExpressionValue(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isSprinting()Z"))
    boolean modifyIsSprinting(boolean original) {
        return EventFlow.post(new MovementEvent.Sprint(original)).getSprint();
    }

    @ModifyReturnValue(method = "isSneaking", at = @At("RETURN"))
    boolean injectSneakingInput(boolean original) {
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        if (self != getMc().player || self.input == null) return original;

        return EventFlow.post(new MovementEvent.Sneak(self.input.playerInput.sneak())).getSneak();
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
    float wrapGetYaw(ClientPlayerEntity instance, Operation<Float> original) {
        return BaritoneHandler.isActive() ? original.call(instance) : Objects.requireNonNullElse(RotationManager.getHandYaw(), original.call(instance));
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"))
    float wrapGetPitch(ClientPlayerEntity instance, Operation<Float> original) {
        return BaritoneHandler.isActive() ? original.call(instance) : Objects.requireNonNullElse(RotationManager.getHandPitch(), original.call(instance));
    }

    @Inject(method = "swingHand", at = @At("HEAD"), cancellable = true)
    void injectSwingHand(Hand hand, CallbackInfo ci) {
        if (EventFlow.post(new PlayerEvent.SwingHand(hand)).isCanceled()) ci.cancel();
    }

    @WrapOperation(method = "swingHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;swingHand(Lnet/minecraft/util/Hand;)V"))
    private void wrapSwingHand(ClientPlayerEntity instance, Hand hand, Operation<Void> original) {
        ViewModel viewModel = ViewModel.INSTANCE;

        if (!viewModel.isEnabled()) {
            original.call(instance, hand);
            return;
        }

        viewModel.adjustSwing(hand, instance);
    }

    @Inject(method = "updateHealth", at = @At("HEAD"))
    public void injectUpdateHealth(float health, CallbackInfo ci) {
        EventFlow.post(new PlayerEvent.Health(health));
    }

    /**
     * Prevents the game from closing Guis when the player is in a nether portal
     * 
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
    @ModifyExpressionValue(method = "tickNausea", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;currentScreen:Lnet/minecraft/client/gui/screen/Screen;", opcode = Opcodes.GETFIELD))
    Screen modifyCurrentScreen(Screen original) {
        if (PortalGui.INSTANCE.isEnabled()) return null;
        else return original;
    }
}
