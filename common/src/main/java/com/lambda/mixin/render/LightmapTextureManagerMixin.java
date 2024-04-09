package com.lambda.mixin.render;

import com.lambda.module.modules.render.Fullbright;
import com.lambda.module.modules.render.NoRender;
import com.lambda.module.modules.render.XRay;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {
    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/NativeImage;setColor(III)V"))
    private void updateModify(Args args) {
        if (Fullbright.INSTANCE.isEnabled() || XRay.INSTANCE.isEnabled()) {
            args.set(2, 0xFFFFFFFF);
        }
    }

    @Inject(method = "getDarknessFactor(F)F", at = @At("HEAD"), cancellable = true)
    private void getDarknessFactor(float tickDelta, CallbackInfoReturnable<Float> info) {
        if (NoRender.getNoDarkness()) info.setReturnValue(0.0f);
    }
}
