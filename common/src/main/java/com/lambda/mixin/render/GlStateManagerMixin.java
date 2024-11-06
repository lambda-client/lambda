/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
