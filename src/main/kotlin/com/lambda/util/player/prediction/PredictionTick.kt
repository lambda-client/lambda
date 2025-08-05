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

package com.lambda.util.player.prediction

import com.lambda.interaction.request.rotating.Rotation
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d

/**
 * Data class representing a movement prediction tick for an entity.
 *
 * @property position The current position of the entity.
 * @property rotation The current rotation (yaw, pitch) of the entity.
 * @property velocity The current velocity vector of the entity.
 * @property boundingBox The bounding box that defines the entity's space in the world.
 * @property eyePos The position of the entity's eyes, typically used for raycasting or viewing purposes.
 * @property onGround Indicates whether the entity is currently touching the ground.
 * @property isJumping Indicates whether the entity is currently jumping.
 */
data class PredictionTick(
    val position: Vec3d,
    val rotation: Rotation,
    val velocity: Vec3d,
    val boundingBox: Box,
    val eyePos: Vec3d,
    val onGround: Boolean,
    val isJumping: Boolean,
    val predictionEntity: PredictionEntity,
) {
    fun next() = skipTicks(1)

    fun skipTicks(amount: Int) = with(predictionEntity) {
        repeat(amount) {
            tickMovement()
        }

        lastTick
    }

    /**
     * Runs the simulation until either [amount] ticks were skipped or [block] is true
     */
    fun skipUntil(amount: Int = 20, block: (PredictionTick) -> Boolean) = with(predictionEntity) {
        repeat(amount) {
            tickMovement()
            val prediction = lastTick

            if (block(prediction)) return@with lastTick
        }

        return@with lastTick
    }
}
