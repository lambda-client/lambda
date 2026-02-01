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

package com.lambda.interaction.managers.rotating.visibilty

import com.lambda.interaction.managers.rotating.Rotation
import net.minecraft.entity.Entity
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3i

enum class PlaceDirection(
	val rotation: Rotation,
	val vector: Vec3i,
) {
	UpNorth(-180.0, -90.0, 0, 1, -1),
	UpSouth(0.0, -90.0, 0, 1, 1),
	UpWest(90.0, -90.0, 1, 1, 0),
	UpEast(-90.0, -90.0, -1, 1, 0),

	DownNorth(-180.0, 90.0, 0, -1, -1),
	DownSouth(0.0, 90.0, 0, -1, 1),
	DownWest(90.0, 90.0, 1, -1, 0),
	DownEast(-90.0, 90.0, -1, -1, 0),

	North(-180.0, 0.0, 0, 0, -1),
	South(0.0, 0.0, 0, 0, 1),
	West(90.0, 0.0, 1, 0, 0),
	East(-90.0, 0.0, -1, 0, 0);

	constructor(yaw: Double, pitch: Double, x: Int, y: Int, z: Int)
			: this(Rotation(yaw, pitch), Vec3i(x, y, z))

	//ToDo: snap to the area not the cardinal to avoid excess rotation distance
	fun snapToArea(rot: Rotation): Rotation = rotation

	fun isInArea(rot: Rotation) = fromRotation(rot) == this

	companion object {
		/**
		 * A modified version of the minecraft getEntityFacingOrder method. This version takes a
		 * [Rotation] instead of an [Entity]
		 *
		 * @see Direction.getEntityFacingOrder
		 */
		fun fromRotation(rotation: Rotation): PlaceDirection {
			val pitchRad = rotation.pitchF * (Math.PI.toFloat() / 180f).toDouble()
			val yawRad = -rotation.yawF * (Math.PI.toFloat() / 180f).toDouble()

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
