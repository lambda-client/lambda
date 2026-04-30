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

package com.lambda.util.player

import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.simulation.processing.PreProcessingData
import com.lambda.interaction.construction.verify.ScanMode
import com.lambda.interaction.construction.verify.SurfaceScan
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.interaction.managers.rotating.Rotation.Companion.rotationTo
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.module.modules.client.Client
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.PlaceDirection
import com.lambda.util.extension.component6
import com.lambda.util.extension.rotation
import com.lambda.util.math.distSq
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import com.lambda.util.world.raycast.RayCastUtils.entityResult
import net.minecraft.entity.Entity
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.util.EnumSet
import kotlin.collections.minus
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Object for handling visibility checks, rotation calculations, and hit detection.
 */
object RotationUtils {
	val ALL_SIDES = Direction.entries.toSet()

	fun SafeContext.lookAt(pos: Vec3d): Rotation {
		val direction = pos.subtract(player.eyePos).normalize()
		val yaw = Math.toDegrees(atan2(direction.z, direction.x)) - 90.0
		val pitch = -Math.toDegrees(atan2(direction.y, hypot(direction.x, direction.z)))
		return Rotation(yaw, pitch)
	}

	fun SafeContext.lookInDirection(direction: PlaceDirection) =
		if (!direction.isInArea(player.rotation)) direction.snapToArea(RotationManager.activeRotation)
		else player.rotation

	fun AutomatedSafeContext.lookAtHit(hit: HitResult) =
		when (hit) {
			is BlockHitResult -> lookAtBlock(hit.blockPos, setOf(hit.side))
			is EntityHitResult -> lookAtEntity(hit.entity)
			else -> null
		}

	fun AutomatedSafeContext.lookAtEntity(entity: Entity, sides: Set<Direction> = ALL_SIDES) =
		entity.findRotation(buildConfig.entityReach, player.eyePos, sides)

	fun AutomatedSafeContext.lookAtBlock(pos: BlockPos, sides: Set<Direction> = ALL_SIDES) =
		pos.findRotation(buildConfig.blockReach, player.eyePos, sides)

	/**
	 * Finds a rotation that intersects with one of the specified bounding boxes, allowing the player to look at entities or blocks.
	 * To increase the stability, it will pause the rotation if eye position is within any of the bounding boxes
	 *
	 * @param reach The maximum reach distance for the interaction.
	 * @param pov The player's eye position.
	 * @param sides Set of block sides to consider for targeting.
	 * @param verify A lambda to verify if a [CheckedHit] meets the desired criteria.
	 *
	 * @return A [CheckedHit] if a valid rotation was found; otherwise, null.
	 */
	context(automatedSafeContext: AutomatedSafeContext)
	fun Entity.findRotation(
		reach: Double,
		pov: Vec3d,
		sides: Set<Direction> = ALL_SIDES,
		preProcessing: PreProcessingData? = null,
		allowInsideBox: Boolean = false,
		verify: (CheckedHit.() -> Boolean)? = null
	): CheckedHit? = with (automatedSafeContext) {
		if (boundingBox.contains(pov)) {
			val currentRotation = RotationManager.activeRotation
			currentRotation.rayCast(reach, pov)?.let { hit ->
				return CheckedHit(hit, currentRotation)
			}
		}

		val reachSq = reach.pow(2)

		val validHits = mutableSetOf<CheckedHit>()
		boundingBox.scanClosestPoints(pov, sides, preProcessing, allowInsideBox) { pos, _ ->
			if (pov distSq pos > reachSq) return@scanClosestPoints

			val newRotation = pov.rotationTo(pos)
			val hit = if (buildConfig.strictRayCast) newRotation.rayCast(reach, pov) ?: return@scanClosestPoints
			else EntityHitResult(this@findRotation, pos)

			if (hit.entityResult?.entity != this@findRotation) return@scanClosestPoints

			val checkedHit = CheckedHit(hit, newRotation)
			if (verify?.invoke(checkedHit) != false) validHits.add(checkedHit)
		}
		return buildConfig.pointSelection.select(validHits)
	}

	context(automatedSafeContext: AutomatedSafeContext)
	fun BlockPos.findRotation(
		reach: Double,
		pov: Vec3d,
		sides: Set<Direction> = ALL_SIDES,
		preProcessing: PreProcessingData? = null,
		allowInsideBox: Boolean = false,
		verify: (CheckedHit.() -> Boolean)? = null
	): CheckedHit? = with (automatedSafeContext) {
		val shape = blockState(this@findRotation)
			.getOutlineShape(world, this@findRotation)
			.offset(this@findRotation)

		if (shape.boundingBoxes.any { it.contains(pov) }) {
			val currentRotation = RotationManager.activeRotation
			currentRotation.rayCast(reach, pov)?.let { hit ->
				return CheckedHit(hit, currentRotation)
			}
		}

		val reachSq = reach.pow(2)

		val validHits = mutableSetOf<CheckedHit>()
		shape.boundingBoxes.forEach { box ->
			box.scanClosestPoints(pov, sides, preProcessing, allowInsideBox) { pos, side ->
				if (pov distSq pos > reachSq) return@scanClosestPoints

				val newRotation = pov.rotationTo(pos)
				val hit = if (buildConfig.strictRayCast) newRotation.rayCast(reach, pov) ?: return@scanClosestPoints
				else BlockHitResult(pos, side, this@findRotation, interactConfig.airPlace.isEnabled)

				if (hit.blockResult?.blockPos != this@findRotation) return@scanClosestPoints

				val checkedHit = CheckedHit(hit, newRotation)
				if (verify?.invoke(checkedHit) != false) validHits.add(checkedHit)
			}
		}
		return buildConfig.pointSelection.select(validHits)
	}

	/**
	 * Scans the surfaces of a given box on the [sides] specified
	 * and executes a callback for each point calculated based on the scanning parameters.
	 *
	 * @param sides A set of sides to scan
	 * @param resolution The number of intervals into which each dimension is divided for scanning (default is 5).
	 * @param preProcessing Configuration specifying the axis and mode of the scan.
	 * @param check A callback function that performs an action for each surface point, receiving the direction of the surface and the current 3D vector.
	 */
	context(_: Automated)
	fun Box.scanSurfaces(
		pov: Vec3d,
		sides: Collection<Direction>,
		resolution: Int = 5,
		preProcessing: PreProcessingData? = null,
		allowInsideBox: Boolean = false,
		check: (Vec3d, Direction) -> Unit
	) {
		val visibleSides = sides.visibleSides(this, pov)
		val (scanBox, invalidSides) = getScanBox(preProcessing, allowInsideBox) ?: return
		(visibleSides - invalidSides).forEach { side ->
			val (minX, minY, minZ, maxX, maxY, maxZ) = scanBox
				.offset(side.doubleVector.multiply(Client.scanShrinkFactor))
				.bounds(side)

			val stepX = (maxX - minX) / resolution
			val stepY = (maxY - minY) / resolution
			val stepZ = (maxZ - minZ) / resolution

			(0..resolution).forEach outer@{ i ->
				val x = if (stepX != 0.0) minX + (stepX * i) else minX
				(0..resolution).forEach inner@{ j ->
					val y = if (stepY != 0.0) minY + (stepY * j) else minY
					val z = if (stepZ != 0.0) minZ + stepZ * ((if (stepX != 0.0) j else i)) else minZ
					check(Vec3d(x, y, z), side)
				}
			}
		}
	}

	context(_: Automated)
	fun Box.scanClosestPoints(
		pov: Vec3d,
		sides: Set<Direction>,
		preProcessing: PreProcessingData? = null,
		allowInsideBox: Boolean = false,
		check: (Vec3d, Direction) -> Unit
	) {
		val visibleSides = sides.visibleSides(this, pov)
		val (scanBox, invalidSides) = getScanBox(preProcessing, allowInsideBox) ?: return
		with(scanBox) {
			(visibleSides - invalidSides).forEach { side ->
				val pos = when (side) {
					Direction.DOWN -> Vec3d(
						pov.x.coerceIn(minX, maxX),
						minY - Client.scanShrinkFactor,
						pov.z.coerceIn(minZ, maxZ)
					)
					Direction.UP -> Vec3d(
						pov.x.coerceIn(minX, maxX),
						maxY + Client.scanShrinkFactor,
						pov.z.coerceIn(minZ, maxZ)
					)
					Direction.NORTH -> Vec3d(
						pov.x.coerceIn(minX, maxX),
						pov.y.coerceIn(minY, maxY),
						minZ - Client.scanShrinkFactor
					)
					Direction.SOUTH -> Vec3d(
						pov.x.coerceIn(minX, maxX),
						pov.y.coerceIn(minY, maxY),
						maxZ + Client.scanShrinkFactor
					)
					Direction.WEST -> Vec3d(
						minX - Client.scanShrinkFactor,
						pov.y.coerceIn(minY, maxY),
						pov.z.coerceIn(minZ, maxZ)
					)
					Direction.EAST -> Vec3d(
						maxX + Client.scanShrinkFactor,
						pov.y.coerceIn(minY, maxY),
						pov.z.coerceIn(minZ, maxZ)
					)
				}
				check(pos, side)
			}
		}
	}

	/**
	 * Determines which surfaces of the box are visible from a specific position, typically the player's eyes.
	 *
	 * @param eyes The position to determine visibility from.
	 * @return A set of directions corresponding to visible sides.
	 */
	fun Box.getVisibleSurfaces(eyes: Vec3d) =
		EnumSet.noneOf(Direction::class.java)
			.checkAxis(eyes.x - center.x, lengthX / 2, Direction.WEST, Direction.EAST)
			.checkAxis(eyes.y - center.y, lengthY / 2, Direction.DOWN, Direction.UP)
			.checkAxis(eyes.z - center.z, lengthZ / 2, Direction.NORTH, Direction.SOUTH)

	private fun Box.getScanBox(
		preProcessing: PreProcessingData?,
		allowInsideBox: Boolean
	): Pair<Box, Set<Direction>>? =
		with(contract(Client.scanShrinkFactor)) {
			if (preProcessing == null || preProcessing.info.surfaceScan.mode == ScanMode.Full) return Pair(this, emptySet())

			val (newXBounds, shrunkXSide) = toScanRange(minX, maxX, preProcessing.pos.x, Direction.Axis.X, preProcessing.info.surfaceScan)
			val (newYBounds, shrunkYSide) = toScanRange(minY, maxY, preProcessing.pos.y, Direction.Axis.Y, preProcessing.info.surfaceScan)
			val (newZBounds, shrunkZSide) = toScanRange(minZ, maxZ, preProcessing.pos.z, Direction.Axis.Z, preProcessing.info.surfaceScan)

			if (newXBounds.isEmpty() || newYBounds.isEmpty() || newZBounds.isEmpty()) return null

			val invalidSides = buildSet {
				if (!allowInsideBox) {
					addAll(listOfNotNull(shrunkXSide, shrunkYSide, shrunkZSide))
				}
			}

			val box = Box(
				newXBounds.start, newYBounds.start, newZBounds.start,
				newXBounds.endInclusive, newYBounds.endInclusive, newZBounds.endInclusive
			)
			return Pair(box, invalidSides)
		}

	private fun toScanRange(
		min: Double,
		max: Double,
		origin: Int,
		axis: Direction.Axis,
		scan: SurfaceScan
	): Pair<ClosedRange<Double>, Direction?> {
		val range = if (scan.axis == axis) {
			when (scan.mode) {
				ScanMode.GreaterBlockHalf -> max(origin + 0.5 + Client.scanShrinkFactor, min)..max
				else -> min..min(origin + 0.5 - Client.scanShrinkFactor, max)
			}
		} else min..max
		return Pair(
			range,
			when (scan.mode) {
				ScanMode.GreaterBlockHalf if range.start > min -> axis.negativeDirection
				ScanMode.LesserBlockHalf if range.endInclusive < max -> axis.positiveDirection
				else -> null
			}
		)
	}

	/**
	 * Helper function to add visible sides to an EnumSet based on positional differences.
	 */
	private fun EnumSet<Direction>.checkAxis(
		diff: Double,
		limit: Double,
		negativeSide: Direction,
		positiveSide: Direction,
	) = apply {
		when {
			diff < -limit -> add(negativeSide)
			diff > limit -> add(positiveSide)
		}
	}

	/**
	 * Gets the bounding coordinates of a box's side, specifying min and max values for each axis.
	 *
	 * @param side The side of the box to calculate bounds for.
	 * @return An array of doubles representing the side's bounds.
	 */
	private fun Box.bounds(side: Direction) =
		when (side) {
			Direction.DOWN -> doubleArrayOf(minX, minY, minZ, maxX, minY, maxZ)
			Direction.UP -> doubleArrayOf(minX, maxY, minZ, maxX, maxY, maxZ)
			Direction.NORTH -> doubleArrayOf(minX, minY, minZ, maxX, maxY, minZ)
			Direction.SOUTH -> doubleArrayOf(minX, minY, maxZ, maxX, maxY, maxZ)
			Direction.WEST -> doubleArrayOf(minX, minY, minZ, minX, maxY, maxZ)
			Direction.EAST -> doubleArrayOf(maxX, minY, minZ, maxX, maxY, maxZ)
		}

	/**
	 * Determines the sides of a box that are visible from a given position, based on interaction settings.
	 *
	 * @param box The box whose visible sides are to be determined.
	 * @param eye The position (e.g., the player's eyes) to determine visibility from.
	 * @return A set of directions corresponding to the visible sides of the box.
	 */
	context(automated: Automated)
	private fun Collection<Direction>.visibleSides(
		box: Box,
		eye: Vec3d
	) = if (automated.buildConfig.checkSideVisibility || automated.buildConfig.strictRayCast) {
		intersect(box.getVisibleSurfaces(eye))
	} else this
}

class CheckedHit(
	val hit: HitResult,
	val rotation: Rotation
)