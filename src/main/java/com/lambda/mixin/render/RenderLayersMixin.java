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

import com.lambda.module.modules.render.XRay;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.BlockRenderLayers;
import net.minecraft.fluid.FluidState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Mixin to make blocks render as translucent for XRay functionality.
 * Note: In 1.21.11, RenderLayers was split - BlockRenderLayers now handles
 * block/fluid layer determination and returns BlockRenderLayer enum instead of RenderLayer.
 */
@Mixin(BlockRenderLayers.class)
public class RenderLayersMixin {
    @WrapMethod(method = "getBlockLayer")
    private static BlockRenderLayer injectGetBlockLayer(BlockState state, Operation<BlockRenderLayer> original) {
        final var opacity = XRay.getOpacity();
        if (XRay.INSTANCE.isDisabled() || XRay.isSelected(state) || opacity <= 0 || opacity >= 100)
            return original.call(state);

        return BlockRenderLayer.TRANSLUCENT;
    }

    @WrapMethod(method = "getFluidLayer")
    private static BlockRenderLayer injectGetFluidLayer(FluidState state, Operation<BlockRenderLayer> original) {
        final var opacity = XRay.getOpacity();
        if (XRay.INSTANCE.isDisabled() || opacity <= 0 || opacity >= 100)
            return original.call(state);

        return BlockRenderLayer.TRANSLUCENT;
    }
}
