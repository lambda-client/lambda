
package com.minato.mixin.render;

import com.minato.module.modules.render.XRay;
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
        if (!XRay.getBlockSelection().contains(state.getBlock())) cir.setReturnValue(BlockRenderLayer.TRANSLUCENT);
    }

    @Inject(method = "getFluidLayer", at = @At("HEAD"), cancellable = true)
    private static void injectGetFluidLayer(FluidState state, CallbackInfoReturnable<BlockRenderLayer> cir) {
        if (XRay.INSTANCE.isDisabled()) return;
        final var opacity = XRay.getOpacity();
        if (opacity > 0 && opacity < 100) cir.setReturnValue(BlockRenderLayer.TRANSLUCENT);
    }
}
