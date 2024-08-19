package com.lambda.mixin.entity;

import com.lambda.event.EventFlow;
import com.lambda.event.events.MovementEvent;
import com.lambda.interaction.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {
    @Inject(method = "clipAtLedge", at = @At(value = "HEAD"), cancellable = true)
    private void injectSafeWalk(CallbackInfoReturnable<Boolean> cir) {
        MovementEvent.ClipAtLedge event = new MovementEvent.ClipAtLedge(((PlayerEntity) (Object) this).isSneaking());
        cir.setReturnValue(EventFlow.post(event).getClip());
    }

    @Redirect(method = "tickNewAi", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getYaw()F"))
    private float injectHeadYaw(PlayerEntity instance) {
        if ((Object) this != MinecraftClient.getInstance().player) {
            return instance.getYaw();
        }

        Float yaw = RotationManager.getRenderYaw();
        return (yaw != null) ? yaw : instance.getYaw();
    }

    @Redirect(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getYaw()F"))
    private float injectAttackFix(PlayerEntity instance) {
        if ((Object) this != MinecraftClient.getInstance().player) {
            return instance.getYaw();
        }

        Float yaw = RotationManager.getMovementYaw();
        return (yaw != null) ? yaw : instance.getYaw();
    }
}
