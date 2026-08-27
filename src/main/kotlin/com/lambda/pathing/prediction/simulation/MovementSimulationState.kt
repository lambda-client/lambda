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

package com.lambda.pathing.prediction.simulation

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.mixin.entity.ClientPlayerEntityAccessor
import com.lambda.util.math.DOWN
import com.lambda.util.math.flooredBlockPos
import com.lambda.util.math.plus
import com.lambda.util.math.times
import com.lambda.util.player.MovementUtils.moveYaw
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import kotlin.jvm.optionals.getOrNull

data class MovementSimulationState(
	val position: Vec3d,
	val rotation: Rotation,
	val velocity: Vec3d,
	val boundingBox: Box,
	val onGround: Boolean,
	val isJumping: Boolean,
	val isSprinting: Boolean,
	val isSneaking: Boolean,
	val jumpingCooldown: Int,
	val velocityAffectingPos: BlockPos,
	val horizontalCollision: Boolean,
	val collidedSoftly: Boolean,
	val verticalCollision: Boolean,
	val supportingBlockPos: BlockPos? = null,
	val doubleTapSprintTicks: Int = 0,
	val hadForwardMovement: Boolean = false,
) {
    companion object {
        fun from(
	        player: ClientPlayerEntity,
	        position: Vec3d = player.pos,
	        rotation: Rotation = Rotation(player.moveYaw, player.pitch),
	        velocity: Vec3d = player.velocity,
	        onGround: Boolean = player.isOnGround,
	        isJumping: Boolean = false,
	        isSprinting: Boolean = player.isSprinting,
	        isSneaking: Boolean = player.isSneaking,
	        jumpingCooldown: Int = player.jumpingCooldown,
	        velocityAffectingPos: BlockPos = player.velocityAffectingPos,
	        horizontalCollision: Boolean = player.horizontalCollision,
	        collidedSoftly: Boolean = player.collidedSoftly,
	        verticalCollision: Boolean = player.verticalCollision,
	        boundingBox: Box = player.boundingBox.offset(position.subtract(player.pos)),
	        supportingBlockPos: BlockPos? = player.supportingBlockPos.getOrNull(),
	        doubleTapSprintTicks: Int =
                (player as ClientPlayerEntityAccessor).`lambda$getTicksLeftToDoubleTapSprint`(),
	        hadForwardMovement: Boolean = player.input.hasForwardMovement(),
        ) = MovementSimulationState(
            position = position,
            rotation = rotation,
            velocity = velocity,
            boundingBox = boundingBox,
            onGround = onGround,
            isJumping = isJumping,
            isSprinting = isSprinting,
            isSneaking = isSneaking,
            jumpingCooldown = jumpingCooldown,
            velocityAffectingPos = velocityAffectingPos,
            horizontalCollision = horizontalCollision,
            collidedSoftly = collidedSoftly,
            verticalCollision = verticalCollision,
            supportingBlockPos = supportingBlockPos,
            doubleTapSprintTicks = doubleTapSprintTicks,
            hadForwardMovement = hadForwardMovement,
        )

        fun at(
	        player: ClientPlayerEntity,
	        position: Vec3d,
	        rotation: Rotation = Rotation(player.moveYaw.toDouble(), player.pitch.toDouble()),
	        velocity: Vec3d = Vec3d.ZERO,
	        onGround: Boolean = true,
	        isSprinting: Boolean = false,
	        isSneaking: Boolean = false,
	        jumpingCooldown: Int = 0,
        ) = from(
            player = player,
            position = position,
            rotation = rotation,
            velocity = velocity,
            onGround = onGround,
            isSprinting = isSprinting,
            isSneaking = isSneaking,
            jumpingCooldown = jumpingCooldown,
            horizontalCollision = false,
            collidedSoftly = false,
            verticalCollision = false,
        )

        fun synthetic(
	        profile: PlayerPhysicsProfile,
	        position: Vec3d,
	        rotation: Rotation,
	        velocity: Vec3d = Vec3d.ZERO,
	        onGround: Boolean = true,
	        isSprinting: Boolean = false,
	        isSneaking: Boolean = false,
	        jumpingCooldown: Int = 0,
        ): MovementSimulationState {
            val halfWidth = profile.width * 0.5
            return MovementSimulationState(
                position = position,
                rotation = rotation,
                velocity = velocity,
                boundingBox = Box(
	                position.x - halfWidth, position.y, position.z - halfWidth,
	                position.x + halfWidth, position.y + profile.height, position.z + halfWidth,
                ),
                onGround = onGround,
                isJumping = false,
                isSprinting = isSprinting,
                isSneaking = isSneaking,
                jumpingCooldown = jumpingCooldown,
                velocityAffectingPos = (position + DOWN * 0.500001F.toDouble()).flooredBlockPos,
                horizontalCollision = false,
                collidedSoftly = false,
                verticalCollision = false,
            )
        }
    }
}