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

import com.lambda.graphics.outline.OutlineCapturingQueue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemFrameEntityRenderer;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemFrameEntityRenderer.class)
public class ItemFrameEntityRendererMixin {

    @WrapOperation(method = "render(Lnet/minecraft/client/render/entity/state/ItemFrameEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;submitBlockStateModel(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/render/model/BlockStateModel;FFFIII)V"))
    private void captureItemFrameModel(OrderedRenderCommandQueue queue, MatrixStack matrices, RenderLayer renderLayer, BlockStateModel model, float r, float g, float b, int light, int overlay, int outlineColor, Operation<Void> original) {
        if (queue instanceof OutlineCapturingQueue lambdaQueue) {
            lambdaQueue.captureItemFrameModel(matrices, renderLayer, model, r, g, b, light, overlay, outlineColor);
        } else original.call(queue, matrices, renderLayer, model, r, g, b, light, overlay, outlineColor);
    }
}
