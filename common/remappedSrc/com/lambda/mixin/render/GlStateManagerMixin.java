package com.lambda.mixin.render;

import com.lambda.graphics.gl.GlStateUtils;
import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.opengl.GL11.*;

@Mixin(GlStateManager.class)
public class GlStateManagerMixin {
    @Inject(method = "_enableDepthTest", at = @At("TAIL"), remap = false)
    private static void depthTestEnable(CallbackInfo ci) {
        GlStateUtils.capSet(GL_DEPTH_TEST, true);
    }

    @Inject(method = "_disableDepthTest", at = @At("TAIL"), remap = false)
    private static void depthTestDisable(CallbackInfo ci) {
        GlStateUtils.capSet(GL_DEPTH_TEST, false);
    }

    @Inject(method = "_depthMask", at = @At("TAIL"), remap = false)
    private static void depthMask(boolean mask, CallbackInfo ci) {
        GlStateUtils.capSet(GL_DEPTH, mask);
    }

    @Inject(method = "_enableBlend", at = @At("TAIL"), remap = false)
    private static void blendEnable(CallbackInfo ci) {
        GlStateUtils.capSet(GL_BLEND, true);
    }

    @Inject(method = "_disableBlend", at = @At("TAIL"), remap = false)
    private static void blendDisable(CallbackInfo ci) {
        GlStateUtils.capSet(GL_BLEND, false);
    }

    @Inject(method = "_enableCull", at = @At("TAIL"), remap = false)
    private static void cullEnable(CallbackInfo ci) {
        GlStateUtils.capSet(GL_CULL_FACE, true);
    }

    @Inject(method = "_disableCull", at = @At("TAIL"), remap = false)
    private static void cullDisable(CallbackInfo ci) {
        GlStateUtils.capSet(GL_CULL_FACE, false);
    }
}
