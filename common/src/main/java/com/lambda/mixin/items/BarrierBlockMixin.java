package com.lambda.mixin.items;

import com.lambda.module.modules.render.BlockESP;
import net.minecraft.block.BarrierBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BarrierBlock.class)
public class BarrierBlockMixin {

    @Inject(method = "getRenderType", at = @At(value = "RETURN"), cancellable = true)
    private void getRenderType(BlockState state, CallbackInfoReturnable<BlockRenderType> cir) {
        if (BlockESP.INSTANCE.isEnabled()
                && BlockESP.getBarrier()
                && state.getBlock() == Blocks.BARRIER
        ) cir.setReturnValue(BlockRenderType.MODEL);
    }
}
