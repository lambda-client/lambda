/*
 * Copyright 2025 Lambda
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

package com.lambda.interaction.request.rotating.visibilty

import com.lambda.context.AutomatedSafeContext
import com.lambda.context.AutomationConfig
import com.lambda.interaction.construction.verify.ScanMode
import com.lambda.interaction.construction.verify.SurfaceScan
import com.lambda.interaction.request.rotating.Rotation
import com.lambda.interaction.request.rotating.Rotation.Companion.rotationTo
import com.lambda.interaction.request.rotating.RotationManager
import com.lambda.util.extension.component6
import com.lambda.util.math.distSq
import com.lambda.util.world.raycast.InteractionMask
import net.minecraft.entity.LivingEntity
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.util.*
import kotlin.math.floor
import kotlin.math.pow

/**
 * Object for handling visibility checks, rotation calculations, and hit detection.
 */
object VisibilityChecker {

    /**
     * Finds a rotation that intersects with one of the specified bounding boxes, allowing the player to look at entities or blocks.
     * To increase the stability, it will pause the rotation if eye position is within any of the bounding boxes
     *
     * @param boxes List of bounding boxes for potential targets.
     * @param reach The maximum reach distance for the interaction.
     * @param eye The player's eye position.
     * @param sides Set of block sides to consider for targeting.
     * @param interaction Specifies interaction settings, such as side visibility and resolution.
     * @param verify A lambda to verify if a [CheckedHit] meets the desired criteria.
     *
     * @return A [CheckedHit] if a valid rotation was found; otherwise, null.
     */
    fun AutomatedSafeContext.findRotation(
        boxes: List<Box>,
        reach: Double,
        eye: Vec3d,
        sides: Set<Direction>,
        scan: SurfaceScan,
        targetType: InteractionMask,
        verify: CheckedHit.() -> Boolean
    ): CheckedHit? {
        val currentRotation = RotationManager.activeRotation

        if (boxes.any { it.contains(eye) }) {
            currentRotation.rayCast(reach, eye)?.let { hit ->
                return CheckedHit(hit, currentRotation, reach)
            }
        }

        return buildConfig.pointSelection.select(
            collectHitsFor(boxes, reach, eye, sides, scan, targetType, verify)
        )
    }

    /**
     * Finds a collection of [CheckedHit] that intersect with one of the specified bounding boxes, allowing the player to look at entities or blocks.
     *
     * @param boxes List of bounding boxes for potential targets.
     * @param reach The maximum reach distance for the interaction.
     * @param eye The player's eye position.
     * @param sides Set of block sides to consider for targeting.
     * @param scan Configuration specifying the axis and mode of the scan (default is `SurfaceScan.DEFAULT`).
     * @param interaction Specifies interaction settings, such as side visibility and resolution.
     * @param verify A lambda to verify if a [CheckedHit] meets the desired criteria.
     *
     * @return A collection of [CheckedHit] with valid angles found
     */
    fun AutomatedSafeContext.collectHitsFor(
        boxes: List<Box>,
        reach: Double,
        eye: Vec3d = player.eyePos,
        sides: Set<Direction> = ALL_SIDES,
        scan: SurfaceScan = SurfaceScan.DEFAULT,
        targetType: InteractionMask,
        verify: CheckedHit.() -> Boolean,
    ) = mutableListOf<CheckedHit>().apply {
        val reachSq = buildConfig.scanReach.pow(2)

        boxes.forEach { box ->
            val visible = visibleSides(box, eye, buildConfig.checkSideVisibility)

            scanSurfaces(box, visible.intersect(sides), buildConfig.resolution, scan) { _, vec ->
                if (eye distSq vec > reachSq) return@scanSurfaces

                val newRotation = eye.rotationTo(vec)

                val mask = if (buildConfig.strictRayCast) InteractionMask.Both else targetType
                val hit = newRotation.rayCast(reach, eye, mask = mask) ?: return@scanSurfaces

                val checked = CheckedHit(hit, newRotation, reach)
                if (!checked.verify()) return@scanSurfaces

                add(checked)
            }
        }
    }

    private fun AutomatedSafeContext.collectHitsInternal(
        boxes: List<Box>,
        reach: Double,
        eye: Vec3d,
        sides: Set<Direction>,
        scan: SurfaceScan,
        targetType: InteractionMask,
        entity: LivingEntity?,
        verify: CheckedHit.() -> Boolean,
    ) = mutableListOf<CheckedHit>().apply {
        val reachSq = buildConfig.scanReach.pow(2)

        boxes.forEach { box ->
            val visible = visibleSides(box, eye, buildConfig.checkSideVisibility)

            scanSurfaces(box, visible.intersect(sides), buildConfig.resolution, scan) { _, vec ->
                if (eye distSq vec > reachSq) return@scanSurfaces

                val newRotation = eye.rotationTo(vec)

                val mask = if (buildConfig.strictRayCast || entity == null) InteractionMask.Both else targetType
                val hit = newRotation.rayCast(reach, eye, mask = mask) ?: return@scanSurfaces

                val checked = CheckedHit(hit, newRotation, reach)
                if (!checked.verify()) return@scanSurfaces

                add(checked)
            }
        }
    }

    /**
     * Scans the surfaces of a given box, optionally excluding specific sides,
     * and executes a callback for each point calculated based on the scanning parameters.
     *
     * @param box The 3D box whose surfaces will be scanned.
     * @param excludedSides A set of directions representing the sides of the box to exclude from the scan (default is an empty set).
     * @param resolution The number of intervals into which each dimension is divided for scanning (default is 5).
     * @param scan Configuration specifying the axis and mode of the scan (default is `SurfaceScan.DEFAULT`).
     * @param check A callback function that performs an action for each surface point, receiving the direction of the surface and the current 3D vector.
     */
    fun scanSurfaces(
        box: Box,
        sides: Set<Direction> = emptySet(),
        resolution: Int = 5,
        scan: SurfaceScan = SurfaceScan.DEFAULT,
        check: (Direction, Vec3d) -> Unit
    ) {
        sides.forEach { side ->
            val (minX, minY, minZ, maxX, maxY, maxZ) = box
                .contract(AutomationConfig.shrinkFactor)
                .offset(side.doubleVector.multiply(AutomationConfig.shrinkFactor))
                .bounds(side)

            // Determine the bounds to scan based on the axis and mode. Skip if no part of the face is in the desired bounds
            val (startX, endX) = if (scan.axis == Direction.Axis.X && maxX != minX) {
                when (scan.mode) {
                    ScanMode.GREATER_BLOCK_HALF -> (floor(minX) + 0.501).let { center ->
                        if (maxX < center) return@forEach
                        minX.coerceAtLeast(center) to maxX
                    }
                    ScanMode.LESSER_BLOCK_HALF -> (floor(maxX) + 0.499).let { center ->
                        if (minX > center) return@forEach
                        minX to maxX.coerceAtMost(center)
                    }
                    ScanMode.FULL -> minX to maxX
                }
            } else minX to maxX

            val (startY, endY) = if (scan.axis == Direction.Axis.Y && maxY != minY) {
                when (scan.mode) {
                    ScanMode.GREATER_BLOCK_HALF -> (floor(minY) + 0.501).let { center ->
                        if (maxY < center) return@forEach
                        minY.coerceAtLeast(center) to maxY
                    }
                    ScanMode.LESSER_BLOCK_HALF -> (floor(maxY) + 0.499).let { center ->
                        if (minY > center) return@forEach
                        minY to maxY.coerceAtMost(center)
                    }
                    ScanMode.FULL -> minY to maxY
                }
            } else minY to maxY

            val (startZ, endZ) = if (scan.axis == Direction.Axis.Z && maxZ != minZ) {
                when (scan.mode) {
                    ScanMode.GREATER_BLOCK_HALF -> (floor(minZ) + 0.501).let { center ->
                        if (maxZ < center) return@forEach
                        minZ.coerceAtLeast(center) to maxZ
                    }
                    ScanMode.LESSER_BLOCK_HALF -> (floor(maxZ) + 0.499).let { center ->
                        if (minZ > center) return@forEach
                        minZ to maxZ.coerceAtMost(center)
                    }
                    ScanMode.FULL -> minZ to maxZ
                }
            } else minZ to maxZ

            val stepX = (endX - startX) / resolution
            val stepY = (endY - startY) / resolution
            val stepZ = (endZ - startZ) / resolution

            (0..resolution).forEach outer@{ i ->
                val x = if (stepX != 0.0) startX + (stepX * i) else startX
                (0..resolution).forEach inner@{ j ->
                    val y = if (stepY != 0.0) startY + (stepY * j) else startY
                    val z = if (stepZ != 0.0) startZ + stepZ * ((if (stepX != 0.0) j else i)) else startZ
                    check(side, Vec3d(x, y, z))
                }
            }
        }
    }

    /**
     * Determines the sides of a box that are visible from a given position, based on interaction settings.
     *
     * @param box The box whose visible sides are to be determined.
     * @param eye The position (e.g., the player's eyes) to determine visibility from.
     * @param visibilityCheck Whether to check the visibility of the side.
     * @return A set of directions corresponding to the visible sides of the box.
     */
    private fun visibleSides(box: Box, eye: Vec3d, visibilityCheck: Boolean) =
        if (visibilityCheck) {
            box.getVisibleSurfaces(eye)
        } else Direction.entries.toSet()

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

    val ALL_SIDES = Direction.entries.toSet()

    class CheckedHit(
        val hit: HitResult,
        val targetRotation: Rotation,
        val reach: Double
    )
}
