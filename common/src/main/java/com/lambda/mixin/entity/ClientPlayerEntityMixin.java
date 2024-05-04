package com.lambda.mixin.entity;

import com.lambda.Lambda;
import com.lambda.event.EventFlow;
import com.lambda.event.events.MovementEvent;
import com.lambda.interaction.PlayerPacketManager;
import com.lambda.interaction.RotationManager;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(value = ClientPlayerEntity.class, priority = Integer.MAX_VALUE)
public abstract class ClientPlayerEntityMixin extends EntityMixin {

    @Shadow protected abstract void autoJump(float dx, float dz);

    @Shadow public abstract boolean isUsingItem();

    @Shadow private boolean autoJumpEnabled;

    @Shadow public Input input;

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    void onMove(MovementType movementType, Vec3d movement, CallbackInfo ci) {
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
        if (self != Lambda.getMc().player) return;

        ci.cancel();

        float prevX = (float) self.getX();
        float prevZ = (float) self.getZ();

        EventFlow.post(new MovementEvent.Pre());
        super.move(movementType, self.getVelocity());
        EventFlow.post(new MovementEvent.Post());

        float currX = (float) self.getX();
        float currZ = (float) self.getZ();

        this.autoJump(currX - prevX, currZ - prevZ);
    }

    @Redirect(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    boolean onSlowDown(ClientPlayerEntity entity) {
        if (EventFlow.post(new MovementEvent.SlowDown()).isCanceled()) return false;
        return isUsingItem();
    }

    @Redirect(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick(ZF)V"))
    void processMovement(Input input, boolean slowDown, float slowDownFactor) {
        input.tick(slowDown, slowDownFactor);
        EventFlow.post(new MovementEvent.InputUpdate(input, slowDown, slowDownFactor));
    }

    @Redirect(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isSprinting()Z"))
    boolean isSprinting(ClientPlayerEntity entity) {
        return EventFlow.post(new MovementEvent.Sprint(entity.isSprinting())).getSprint();
    }

    @Inject(method = "sendMovementPackets", at = @At(value = "HEAD"), cancellable = true)
    void sendBegin(CallbackInfo ci) {
        ci.cancel();
        PlayerPacketManager.sendPlayerPackets();
        autoJumpEnabled = Lambda.getMc().options.getAutoJump().getValue();
    }

    @Redirect(method = "tickNewAi", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
    float fixHeldItemYaw(ClientPlayerEntity instance) {
        return Objects.requireNonNullElse(RotationManager.getHandYaw(), instance.getYaw());
    }

    @Redirect(method = "tickNewAi", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"))
    float fixHeldItemPitch(ClientPlayerEntity instance) {
        return Objects.requireNonNullElse(RotationManager.getHandPitch(), instance.getPitch());
    }
}
