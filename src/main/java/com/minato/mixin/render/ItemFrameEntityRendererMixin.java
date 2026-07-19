
package com.minato.mixin.render;

import com.minato.graphics.outline.OutlineCapturingQueue;
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
        if (queue instanceof OutlineCapturingQueue minatoQueue) {
            minatoQueue.captureItemFrameModel(matrices, renderLayer, model, r, g, b, light, overlay, outlineColor);
        } else original.call(queue, matrices, renderLayer, model, r, g, b, light, overlay, outlineColor);
    }
}
