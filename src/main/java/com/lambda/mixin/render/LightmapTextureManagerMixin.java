/*
 * Copyright 2025 Lambda
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

import com.lambda.module.modules.render.Fullbright;
import com.lambda.module.modules.render.NoRender;
import com.lambda.module.modules.render.XRay;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to override lightmap for Fullbright/XRay and disable darkness effect.
 *
 * Note: In 1.21.11, the lightmap rendering was rewritten to use RenderPass with shaders.
 * We override the texture after normal rendering completes.
 */
@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {
    @Shadow @Final private GpuTexture glTexture;

    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;pop()V", shift = At.Shift.BEFORE))
    private void injectUpdate(float tickProgress, CallbackInfo ci) {
        if (Fullbright.INSTANCE.isEnabled() || XRay.INSTANCE.isEnabled()) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(glTexture, ColorHelper.fullAlpha(ColorHelper.getWhite(1.0f)));
        }
    }

    @ModifyReturnValue(method = "getDarkness", at = @At("RETURN"))
    private float modifyGetDarkness(float original, LivingEntity entity, float factor, float tickProgress) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoDarkness()) return 0.0f;
        return original;
    }
}
