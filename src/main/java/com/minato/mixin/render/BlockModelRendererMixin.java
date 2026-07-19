
package com.minato.mixin.render;

import com.minato.module.modules.render.XRay;
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
