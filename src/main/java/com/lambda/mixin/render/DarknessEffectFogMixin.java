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

import com.lambda.module.modules.render.NoRender;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.fog.DarknessEffectFogModifier;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin to disable darkness fog effect when NoRender is enabled.
 */
@Mixin(DarknessEffectFogModifier.class)
public class DarknessEffectFogMixin {
    @WrapMethod(method = "applyDarknessModifier")
    private float injectShouldApplyDarkness(LivingEntity cameraEntity, float darkness, float tickProgress, Operation<Float> original) {
        if (NoRender.INSTANCE.isDisabled() || !NoRender.getNoDarkness())
            return original.call(cameraEntity, darkness, tickProgress);

        return 0f;
    }
}
