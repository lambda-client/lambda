package com.lambda.pathing.physics

import com.lambda.pathing.core.VoxelPos
import net.minecraft.util.math.Direction

sealed interface Interaction {
	val target: VoxelPos

	data class Use(override val target: VoxelPos, val face: Direction) : Interaction

	data class Attack(override val target: VoxelPos, val face: Direction) : Interaction
}
