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
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BlockModelRenderer.class)
public class BlockModelRendererMixin {
    @Unique private final ThreadLocal<Integer> opacity = new ThreadLocal<>();

    @Inject(method = {"renderSmooth", "renderFlat"}, at = @At("HEAD"), cancellable = true)
    private void injectRenderSmoothFlat(BlockRenderView world, List<BlockModelPart> parts, BlockState state, BlockPos pos, MatrixStack matrices, VertexConsumer vertexConsumer, boolean cull, int overlay, CallbackInfo ci) {
        if (XRay.INSTANCE.isDisabled()) {
            this.opacity.set(-1);
            return;
        }
        int alpha = (int) (XRay.getOpacity() * 2.55);

        if (alpha == 0) ci.cancel();
        else this.opacity.set(alpha);
    }

    @ModifyConstant(method = "renderQuad", constant = @Constant(floatValue = 1, ordinal = 3))
    private float modifyAlpha(float original) {
        int alpha = this.opacity.get();
        return alpha == -1 ? original : alpha / 255f;
    }
}
