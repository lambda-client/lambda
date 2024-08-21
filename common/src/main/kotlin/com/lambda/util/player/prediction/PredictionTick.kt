package com.lambda.util.player.prediction

import com.lambda.interaction.rotation.Rotation
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
    val predictionEntity: PredictionEntity
) {
    fun next() = with(predictionEntity) {
        tickMovement()
        lastTick
    }
}