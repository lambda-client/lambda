package com.lambda.pathing.rollout

import com.lambda.pathing.core.Stance
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

sealed interface TrajectoryDiagnostic {
	val frame: Int

	data class HorizontalCollision(
		override val frame: Int,
		val position: Vec3d,
	) : TrajectoryDiagnostic

	data class HeadBonk(
		override val frame: Int,
		val position: Vec3d,
	) : TrajectoryDiagnostic

	data class FellBelowRoute(
		override val frame: Int,
		val depth: Double,
	) : TrajectoryDiagnostic

	data class RepeatedCoarseStance(override val frame: Int, val stance: Stance) : TrajectoryDiagnostic

	data class UnknownTerrain(
		override val frame: Int,
		val sectionX: Int,
		val sectionY: Int,
		val sectionZ: Int,
	) : TrajectoryDiagnostic

	data class HarmfulFall(
		override val frame: Int,
		val fallDistance: Double,
	) : TrajectoryDiagnostic

	data class NoStop(
		override val frame: Int,
		val goalError: Double,
		val speed: Double,
	) : TrajectoryDiagnostic

	data class UnsupportedPhysics(
		override val frame: Int,
		val reason: String,
	) : TrajectoryDiagnostic

	data class OutsideSnapshot(
		override val frame: Int,
		val position: BlockPos,
	) : TrajectoryDiagnostic
}
