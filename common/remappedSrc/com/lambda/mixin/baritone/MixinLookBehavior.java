package com.lambda.mixin.baritone;

import baritone.api.event.events.PlayerUpdateEvent;
import baritone.api.event.events.RotationMoveEvent;
import baritone.api.utils.Rotation;
import baritone.behavior.LookBehavior;
import com.lambda.interaction.RotationManager;
import com.lambda.util.BaritoneUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LookBehavior.class)
public class MixinLookBehavior {
    // Redirect baritone's rotations into our rotation engine
    @Inject(method = "updateTarget", at = @At("HEAD"), remap = false, cancellable = true)
    void onTargetUpdate(Rotation rotation, boolean blockInteract, CallbackInfo ci) {
        LookBehavior instance = ((LookBehavior) (Object) this);
        if (instance.baritone != BaritoneUtils.getPrimary()) return;

        RotationManager.BaritoneProcessor.handleBaritoneRotation(rotation.getYaw(), rotation.getPitch());
        ci.cancel();
    }

    @Inject(method = "onPlayerUpdate", at = @At("HEAD"), remap = false, cancellable = true)
    void onUpdate(PlayerUpdateEvent event, CallbackInfo ci) {
        LookBehavior instance = ((LookBehavior) (Object) this);
        if (instance.baritone != BaritoneUtils.getPrimary()) return;

        ci.cancel();
    }

    @Inject(method = "onPlayerRotationMove", at = @At("HEAD"), remap = false, cancellable = true)
    void onMovementUpdate(RotationMoveEvent event, CallbackInfo ci) {
        LookBehavior instance = ((LookBehavior) (Object) this);
        if (instance.baritone != BaritoneUtils.getPrimary()) return;

        ci.cancel();
    }
}
