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

import com.lambda.interaction.request.rotation.Rotation
import net.minecraft.entity.Entity
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.MathHelper.wrapDegrees
import net.minecraft.util.math.Vec3i
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

enum class PlaceDirection(
    val rotation: Rotation,
    val vector: Vec3i,
    private val yawRanges: List<ClosedRange<Double>>
) {
    Up       (   0.0, -90.0,  0,  1,  0, listOf(Double.MIN_VALUE..Double.MAX_VALUE)),
    Down     (   0.0,  90.0,  0, -1,  0, listOf(Double.MIN_VALUE..Double.MAX_VALUE)),

    UpNorth  ( -180.0, -90.0,  0,  1, -1,        northYawRanges),
    UpSouth  (    0.0, -90.0,  0,  1,  1, listOf(southYawRange)),
    UpWest   (   90.0, -90.0,  1,  1,  0, listOf(westYawRange)),
    UpEast   (  -90.0, -90.0, -1,  1,  0, listOf(eastYawRange)),

    DownNorth( -180.0,  90.0,  0, -1, -1,        northYawRanges),
    DownSouth(    0.0,  90.0,  0, -1,  1, listOf(southYawRange)),
    DownWest (   90.0,  90.0,  1, -1,  0, listOf(westYawRange)),
    DownEast (  -90.0,  90.0, -1, -1,  0, listOf(eastYawRange)),

    North    ( -180.0,   0.0,  0,  0, -1,        northYawRanges),
    South    (    0.0,   0.0,  0,  0,  1, listOf(southYawRange)),
    West     (   90.0,   0.0,  1,  0,  0, listOf(westYawRange)),
    East     (  -90.0,   0.0, -1,  0,  0, listOf(eastYawRange));

    constructor(yaw: Double, pitch: Double, x: Int, y: Int, z: Int, yawRanges: List<ClosedRange<Double>>)
            : this(Rotation(yaw, pitch), Vec3i(x, y, z), yawRanges)

    fun snapToArea(rot: Rotation): Rotation {
        if (isInArea(rot)) return rot

        val normalizedYaw = wrapDegrees(rot.yaw)
        val clampedYaw = when {
            rotation.yaw != -180.0 -> normalizedYaw.coerceIn(yawRanges[0])
            normalizedYaw < 0 -> normalizedYaw.coerceIn(yawRanges[0])
            else -> normalizedYaw.coerceIn(yawRanges[1])
        }

        // Calculate pitch boundaries based on the snapped yaw
        val snappedYawRad = Math.toRadians(clampedYaw)
        val sinYaw = abs(sin(snappedYawRad))
        val cosYaw = abs(cos(snappedYawRad))
        val pitchBoundaryEW = Math.toDegrees(atan(sinYaw))
        val pitchBoundaryNS = Math.toDegrees(atan(cosYaw))

        // Determine the correct pitch boundary and snap pitch
        val snappedPitch = when {
            // Primary E/W Directions
            isEast() || isWest() -> {
                when {
                    isUp() -> calculateVerticalPitch(sinYaw, pitchBoundaryEW, true)
                    isDown() -> calculateVerticalPitch(sinYaw, pitchBoundaryEW, false)
                    else -> calculateHorizontalPitch(rot.pitch, pitchBoundaryEW)
                }
            }
            // Primary N/S Directions
            isNorth() || isSouth() -> {
                when {
                    isUp() -> calculateVerticalPitch(cosYaw, pitchBoundaryNS, true)
                    isDown() -> calculateVerticalPitch(cosYaw, pitchBoundaryNS, false)
                    else -> calculateHorizontalPitch(rot.pitch, pitchBoundaryNS)
                }
            }
            // Handle purely UP/DOWN directions
            else -> rotation.pitch
        }

        // Clamp pitch to valid range
        val clampedPitch = snappedPitch.coerceIn(-90.0, 90.0)

        return Rotation(clampedYaw, clampedPitch)
    }

    /**
     * Calculates the pitch for vertical (Up/Down) directions
     * 
     * @param trigValue The trigonometric value (sinYaw for E/W, cosYaw for N/S)
     * @param boundaryValue The boundary value (pitchBoundaryEW for E/W, pitchBoundaryNS for N/S)
     * @param isUp Whether this is for an Up direction (true) or Down direction (false)
     * @return The calculated pitch value
     */
    private fun calculateVerticalPitch(trigValue: Double, boundaryValue: Double, isUp: Boolean): Double {
        val epsilon = 0.01
        val boundarySign = if (isUp) 1 else -1
        val asinSign = if (isUp) -1 else 1

        val targetPitch = Math.toDegrees(
            asinSign * asin(trigValue * cos(Math.toRadians(boundarySign * boundaryValue)) + epsilon)
        )

        return if (isUp) {
            targetPitch.coerceIn(-90.0, 0.0) // Ensure it's in the up range
        } else {
            targetPitch.coerceIn(0.0, 90.0) // Ensure it's in the down range
        }
    }

    /**
     * Calculates the pitch for horizontal directions
     * 
     * @param currentPitch The current pitch value
     * @param boundaryValue The boundary value (pitchBoundaryEW for E/W, pitchBoundaryNS for N/S)
     * @return The calculated pitch value
     */
    private fun calculateHorizontalPitch(currentPitch: Double, boundaryValue: Double): Double {
        val isWithinPositiveBoundary = abs(currentPitch - boundaryValue) < abs(currentPitch - (-boundaryValue))
        return if (isWithinPositiveBoundary) boundaryValue else -boundaryValue
    }

    // Helper functions to determine direction type
    private fun isEast(): Boolean = this == East || this == UpEast || this == DownEast
    private fun isWest(): Boolean = this == West || this == UpWest || this == DownWest
    private fun isNorth(): Boolean = this == North || this == UpNorth || this == DownNorth
    private fun isSouth(): Boolean = this == South || this == UpSouth || this == DownSouth
    private fun isUp(): Boolean = this == UpEast || this == UpWest || this == UpNorth || this == UpSouth || this == Up
    private fun isDown(): Boolean = this == DownEast || this == DownWest || this == DownNorth || this == DownSouth || this == Down

    fun isInArea(rot: Rotation) = fromRotation(rot) == this

    companion object {
        /**
         * A modified version of the minecraft getEntityFacingOrder method. This version takes a
         * [Rotation] instead of an [Entity]
         *
         * @see Direction.getEntityFacingOrder
         */
        fun fromRotation(rotation: Rotation): PlaceDirection {
            val pitchRad = rotation.pitchF * (Math.PI.toFloat() / 180f)
            val yawRad = -rotation.yawF * (Math.PI.toFloat() / 180f)

            val sinPitch = MathHelper.sin(pitchRad)
            val cosPitch = MathHelper.cos(pitchRad)
            val sinYaw = MathHelper.sin(yawRad)
            val cosYaw = MathHelper.cos(yawRad)

            val isFacingEast = sinYaw > 0.0f
            val isFacingUp = sinPitch < 0.0f
            val isFacingSouth = cosYaw > 0.0f

            val eastWestStrength = if (isFacingEast) sinYaw else -sinYaw
            val upDownStrength = if (isFacingUp) -sinPitch else sinPitch
            val northSouthStrength = if (isFacingSouth) cosYaw else -cosYaw

            val adjustedEastWestStrength = eastWestStrength * cosPitch
            val adjustedNorthSouthStrength = northSouthStrength * cosPitch

            return when {
                eastWestStrength > northSouthStrength -> when {
                    upDownStrength > adjustedEastWestStrength -> when {
                        isFacingUp && isFacingEast -> UpEast
                        isFacingUp -> UpWest
                        isFacingEast -> DownEast
                        else -> DownWest
                    }
                    else -> if (isFacingEast) East else West
                }
                upDownStrength > adjustedNorthSouthStrength -> when {
                    isFacingUp && isFacingSouth -> UpSouth
                    isFacingUp -> UpNorth
                    isFacingSouth -> DownSouth
                    else -> DownNorth
                }
                else -> if (isFacingSouth) South else North
            }
        }
    }
}

const val FUDGE_FACTOR = 0.01

val northYawRanges = listOf(-180.0..(-135.0 - FUDGE_FACTOR), (135.0 + FUDGE_FACTOR)..180.0)
val southYawRange =  ( -45.0 + FUDGE_FACTOR)..( 45.0 - FUDGE_FACTOR)
val eastYawRange =   (-135.0 + FUDGE_FACTOR)..(-45.0 - FUDGE_FACTOR)
val westYawRange =   (  45.0 + FUDGE_FACTOR)..(135.0 - FUDGE_FACTOR)
