package com.lambda.client.util.world

import com.lambda.client.util.Wrapper
import com.lambda.client.util.items.blockBlacklist
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.init.Blocks
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos

val IBlockState.isBlacklisted: Boolean
    get() = blockBlacklist.contains(this.block)

val IBlockState.isLiquid: Boolean
    get() = this.material.isLiquid

val IBlockState.isWater: Boolean
    get() = this.block == Blocks.WATER

val IBlockState.isReplaceable: Boolean
    get() = this.material.isReplaceable

val IBlockState.isFullBox: Boolean
    get() = Wrapper.world?.let {
        this.getCollisionBoundingBox(it, BlockPos.ORIGIN)
    } == Block.FULL_BLOCK_AABB

fun WorldClient.getSelectedBox(pos: BlockPos): AxisAlignedBB =
    this.getBlockState(pos).getSelectedBoundingBox(this, pos)

fun WorldClient.getCollisionBox(pos: BlockPos): AxisAlignedBB? =
    this.getBlockState(pos).getCollisionBoundingBox(this, pos)