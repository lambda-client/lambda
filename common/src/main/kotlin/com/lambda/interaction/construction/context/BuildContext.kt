package com.lambda.interaction.construction.context

import com.lambda.interaction.rotation.RotationContext
import net.minecraft.block.BlockState
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

interface BuildContext : Comparable<BuildContext> {
    val pov: Vec3d
    val result: BlockHitResult
    val distance: Double
    val expectedState: BlockState
    val checkedState: BlockState
    val hand: Hand
    val resultingPos: BlockPos
    val rotation: RotationContext

    override fun compareTo(other: BuildContext): Int {
        return compareBy<BuildContext> {
            it.distance
        }.compare(this, other)
    }
}
