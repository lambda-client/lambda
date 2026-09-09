package com.lambda.pathing.physics

import com.lambda.interaction.managers.rotating.Rotation
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d

data class MovementSimulationTick(
	val position: Vec3d,
	val rotation: Rotation,
	val velocity: Vec3d,
	val boundingBox: Box,
	val eyePos: Vec3d,
	val onGround: Boolean,
	val isJumping: Boolean,
)
