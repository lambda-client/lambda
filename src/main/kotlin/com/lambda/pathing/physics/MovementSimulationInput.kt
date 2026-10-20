package com.lambda.pathing.physics

import com.lambda.interaction.managers.rotating.Rotation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

data class MovementSimulationInput(
	val forward: Double = 0.0,
	val strafe: Double = 0.0,
	val jump: Boolean = false,
	val sneak: Boolean = false,
	val sprint: Boolean = false,
	val useItemSlowdown: Boolean = false,
	val rotation: Rotation? = null,
	val use: BlockUse? = null,
	val steering: Rotation? = null,
) {
	override fun toString(): String =
		"MovementSimulationInput(forward=$forward, strafe=$strafe, jump=$jump, sneak=$sneak, sprint=$sprint, " +
				"useItemSlowdown=$useItemSlowdown, rotation=$rotation" + (use?.let { ", use=$it" } ?: "") + ")"
}

data class BlockUse(val pos: BlockPos, val side: Direction, val hit: Vec3d, val open: Boolean)
