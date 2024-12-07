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
import com.lambda.event.events.PlayerEvent;
import com.lambda.event.events.MovementEvent;
import com.lambda.event.events.TickEvent;
import com.lambda.interaction.PlayerPacketManager;
import com.lambda.interaction.RotationManager;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
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
        RotationManager.BaritoneProcessor.processPlayerMovement(input, slowDown, slowDownFactor);
        EventFlow.post(new MovementEvent.InputUpdate(input, slowDown, slowDownFactor));
    }

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

    @Inject(method = "sendMovementPackets", at = @At(value = "HEAD"), cancellable = true)
    void sendBegin(CallbackInfo ci) {
        ci.cancel();
        PlayerPacketManager.sendPlayerPackets();
        autoJumpEnabled = Lambda.getMc().options.getAutoJump().getValue();

        RotationManager.update();
    }

    @Inject(method = "tick", at = @At(value = "HEAD"))
    void onTickPre(CallbackInfo ci) {
        EventFlow.post(new TickEvent.Player.Pre());
    }

    @Inject(method = "tick", at = @At(value = "RETURN"))
    void onTickPost(CallbackInfo ci) {
        EventFlow.post(new TickEvent.Player.Post());
    }

    @Redirect(method = "tickNewAi", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
    float fixHeldItemYaw(ClientPlayerEntity instance) {
        return Objects.requireNonNullElse(RotationManager.getHandYaw(), instance.getYaw());
    }

    @Redirect(method = "tickNewAi", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"))
    float fixHeldItemPitch(ClientPlayerEntity instance) {
        return Objects.requireNonNullElse(RotationManager.getHandPitch(), instance.getPitch());
    }

    @Inject(method = "swingHand", at = @At("HEAD"), cancellable = true)
    void onSwingHandPre(Hand hand, CallbackInfo ci) {
        if (EventFlow.post(new PlayerEvent.SwingHand(hand)).isCanceled()) ci.cancel();
    }
}
