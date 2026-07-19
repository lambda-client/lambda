
package com.minato.mixin.render;

import com.minato.module.modules.render.XRay;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderer.class)
public class SodiumBlockRendererMixin {
    @Inject(method = "renderModel", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/model/color/ColorProviderRegistry;getColorProvider(Lnet/minecraft/block/Block;)Lnet/caffeinemc/mods/sodium/client/model/color/ColorProvider;", shift = At.Shift.AFTER), cancellable = true)
    private void injectRenderModel(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
        if (XRay.INSTANCE.isEnabled() && !XRay.getBlockSelection().contains(state.getBlock()) && XRay.getOpacity() < 100)
            ci.cancel();
    }
}
