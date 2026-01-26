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
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.FluidRenderer;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(FluidRenderer.class)
public class FluidRendererMixin {
    @Unique
    private final ThreadLocal<Integer> opacity = new ThreadLocal<>();

    @WrapMethod(method = "render")
    private void injectRender(BlockRenderView world, BlockPos pos, VertexConsumer vertexConsumer, BlockState blockState, FluidState fluidState, Operation<Void> original) {
        if (XRay.INSTANCE.isDisabled()) {
            opacity.set(255);
            original.call(world, pos, vertexConsumer, blockState, fluidState);
        }

        int alpha = (int) (XRay.getOpacity() * 2.55);

        if (alpha > 0)
            this.opacity.set(alpha);
    }

    @WrapMethod(method = "vertex")
    private void injectVertex(VertexConsumer vertexConsumer, float x, float y, float z, float red, float green, float blue, float u, float v, int light, Operation<Void> original) {
        int alpha = this.opacity.get();

        if (alpha != 255)
            vertexConsumer.vertex(x, y, z).color((int) (red * 255), (int) (green * 255), (int) (blue * 255), alpha).texture(u, v).light(light).normal(0.0f, 1.0f, 0.0f);

        original.call(vertexConsumer, x, y, z, red, green, blue, u, v, light);
    }
}
