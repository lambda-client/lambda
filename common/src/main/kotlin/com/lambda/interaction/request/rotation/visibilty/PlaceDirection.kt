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

    //ToDo: Add dynamic pitch border calculations to avoid excess rotation distance
    fun snapToArea(rot: Rotation): Rotation {
        if (isInArea(rot)) return rot

        val normalizedYaw = wrapDegrees(rot.yaw)
        val clampedYaw = when {
            this.rotation.yaw != -180.0 -> normalizedYaw.coerceIn(yawRanges[0])
            normalizedYaw < 0 -> normalizedYaw.coerceIn(yawRanges[0])
            else -> normalizedYaw.coerceIn(yawRanges[1])
        }

        return Rotation(clampedYaw, this.rotation.pitch)
    }

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