package com.lambda.mixin;

import com.lambda.event.EventFlow;
import com.lambda.event.events.MovementEvent;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {
    @Inject(method = "clipAtLedge", at = @At(value = "HEAD"), cancellable = true)
    private void injectSafeWalk(CallbackInfoReturnable<Boolean> cir) {
        if (EventFlow.post(new MovementEvent.ClipAtLedge()).isCanceled()) {
            cir.setReturnValue(true);
        }
    }
}
