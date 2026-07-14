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

package com.lambda.util.player.prediction

import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

/**
 * World-dependent operations used by [MovementSimulator].
 *
 * Keeping this interface separate from the movement equations lets focused
 * tests run the simulator against small deterministic environments. A future
 * path-planner snapshot can implement the same interface without changing the
 * simulator or its control contract.
 *
 * Initial scope is deliberately ordinary ground and airborne movement. Fluids,
 * climbables, webs, elytra, and context-sensitive block or item behavior are
 * not certified by this API yet.
 */
interface SimulationEnvironment {
    fun slipperiness(pos: BlockPos): Double
    fun velocityMultiplier(pos: BlockPos): Double
    fun jumpVelocityMultiplier(pos: BlockPos): Double

    /** Vanilla block collision adjustment for the supplied player box. */
    fun adjustMovementForCollisions(
        movement: Vec3d,
        boundingBox: Box,
        onGround: Boolean,
        stepHeight: Double,
    ): Vec3d
}

/** Client-thread environment backed by the live Minecraft world. */
class LiveSimulationEnvironment(
    private val world: World,
    private val player: ClientPlayerEntity,
) : SimulationEnvironment {
    override fun slipperiness(pos: BlockPos): Double =
        world.getBlockState(pos).block.slipperiness.toDouble()

    override fun velocityMultiplier(pos: BlockPos): Double =
        world.getBlockState(pos).block.velocityMultiplier.toDouble()

    override fun jumpVelocityMultiplier(pos: BlockPos): Double =
        world.getBlockState(pos).block.jumpVelocityMultiplier.toDouble()

    override fun adjustMovementForCollisions(
        movement: Vec3d,
        boundingBox: Box,
        onGround: Boolean,
        stepHeight: Double,
    ): Vec3d = VanillaBlockCollisionResolver.adjust(
        movement = movement,
        boundingBox = boundingBox,
        onGround = onGround,
        stepHeight = stepHeight,
        collisionShapes = { box -> world.getBlockCollisions(player, box).toList() },
    )
}

/** Immutable player constants captured once when a simulation is created. */
data class PlayerPhysicsProfile(
    /** Movement speed with the transient sprint modifier removed. */
    val movementSpeed: Double,
    val sneakSpeedModifier: Double,
    val gravity: Double,
    val jumpStrength: Double,
    val stepHeight: Double,
    val jumpBoostVelocityModifier: Double,
    val slowFalling: Boolean,
    val width: Double,
    val height: Double,
    val eyeHeight: Double,
) {
    companion object {
        const val SPRINT_SPEED_MULTIPLIER = 1.3

        /** Client thread only. */
        fun capture(player: ClientPlayerEntity): PlayerPhysicsProfile {
            val liveSpeed = player.movementSpeed.toDouble()
            return PlayerPhysicsProfile(
                movementSpeed = if (player.isSprinting) liveSpeed / SPRINT_SPEED_MULTIPLIER else liveSpeed,
                sneakSpeedModifier = player.getAttributeValue(EntityAttributes.SNEAKING_SPEED),
                gravity = player.getAttributeValue(EntityAttributes.GRAVITY),
                jumpStrength = player.getAttributeValue(EntityAttributes.JUMP_STRENGTH),
                stepHeight = player.stepHeight.toDouble(),
                jumpBoostVelocityModifier = player.jumpBoostVelocityModifier.toDouble(),
                slowFalling = player.hasStatusEffect(StatusEffects.SLOW_FALLING),
                width = player.boundingBox.lengthX,
                height = player.boundingBox.lengthY,
                eyeHeight = player.standingEyeHeight.toDouble(),
            )
        }
    }
}
