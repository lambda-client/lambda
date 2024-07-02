package com.lambda.mixin.render;

import com.lambda.module.modules.render.NoRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void onRenderFireOverlay(
            MinecraftClient mc,
            MatrixStack matrixStack,
            CallbackInfo ci
    ) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoBurning()) ci.cancel();
    }

    @ModifyArg(method = "renderFireOverlay", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"), index = 1)
    private static float onRenderFireOverlayTranslate(float x) {
        if (NoRender.INSTANCE.isEnabled()) {
            return (float) NoRender.getFireOverlayYOffset();
        } else {
            return -0.3f;
        }
    }

    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true)
    private static void onRenderUnderwaterOverlay(
            MinecraftClient mc,
            MatrixStack matrixStack,
            CallbackInfo ci
    ) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoUnderwater()) ci.cancel();
    }

    @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
    private static void onRenderInWallOverlay(
            Sprite sprite,
            MatrixStack matrices,
            CallbackInfo ci
    ) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoInWall()) ci.cancel();
    }
}
