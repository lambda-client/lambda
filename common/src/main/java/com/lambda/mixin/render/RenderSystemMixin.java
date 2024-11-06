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
