package com.lambda.mixin.baritone;

import baritone.Baritone;
import baritone.api.utils.Rotation;
import baritone.utils.player.BaritonePlayerContext;
import com.lambda.interaction.RotationManager;
import com.lambda.util.BaritoneUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BaritonePlayerContext.class, remap = false) // fix compileJava warning
public class MixinBaritonePlayerContext {
    @Shadow
    @Final
    private Baritone baritone;

    // Let baritone know the actual rotation
    @Inject(method = "playerRotations", at = @At("HEAD"), cancellable = true, remap = false)
    void syncRotationWithBaritone(CallbackInfoReturnable<Rotation> cir) {
        if (baritone != BaritoneUtils.getPrimary()) return;

        RotationManager rm = RotationManager.INSTANCE;
        cir.setReturnValue(new Rotation((float) rm.getCurrentRotation().getYaw(), (float) rm.getCurrentRotation().getPitch()));
    }
}
