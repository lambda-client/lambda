package com.lambda.mixin.render;

import com.lambda.module.modules.render.Fullbright;
import com.lambda.module.modules.render.NoRender;
import com.lambda.module.modules.render.XRay;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {
    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/NativeImage;setColor(III)V"), index = 2)
    private int updateModify(int color) {
        if (Fullbright.INSTANCE.isEnabled() || XRay.INSTANCE.isEnabled()) {
            return 0xFFFFFFFF;
        }
        return color;
    }

    @Inject(method = "getDarknessFactor(F)F", at = @At("HEAD"), cancellable = true)
    private void getDarknessFactor(float tickDelta, CallbackInfoReturnable<Float> info) {
        if (NoRender.getNoDarkness()) info.setReturnValue(0.0f);
    }
}
