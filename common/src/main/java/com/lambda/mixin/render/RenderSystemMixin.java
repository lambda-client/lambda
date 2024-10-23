package com.lambda.mixin.render;

import com.lambda.module.modules.render.WorldColors;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public class RenderSystemMixin {
    @Shadow
    @Final
    private static float[] shaderFogColor;

    @Inject(method = "_setShaderFogColor", at = @At(value = "HEAD"), cancellable = true)
    private static void onSetShaderFogColor(float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (WorldColors.INSTANCE.isEnabled() && WorldColors.getCustomFog()) {
            ci.cancel();
            shaderFogColor[0] = WorldColors.getFogColor().getRed() / 255f;
            shaderFogColor[1] = WorldColors.getFogColor().getGreen() / 255f;
            shaderFogColor[2] = WorldColors.getFogColor().getBlue() / 255f;
            shaderFogColor[3] = WorldColors.getFogColor().getAlpha() / 255f;
        }
    }
}
