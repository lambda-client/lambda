package com.lambda.mixin.world;

import com.lambda.client.module.modules.movement.Prevent;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.BlockCactus;
import net.minecraft.block.BlockFire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Block.class, BlockAir.class, BlockFire.class, BlockCactus.class})
public class MixinGetCollisionBB {

    private final Minecraft mc = Minecraft.getMinecraft();

    @Inject(method = "getCollisionBoundingBox", at = @At("HEAD"), cancellable = true)
    private void getCollisionBoundingBox(IBlockState blockState, IBlockAccess worldIn, BlockPos pos, CallbackInfoReturnable<AxisAlignedBB> cir) {
        if (mc.world != null && Prevent.INSTANCE.isEnabled()) {
            Block checkBlock = getBlock(pos);
            if ((checkBlock.equals(Blocks.FIRE) && Prevent.INSTANCE.getFire()) ||
                (checkBlock.equals(Blocks.CACTUS) && Prevent.INSTANCE.getCactus()) ||
                (!mc.world.isBlockLoaded(pos, false) && Prevent.INSTANCE.getUnloaded()) ||
                (pos.getY() < 0 && Prevent.INSTANCE.getVoid())) {
                cir.cancel();
                cir.setReturnValue(Block.FULL_BLOCK_AABB);
            }
        }
    }

    public Block getBlock(BlockPos var0) {
        return mc.world.getBlockState(var0).getBlock();
    }
}
