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

package com.lambda.interaction.managers.rotating.visibilty

import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.interaction.managers.rotating.visibilty.VisibilityChecker.ALL_SIDES
import com.lambda.interaction.managers.rotating.visibilty.VisibilityChecker.findRotation
import com.lambda.util.extension.rotation
import net.minecraft.entity.Entity
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.math.atan2
import kotlin.math.hypot

@DslMarker
annotation class RotationDsl

@RotationDsl
fun SafeContext.lookAt(pos: Vec3d): Rotation {
	val direction = pos.subtract(player.eyePos).normalize()
	val yaw = Math.toDegrees(atan2(direction.z, direction.x)) - 90.0
	val pitch = -Math.toDegrees(atan2(direction.y, hypot(direction.x, direction.z)))
	return Rotation(yaw, pitch)
}

@RotationDsl
fun SafeContext.lookInDirection(direction: PlaceDirection) =
	if (!direction.isInArea(player.rotation)) direction.snapToArea(RotationManager.activeRotation)
	else player.rotation

@RotationDsl
fun AutomatedSafeContext.lookAtHit(hit: HitResult) =
	when (hit) {
		is BlockHitResult -> lookAtBlock(hit.blockPos, setOf(hit.side))
		is EntityHitResult -> lookAtEntity(hit.entity)
		else -> null
	}

@RotationDsl
fun AutomatedSafeContext.lookAtEntity(entity: Entity, sides: Set<Direction> = ALL_SIDES) =
	entity.findRotation(buildConfig.entityReach, player.eyePos, sides)

@RotationDsl
fun AutomatedSafeContext.lookAtBlock(pos: BlockPos, sides: Set<Direction> = ALL_SIDES) =
	pos.findRotation(buildConfig.blockReach, player.eyePos, sides)