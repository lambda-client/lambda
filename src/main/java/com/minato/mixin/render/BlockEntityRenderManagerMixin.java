
package com.minato.mixin.render;

import com.minato.graphics.outline.OutlineCapturingQueue;
import com.minato.graphics.outline.OutlineHandler;
import com.minato.graphics.outline.VertexCapture;
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
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockEntityRenderManager.class)
public class BlockEntityRenderManagerMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;render(Lnet/minecraft/client/render/block/entity/state/BlockEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V"))
    private <S extends BlockEntityRenderState> void wrapRenderQueue(BlockEntityRenderer<?, S> renderer, S renderState,
            MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState,
            Operation<Void> original) {
        BlockPos pos = renderState.pos;

        if (pos != null && OutlineHandler.shouldCapture(pos)) {
            VertexCapture.INSTANCE.beginCapture(pos);

            boolean outlineOnly = !Vec3d.ofCenter(pos).isInRange(cameraState.pos, renderer.getRenderDistance());
            OrderedRenderCommandQueueImpl wrappedQueue = new OutlineCapturingQueue((OrderedRenderCommandQueueImpl) queue, pos, outlineOnly);
            original.call(renderer, renderState, matrices, wrappedQueue, cameraState);

            VertexCapture.INSTANCE.endCapture();
        } else {
            original.call(renderer, renderState, matrices, queue, cameraState);
        }
    }
}
