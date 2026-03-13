/*
 * Copyright 2026 Lambda
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
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.BlockRenderLayers;
import net.minecraft.fluid.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockRenderLayers.class)
public class RenderLayersMixin {
    @Inject(method = "getBlockLayer", at = @At("HEAD"), cancellable = true)
    private static void injectGetBlockLayer(BlockState state, CallbackInfoReturnable<BlockRenderLayer> cir) {
        if (XRay.INSTANCE.isDisabled()) return;
        final var opacity = XRay.getOpacity();
        if (opacity <= 0 || opacity >= 100) return;
        if (!XRay.isSelected(state)) cir.setReturnValue(BlockRenderLayer.TRANSLUCENT);
    }

    @Inject(method = "getFluidLayer", at = @At("HEAD"), cancellable = true)
    private static void injectGetFluidLayer(FluidState state, CallbackInfoReturnable<BlockRenderLayer> cir) {
        if (XRay.INSTANCE.isDisabled()) return;
        final var opacity = XRay.getOpacity();
        if (opacity > 0 && opacity < 100) cir.setReturnValue(BlockRenderLayer.TRANSLUCENT);
    }
}
