package com.lambda.interaction.construction.context

import net.minecraft.block.BlockState
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

data class BreakContext(
//    val hitVec: Vec3d,
    val hitPos: BlockPos,
    val exposedSides: Int,
//    val side: Direction,
    override val distance: Double,
//    var hand: Hand,
//    val pointOfView: Vec3d,
//    val blockState: BlockState,
) : BuildContext, ComparableContext {
//    override val resultingPos = hitPos
//    override fun toBlockHitResult() = BlockHitResult(hitVec, side, hitPos, false)

//    val expectedBlockState = blockState.fluidState.blockState

    override fun compareTo(other: ComparableContext): Int {
        return when (other) {
            is BreakContext -> compareBy<BreakContext> {
                it.distance
            }.compare(this, other)
            else -> 1
        }
    }
}
