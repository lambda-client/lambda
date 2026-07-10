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

import com.lambda.worldview.WorldView
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.World

/**
 * The world half of a movement simulation: block physics constants and
 * collision resolution. Splitting this from [MovementSimulator] is what
 * makes planner-side simulation legal off the client thread — the worker
 * simulates against a [SnapshotSimulationEnvironment] whose only inputs are
 * the session's [WorldView] and immutable trait tables, while client-thread
 * callers (launch gate, air control, fall prediction) keep the live world.
 */
interface SimulationEnvironment {
    fun slipperiness(pos: BlockPos): Double
    fun velocityMultiplier(pos: BlockPos): Double
    fun jumpVelocityMultiplier(pos: BlockPos): Double

    /** Vanilla `Entity.adjustMovementForCollisions`, blocks only. */
    fun adjustMovementForCollisions(movement: Vec3d, boundingBox: Box): Vec3d
}

/**
 * Live-world environment. Client thread only — reads chunk data and block
 * properties through the same paths the vanilla movement tick uses.
 */
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

    override fun adjustMovementForCollisions(movement: Vec3d, boundingBox: Box): Vec3d =
        Entity.adjustMovementForCollisions(player, movement, boundingBox, world, emptyList())
}

/**
 * Snapshot environment over a [WorldView]: any-thread reads, no Minecraft
 * world objects on the hot path. Collision uses the context-free per-state
 * shapes from the trait table and vanilla's own axis-ordered clipping
 * ([VoxelShapes.calculateMaxOffset] over [Direction.getCollisionOrder]), so
 * the physics matches the live path exactly wherever shapes are exact.
 * Unknown terrain carries conservative full-cube traits — a simulation can
 * only be *stricter* than the observed world, never validate through it.
 *
 * Known deltas from the live path, both accepted: no world-border shape, and
 * context-dependent block shapes (scaffolding, moving pistons) fall back to
 * the conservative trait cube. The executor's live launch gate re-validates
 * every maneuver at execution time, so a divergence costs a held jump and a
 * repair, never a blind launch.
 */
class SnapshotSimulationEnvironment(private val view: WorldView) : SimulationEnvironment {
    override fun slipperiness(pos: BlockPos): Double =
        view.traits(pos.x, pos.y, pos.z).slipperiness

    override fun velocityMultiplier(pos: BlockPos): Double =
        view.traits(pos.x, pos.y, pos.z).velocityMultiplier

    override fun jumpVelocityMultiplier(pos: BlockPos): Double =
        view.traits(pos.x, pos.y, pos.z).jumpVelocityMultiplier

    override fun adjustMovementForCollisions(movement: Vec3d, boundingBox: Box): Vec3d {
        if (movement.lengthSquared() == 0.0) return movement
        val shapes = collectBlockShapes(boundingBox.stretch(movement))
        if (shapes.isEmpty()) return movement

        // Mirror of the private vanilla Entity.adjustMovementForCollisions
        // (Vec3d, Box, List<VoxelShape>) — per-axis clipping in vanilla's
        // collision order, box advanced by the accumulated adjustment.
        var adjusted = Vec3d.ZERO
        for (axis in Direction.getCollisionOrder(movement)) {
            val component = movement.getComponentAlongAxis(axis)
            if (component == 0.0) continue
            val offset = VoxelShapes.calculateMaxOffset(axis, boundingBox.offset(adjusted), shapes, component)
            adjusted = adjusted.withAxis(axis, offset)
        }
        return adjusted
    }

    /**
     * Non-empty collision shapes intersecting the swept box, iterated one
     * voxel beyond it on every axis like vanilla's BlockCollisionSpliterator
     * — shapes taller than a block (fences, walls) live in neighbouring
     * voxels the box itself never overlaps.
     */
    private fun collectBlockShapes(swept: Box): List<VoxelShape> {
        val minX = MathHelper.floor(swept.minX - EPSILON) - 1
        val maxX = MathHelper.floor(swept.maxX + EPSILON) + 1
        val minY = MathHelper.floor(swept.minY - EPSILON) - 1
        val maxY = MathHelper.floor(swept.maxY + EPSILON) + 1
        val minZ = MathHelper.floor(swept.minZ - EPSILON) - 1
        val maxZ = MathHelper.floor(swept.maxZ + EPSILON) + 1

        var shapes: ArrayList<VoxelShape>? = null
        for (y in minY..maxY) {
            for (z in minZ..maxZ) {
                for (x in minX..maxX) {
                    val traits = view.traits(x, y, z)
                    if (traits.passable) continue
                    val shape = traits.collisionShape
                    if (shape.isEmpty) continue
                    (shapes ?: ArrayList<VoxelShape>().also { shapes = it }) +=
                        shape.offset(x.toDouble(), y.toDouble(), z.toDouble())
                }
            }
        }
        return shapes ?: emptyList()
    }

    private companion object {
        const val EPSILON = 1.0E-7
    }
}

/**
 * Immutable physical constants of the simulated player, captured on the
 * client thread once per planning session. Worker simulations must never
 * read the live entity — mid-tick attribute or pose reads from another
 * thread are torn state, and torn sims validate jumps the executor then
 * fails in the field.
 */
data class PlayerPhysicsProfile(
    /**
     * MOVEMENT_SPEED attribute value *without* the transient sprint
     * modifier; the simulator re-applies vanilla's +30% itself when a tick
     * runs with sprint held. Capturing this way makes the profile identical
     * whether the session started standing or mid-sprint.
     */
    val movementSpeed: Double,
    /** `0.3 + SNEAKING_SPEED attribute` — the sneak input multiplier. */
    val sneakSpeedModifier: Double,
    /** `LivingEntity.jumpBoostVelocityModifier` (jump boost effect). */
    val jumpBoostVelocityModifier: Double,
    /** Slow-falling gravity applies while descending. */
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
                sneakSpeedModifier = 0.3 + player.getAttributeValue(EntityAttributes.SNEAKING_SPEED),
                jumpBoostVelocityModifier = player.jumpBoostVelocityModifier.toDouble(),
                slowFalling = player.hasStatusEffect(StatusEffects.SLOW_FALLING),
                width = player.boundingBox.lengthX,
                height = player.boundingBox.lengthY,
                eyeHeight = player.standingEyeHeight.toDouble(),
            )
        }
    }
}
