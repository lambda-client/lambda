package com.lambda.interaction.construction.context

import com.lambda.context.SafeContext
import com.lambda.interaction.rotation.RotationContext
import com.lambda.util.world.raycast.RayCastUtils.distanceTo
import net.minecraft.block.BlockState
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

data class BreakContext(
    override val pov: Vec3d,
    override val result: BlockHitResult,
    override val rotation: RotationContext,
    override val checkedState: BlockState,
    override var hand: Hand,
    val instantBreak: Boolean,
) : BuildContext {
    override val resultingPos: BlockPos
        get() = result.blockPos

    override val distance: Double by lazy {
        result.distanceTo(pov)
    }

    fun exposedSides(ctx: SafeContext) =
        Direction.entries.filter {
            ctx.world.isAir(result.blockPos.offset(it))
        }

    override val expectedState = checkedState.fluidState.blockState

    override fun compareTo(other: BuildContext): Int {
        return when (other) {
            is BreakContext -> compareBy<BreakContext> {
                it.distance
            }.compare(this, other)
            else -> 1
        }
    }
}
