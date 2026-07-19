
package com.minato.util.player.prediction

import com.minato.interaction.managers.rotating.Rotation
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d

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

    fun skipUntil(amount: Int = 20, block: (PredictionTick) -> Boolean) = with(predictionEntity) {
        repeat(amount) {
            tickMovement()
            val prediction = lastTick

            if (block(prediction)) return@with lastTick
        }

        return@with lastTick
    }
}
