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
import net.minecraft.block.Blocks
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
import net.minecraft.util.Identifier
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

    /**
     * What a landing on this block does to the body's downward velocity.
     *
     * Vanilla dispatches every touchdown through `Block.onEntityLand`, whose default is to
     * zero the vertical velocity -- so "landing stops you" is not a rule of the engine, it
     * is one block behaviour among several. Slime overrides it to reflect instead. Returning
     * 0.0 is the default and reproduces the old unconditional zeroing exactly.
     *
     * @see net.minecraft.block.SlimeBlock.onEntityLand
     */
    fun bounceFactor(pos: BlockPos): Double = 0.0

    /**
     * Whether standing on this block drags a slow-moving body to a crawl.
     *
     * Slime's `onSteppedOn`, which is a separate behaviour from the bounce and fires on the
     * same block: walking across slime is roughly half speed. Without it a tape that crosses
     * slime diverges from the client on the first tick it touches one.
     *
     * @see net.minecraft.block.SlimeBlock.onSteppedOn
     */
    fun dampensSteppingSpeed(pos: BlockPos): Boolean = false
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

    /**
     * Nearest colliding block under [box], measured from [entityPos].
     *
     * Not delegated to `world.findSupportingBlockPos(player, box)`: vanilla measures
     * "nearest" from its entity argument, which is the live player standing wherever it
     * happens to stand -- while the body being simulated is somewhere else entirely.
     * Near a block boundary that mis-measured distance attributes the wrong block, and
     * the supporting block decides friction, the velocity multipliers, and whether a
     * landing bounces: a simulated stride onto slime read the stone *behind* the live
     * player and never reflected. Same nearest-plus-BlockPos-tie-break as vanilla and
     * the snapshot environment, with the distance taken from the simulated body.
     */
    override fun findSupportingBlockPos(box: Box, entityPos: Vec3d): BlockPos? {
        val minX = net.minecraft.util.math.MathHelper.floor(box.minX - 1.0E-7) - 1
        val maxX = net.minecraft.util.math.MathHelper.floor(box.maxX + 1.0E-7) + 1
        val minY = net.minecraft.util.math.MathHelper.floor(box.minY - 1.0E-7) - 1
        val maxY = net.minecraft.util.math.MathHelper.floor(box.maxY + 1.0E-7) + 1
        val minZ = net.minecraft.util.math.MathHelper.floor(box.minZ - 1.0E-7) - 1
        val maxZ = net.minecraft.util.math.MathHelper.floor(box.maxZ + 1.0E-7) + 1

        var best: BlockPos? = null
        var bestDistance = Double.MAX_VALUE
        val mutable = BlockPos.Mutable()
        for (y in minY..maxY) {
            for (z in minZ..maxZ) {
                for (x in minX..maxX) {
                    val pos = mutable.set(x, y, z)
                    val shape = world.getBlockState(pos).getCollisionShape(world, pos)
                    if (shape.isEmpty) continue
                    val collides = shape
                        .offset(x.toDouble(), y.toDouble(), z.toDouble())
                        .boundingBoxes
                        .any { it.intersects(box) }
                    if (!collides) continue

                    val candidate = pos.toImmutable()
                    val distance = candidate.getSquaredDistance(entityPos)
                    if (distance < bestDistance ||
                        (distance == bestDistance && (best == null || best < candidate))
                    ) {
                        best = candidate
                        bestDistance = distance
                    }
                }
            }
        }
        return best
    }

    override fun isSpaceEmpty(box: Box): Boolean = world.isSpaceEmpty(player, box)

    override fun isFenceLike(pos: BlockPos): Boolean = world.getBlockState(pos).isFenceLike()

    override fun isClimbable(pos: BlockPos): Boolean =
        world.getBlockState(pos).isIn(BlockTags.CLIMBABLE)

    override fun bounceFactor(pos: BlockPos): Double = bounceFactorOf(world.getBlockState(pos))

    override fun dampensSteppingSpeed(pos: BlockPos): Boolean =
        dampensSteppingSpeedOf(world.getBlockState(pos))
}

/**
 * Vanilla's reflection factor for a landing on [state], or zero for an ordinary stop.
 *
 * One is a full reflection, which is what a living entity gets from slime -- the body leaves
 * with exactly the speed it arrived with. Beds bounce too, at two thirds, but nothing in the
 * planner has a reason to land on one yet.
 *
 * @see net.minecraft.block.SlimeBlock.bounce
 */
internal fun bounceFactorOf(state: BlockState): Double =
    if (state.isOf(Blocks.SLIME_BLOCK)) SLIME_BOUNCE_FACTOR else 0.0

internal fun dampensSteppingSpeedOf(state: BlockState): Boolean = state.isOf(Blocks.SLIME_BLOCK)

/** @see net.minecraft.block.SlimeBlock.bounce */
const val SLIME_BOUNCE_FACTOR = 1.0

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

        private val SPRINTING_SPEED_MODIFIER_ID = Identifier.ofVanilla("sprinting")

        /** Client thread only. */
        fun capture(player: ClientPlayerEntity): PlayerPhysicsProfile {
            val liveSpeed = player.movementSpeed.toDouble()
            // The sprint flag and the sprint attribute modifier can disagree for a tick
            // (setSprinting toggles them at a different stage than the flag readers);
            // dividing on the flag alone captured base/1.3 in that window and rejected
            // running tapes with a phantom PhysicsProfile deviation. Key off the
            // modifier that actually shaped the value we read.
            val sprintBoosted = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED)
                ?.hasModifier(SPRINTING_SPEED_MODIFIER_ID) == true
            // Pose is transient execution state, not part of the player's physics
            // configuration. Keep the profile stable while a tape toggles sneak.
            val standingDimensions = player.getDimensions(EntityPose.STANDING)
            return PlayerPhysicsProfile(
                movementSpeed = if (sprintBoosted) liveSpeed / SPRINT_SPEED_MULTIPLIER else liveSpeed,
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
