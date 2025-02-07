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

package com.lambda.interaction.request.rotation.visibilty

import com.lambda.config.groups.InteractionConfig
import com.lambda.config.groups.InteractionSettings
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.verify.ScanMode
import com.lambda.interaction.construction.verify.SurfaceScan
import com.lambda.interaction.request.rotation.*
import com.lambda.interaction.request.rotation.Rotation.Companion.rotationTo
import com.lambda.util.extension.component6
import com.lambda.util.math.distSq
import com.lambda.util.world.raycast.InteractionMask
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.util.*
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
    fun SafeContext.findRotation(
        boxes: List<Box>,
        reach: Double,
        eye: Vec3d,
        sides: Set<Direction>,
        targetType: InteractionMask,
        interaction: InteractionConfig,
        verify: CheckedHit.() -> Boolean
    ): CheckedHit? {
        val currentRotation = RotationManager.currentRotation

        if (boxes.any { it.contains(eye) }) {
            currentRotation.rayCast(reach, eye)?.let { hit ->
                return CheckedHit(hit, currentRotation, reach)
            }
        }

        return interaction.pointSelection.select(
            collectHitsFor(boxes, reach, eye, sides, SurfaceScan.DEFAULT, targetType, interaction, verify)
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
    fun SafeContext.collectHitsFor(
        boxes: List<Box>,
        reach: Double,
        eye: Vec3d = player.eyePos,
        sides: Set<Direction> = ALL_SIDES,
        scan: SurfaceScan = SurfaceScan.DEFAULT,
        targetType: InteractionMask,
        interaction: InteractionConfig,
        verify: CheckedHit.() -> Boolean,
    ) = mutableListOf<CheckedHit>().apply {
        val reachSq = interaction.scanReach.pow(2)

        boxes.forEach { box ->
            val visible = visibleSides(box, eye, interaction.checkSideVisibility)

            scanSurfaces(box, visible.intersect(sides), interaction.resolution, scan) { _, vec ->
                if (eye distSq vec > reachSq) return@scanSurfaces

                val newRotation = eye.rotationTo(vec)

                val mask = if (interaction.strictRayCast) InteractionMask.BOTH else targetType
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
        excludedSides: Set<Direction> = emptySet(),
        resolution: Int = 5,
        scan: SurfaceScan = SurfaceScan.DEFAULT,
        check: (Direction, Vec3d) -> Unit,
    ) {
        excludedSides.forEach { side ->
            if (excludedSides.isNotEmpty() && side !in excludedSides) return@forEach
            val (minX, minY, minZ, maxX, maxY, maxZ) = box.contract(1.0E-3).bounds(side)
            val stepX = (maxX - minX) / resolution
            val stepY = (maxY - minY) / resolution
            val stepZ = (maxZ - minZ) / resolution

            // Determine the bounds to scan based on the axis and mode
            val (startX, endX) = if (scan.axis == Direction.Axis.X && stepX != 0.0) {
                val centerX = (minX + maxX) / 2
                when (scan.mode) {
                    ScanMode.GREATER_HALF -> centerX + 0.01 to maxX
                    ScanMode.LESSER_HALF -> minX to centerX - 0.01
                    ScanMode.FULL -> minX to maxX
                }
            } else minX to maxX

            val (startY, endY) = if (scan.axis == Direction.Axis.Y && stepY != 0.0) {
                val centerY = (minY + maxY) / 2
                when (scan.mode) {
                    ScanMode.GREATER_HALF -> centerY + 0.01 to maxY
                    ScanMode.LESSER_HALF -> minY to centerY - 0.01
                    ScanMode.FULL -> minY to maxY
                }
            } else minY to maxY

            val (startZ, endZ) = if (scan.axis == Direction.Axis.Z && stepZ != 0.0) {
                val centerZ = (minZ + maxZ) / 2
                when (scan.mode) {
                    ScanMode.GREATER_HALF -> centerZ + 0.01 to maxZ
                    ScanMode.LESSER_HALF -> minZ to centerZ - 0.01
                    ScanMode.FULL -> minZ to maxZ
                }
            } else minZ to maxZ

            (0..resolution).forEach outer@{ i ->
                val x = if (stepX != 0.0) startX + stepX * i else startX
                if (x > endX) return@outer
                (0..resolution).forEach inner@{ j ->
                    val y = if (stepY != 0.0) startY + stepY * j else startY
                    if (y > endY) return@inner
                    val z = if (stepZ != 0.0) startZ + stepZ * ((if (stepX != 0.0) j else i)) else startZ
                    if (z > endZ) return@inner
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

    class CheckedHit(val hit: HitResult, val targetRotation: Rotation, val reach: Double)
}
