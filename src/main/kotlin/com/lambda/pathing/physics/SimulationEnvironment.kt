/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.pathing.physics

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d

interface SimulationEnvironment {
	fun slipperiness(pos: BlockPos): Double
	fun velocityMultiplier(pos: BlockPos): Double
	fun jumpVelocityMultiplier(pos: BlockPos): Double
	fun adjustMovementForCollisions(
		movement: Vec3d,
		boundingBox: Box,
		onGround: Boolean,
		stepHeight: Double,
	): Vec3d

	/**
	 * @see net.minecraft.world.CollisionView.findSupportingBlockPos
	 */
	fun findSupportingBlockPos(box: Box, entityPos: Vec3d): BlockPos?

	fun isFenceLike(pos: BlockPos): Boolean

	/**
	 * @see net.minecraft.entity.LivingEntity.isClimbing
	 */
	fun isClimbable(pos: BlockPos): Boolean = false

	/**
	 * @see net.minecraft.entity.player.PlayerEntity.adjustMovementForSneaking
	 * @see net.minecraft.entity.player.PlayerEntity.updatePose
	 */
	fun isSpaceEmpty(box: Box): Boolean? = null

	/**
	 * @see net.minecraft.block.SlimeBlock.onEntityLand
	 */
	fun bounceFactor(pos: BlockPos): Double = 0.0

	/**
	 * @see net.minecraft.block.SlimeBlock.onSteppedOn
	 */
	fun dampensSteppingSpeed(pos: BlockPos): Boolean = false
}

