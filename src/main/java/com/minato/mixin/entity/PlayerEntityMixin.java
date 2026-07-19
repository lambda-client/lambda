
package com.minato.mixin.entity;

import com.minato.Minato;
import com.minato.event.EventFlow;
import com.minato.event.events.MovementEvent;
import com.minato.interaction.handlers.GlideHandler;
import com.minato.interaction.managers.rotating.RotationManager;
import com.minato.module.modules.player.Reach;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "clipAtLedge", at = @At(value = "HEAD"), cancellable = true)
    private void injectSafeWalk(CallbackInfoReturnable<Boolean> cir) {
        MovementEvent.ClipAtLedge event = new MovementEvent.ClipAtLedge(((PlayerEntity) (Object) this).isSneaking());
        cir.setReturnValue(EventFlow.post(event).getClip());
    }

    @WrapOperation(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getYaw()F"))
    private float wrapHeadYaw(PlayerEntity instance, Operation<Float> original) {
        if ((Object) this != Minato.getMc().player) {
            return original.call(instance);
        }

        Float yaw = RotationManager.getHeadYaw();
        return (yaw != null) ? yaw : original.call(instance);
    }

    @SuppressWarnings("RedundantCast")
    @WrapMethod(method = "getBlockInteractionRange")
    private double wrapGetBlockInteractionRange(Operation<Double> original) {
        if ((PlayerEntity) (Object) this == Minato.getMc().player && Reach.INSTANCE.isEnabled()) return Reach.getBlockReach();
        return original.call();
    }

    @SuppressWarnings("RedundantCast")
    @WrapMethod(method = "getEntityInteractionRange")
    private double wrapGetEntityInteractionRange(Operation<Double> original) {
        if ((PlayerEntity) (Object) this == Minato.getMc().player && Reach.INSTANCE.isEnabled()) return Reach.getEntityReach();
        return original.call();
    }

    @Inject(method = "checkGliding", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;startGliding()V"), cancellable = true)
    private void injectCheckGliding(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this != Minato.getMc().player) return;
        if (GlideHandler.getOverridingGlide()) cir.setReturnValue(false);
    }
}
