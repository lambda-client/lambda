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

import com.lambda.graphics.outline.OutlineManager;
import com.lambda.graphics.outline.OutlineCapturingQueue;
import com.lambda.graphics.outline.VertexCapture;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockEntityRenderManager.class)
public class BlockEntityRenderManagerMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;render(Lnet/minecraft/client/render/block/entity/state/BlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V"))
    private <S extends BlockEntityRenderState> void wrapRenderQueue(BlockEntityRenderer<?, S> renderer, S renderState, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, Operation<Void> original) {
        BlockPos pos = renderState.pos;

        if (pos != null && OutlineManager.isBlockCaptured(pos)) {
            VertexCapture.INSTANCE.beginCapture(pos);

            OrderedRenderCommandQueueImpl wrappedQueue = new OutlineCapturingQueue((OrderedRenderCommandQueueImpl) queue, pos);
            original.call(renderer, renderState, matrices, wrappedQueue, cameraState);

            VertexCapture.INSTANCE.endCapture();
        } else {
            original.call(renderer, renderState, matrices, queue, cameraState);
        }
    }
}
