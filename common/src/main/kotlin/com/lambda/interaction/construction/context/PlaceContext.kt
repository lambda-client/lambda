package com.lambda.interaction.construction.context

import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.rotation.RotationContext
import com.lambda.util.BlockUtils
import net.minecraft.block.BlockState
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

data class PlaceContext(
    val pov: Vec3d,
    val result: BlockHitResult,
    val rotation: RotationContext,
    override val distance: Double,
    override val expectedState: BlockState,
    override val checkedState: BlockState,
    val targetState: TargetState,
    override val hand: Hand,
    val sneak: Boolean,
    val insideBlock: Boolean,
) : BuildContext {
    override val resultingPos: BlockPos
        get() = result.blockPos.offset(result.side)

    override fun compareTo(other: ComparableContext): Int {
        return when (other) {
            is PlaceContext -> compareBy<PlaceContext> {
                BlockUtils.fluids.indexOf(it.checkedState.fluidState.fluid)
            }.thenByDescending {
                it.checkedState.fluidState.level
            }.thenBy {
                it.hand
            }.thenBy {
                it.sneak
            }.thenBy {
                it.distance
            }.thenBy {
                it.insideBlock
            }.compare(this, other)
            else -> 1
        }
    }
}