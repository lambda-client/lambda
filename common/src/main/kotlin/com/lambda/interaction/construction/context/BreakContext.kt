package com.lambda.interaction.construction.context

import com.lambda.context.SafeContext
import com.lambda.interaction.rotation.RotationContext
import com.lambda.util.world.raycast.RayCastUtils.distanceTo
import net.minecraft.block.BlockState
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

data class BreakContext(
    val pov: Vec3d,
    val result: BlockHitResult,
    val rotation: RotationContext,
    val checkedState: BlockState,
    val hand: Hand,
    val instantBreak: Boolean,
) : BuildContext, ComparableContext {
    override val distance: Double by lazy {
        result.distanceTo(pov)
    }

    fun exposedSides(ctx: SafeContext) =
        Direction.entries.filter {
            ctx.world.isAir(result.blockPos.offset(it))
        }

    override val expectedState = checkedState.fluidState.blockState

    override fun compareTo(other: ComparableContext): Int {
        return when (other) {
            is BreakContext -> compareBy<BreakContext> {
                it.distance
            }.compare(this, other)
            else -> 1
        }
    }
}
