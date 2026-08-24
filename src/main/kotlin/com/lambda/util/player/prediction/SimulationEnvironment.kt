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

import net.minecraft.block.BlockState
import net.minecraft.block.FenceGateBlock
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.EntityPose
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import kotlin.jvm.optionals.getOrNull
import kotlin.math.abs
import kotlin.math.max

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

    /**
     * Nearest block colliding with [box], by squared distance to [entityPos] with
     * a BlockPos-order tie-break.
     *
     * This is what the entity is *standing on*, and it is not the same as the
     * column under its centre: near a block edge the supporting block's X/Z can
     * differ from `floor(pos)`. Vanilla reads friction and the velocity/jump
     * multipliers from it, so getting it wrong reads the wrong block's physics.
     *
     * @see net.minecraft.world.CollisionView.findSupportingBlockPos
     */
    fun findSupportingBlockPos(box: Box, entityPos: Vec3d): BlockPos?

    /** Fences, walls and fence gates anchor the velocity-affecting pos to themselves. */
    fun isFenceLike(pos: BlockPos): Boolean

    /**
     * Whether a body standing at [pos] is holding a ladder, vine or other climbable.
     *
     * Defaulted so environments written before climbing existed keep compiling and keep
     * behaving exactly as they did.
     *
     * @see net.minecraft.entity.LivingEntity.isClimbing
     */
    fun isClimbable(pos: BlockPos): Boolean = false

    /**
     * Whether [box] is free of block collisions.
     *
     * Needed by vanilla's sneak ledge clipping, which shrinks a sneaking body's movement
     * one axis at a time until the box it would occupy, dropped by a step height, finds
     * something to stand on. That is the mechanism that lets a player walk to the very lip
     * of a drop and stop dead on it.
     *
     * Also needed by the crouch pose, which vanilla refuses to leave when standing up would
     * put the body's head in a block.
     *
     * Three-valued on purpose. `null` is "this environment does not answer space queries",
     * and the two callers want opposite answers to that: the ledge clip must not fire on an
     * environment it cannot reason about, while a body must still be allowed to stand up in
     * one. Collapsing that into a single boolean default gives one of them the wrong
     * behaviour silently, which is exactly how a body ends up permanently crouched.
     *
     * @see net.minecraft.entity.player.PlayerEntity.adjustMovementForSneaking
     * @see net.minecraft.entity.player.PlayerEntity.updatePose
     */
    fun isSpaceEmpty(box: Box): Boolean? = null
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

    override fun findSupportingBlockPos(box: Box, entityPos: Vec3d): BlockPos? =
        world.findSupportingBlockPos(player, box).getOrNull()

    override fun isSpaceEmpty(box: Box): Boolean = world.isSpaceEmpty(player, box)

    override fun isFenceLike(pos: BlockPos): Boolean = world.getBlockState(pos).isFenceLike()

    override fun isClimbable(pos: BlockPos): Boolean =
        world.getBlockState(pos).isIn(BlockTags.CLIMBABLE)
}

/** @see net.minecraft.entity.Entity.getPosWithYOffset */
internal fun BlockState.isFenceLike(): Boolean =
    isIn(BlockTags.FENCES) || isIn(BlockTags.WALLS) || block is FenceGateBlock

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
    /**
     * The crouching box, which vanilla swaps to at the end of any tick the sneak key is
     * down and back at the end of the first tick it is not.
     *
     * Part of the profile rather than transient state because it is a constant of the
     * entity type: which pose the body is *in* is execution state, how tall each pose is
     * is not. Defaulted to vanilla's player so every existing construction still describes
     * a real body.
     */
    val crouchHeight: Double = VANILLA_CROUCH_HEIGHT,
    val crouchEyeHeight: Double = VANILLA_CROUCH_EYE_HEIGHT,
    /**
     * Ticks after releasing forward during which pressing it again starts a sprint.
     *
     * Vanilla's double-tap-to-sprint window (`options.sprintWindow`, default 7). It is a
     * client *option*, not a physics constant, which is why it is captured rather than
     * assumed -- a player who has turned it off has different physics from one who has
     * not, for the same keys.
     */
    val sprintWindowTicks: Int = DEFAULT_SPRINT_WINDOW_TICKS,
) {
    /**
     * Runtime attributes can pass through float sprint modifiers before being
     * exposed as doubles. Treat that round-trip noise as the same profile while
     * still rejecting material attribute, effect, option, or dimension changes.
     */
    fun isCompatibleWith(other: PlayerPhysicsProfile): Boolean =
        movementSpeed.closeTo(other.movementSpeed) &&
            sneakSpeedModifier.closeTo(other.sneakSpeedModifier) &&
            gravity.closeTo(other.gravity) &&
            jumpStrength.closeTo(other.jumpStrength) &&
            stepHeight.closeTo(other.stepHeight) &&
            jumpBoostVelocityModifier.closeTo(other.jumpBoostVelocityModifier) &&
            slowFalling == other.slowFalling &&
            width.closeTo(other.width) &&
            height.closeTo(other.height) &&
            eyeHeight.closeTo(other.eyeHeight) &&
            crouchHeight.closeTo(other.crouchHeight) &&
            crouchEyeHeight.closeTo(other.crouchEyeHeight) &&
            sprintWindowTicks == other.sprintWindowTicks

    private fun Double.closeTo(other: Double): Boolean =
        abs(this - other) <= PROFILE_EPSILON * max(1.0, max(abs(this), abs(other)))

    companion object {
        const val SPRINT_SPEED_MULTIPLIER = 1.3

        private const val PROFILE_EPSILON = 1e-7

        /** @see net.minecraft.client.option.GameOptions.getSprintWindow */
        const val DEFAULT_SPRINT_WINDOW_TICKS = 7

        /** @see net.minecraft.entity.EntityPose.CROUCHING */
        const val VANILLA_CROUCH_HEIGHT = 1.5
        const val VANILLA_CROUCH_EYE_HEIGHT = 1.27

        /** Client thread only. */
        fun capture(player: ClientPlayerEntity): PlayerPhysicsProfile {
            val liveSpeed = player.movementSpeed.toDouble()
            // Pose is transient execution state, not part of the player's physics
            // configuration. Keep the profile stable while a tape toggles sneak.
            val standingDimensions = player.getDimensions(EntityPose.STANDING)
            return PlayerPhysicsProfile(
                movementSpeed = if (player.isSprinting) liveSpeed / SPRINT_SPEED_MULTIPLIER else liveSpeed,
                sneakSpeedModifier = player.getAttributeValue(EntityAttributes.SNEAKING_SPEED),
                gravity = player.getAttributeValue(EntityAttributes.GRAVITY),
                jumpStrength = player.getAttributeValue(EntityAttributes.JUMP_STRENGTH),
                stepHeight = player.stepHeight.toDouble(),
                jumpBoostVelocityModifier = player.jumpBoostVelocityModifier.toDouble(),
                slowFalling = player.hasStatusEffect(StatusEffects.SLOW_FALLING),
                width = standingDimensions.width.toDouble(),
                height = standingDimensions.height.toDouble(),
                eyeHeight = player.getEyeHeight(EntityPose.STANDING).toDouble(),
                crouchHeight = player.getDimensions(EntityPose.CROUCHING).height.toDouble(),
                crouchEyeHeight = player.getEyeHeight(EntityPose.CROUCHING).toDouble(),
                sprintWindowTicks = MinecraftClient.getInstance().options.sprintWindow.value,
            )
        }
    }
}
