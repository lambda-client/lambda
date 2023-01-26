package com.lambda.mixin.world;

import com.lambda.client.module.modules.player.NoGhostBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemBlock.class)
public class MixinItemBlock {

    private final Minecraft mc = Minecraft.getMinecraft();

    @Redirect(method = "placeBlockAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z"))
    private boolean ignoreSetBlockState(World instance, BlockPos p_setBlockState_1_, IBlockState p_setBlockState_2_, int p_setBlockState_3_) {
        if (NoGhostBlocks.INSTANCE.isEnabled() && !mc.isSingleplayer()) {
            return true;
        } else {
            return instance.setBlockState(p_setBlockState_1_, p_setBlockState_2_, p_setBlockState_3_);
        }
    }
}