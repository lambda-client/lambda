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

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.module.modules.movement.SafeWalk.isNearLedge
import com.lambda.util.math.DOWN
import com.lambda.util.math.flooredBlockPos
import com.lambda.util.math.plus
import com.lambda.util.math.times
import com.lambda.util.player.MovementUtils.jumping
import com.lambda.util.player.MovementUtils.moveYaw
import com.lambda.util.player.MovementUtils.sneaking
import com.lambda.util.player.MovementUtils.sprinting
import com.lambda.util.player.MovementUtils.forward
import com.lambda.util.player.MovementUtils.strafe
import com.lambda.util.player.MovementUtils.movementVector
import net.minecraft.client.input.Input
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import kotlin.jvm.optionals.getOrNull
import kotlin.math.abs

/**
 * Reusable Minecraft-style movement simulator based on the client's own movement code.
 *
 * This is a refactor of the older prediction helper into an input-driven simulator
 * that can be reused by pathing, jump validation, shortcut refinement, and existing
 * callers like fall-damage prediction.
 *
 * World reads and player constants are injected ([SimulationEnvironment],
 * [PlayerPhysicsProfile]). The current live environment is client-thread only;
 * a future immutable planner snapshot can implement the same interface without
 * changing the movement equations. Live-entity extras (entity collisions and
 * the sneak ledge clamp) exist only when a [livePlayer] is attached.
 *
 * Still intentionally unsupported for now:
 * - fluids
 * - ladders / vines
 * - webs
 * - elytra
 * - exact sneak ledge clamping
 * - item-specific use-speed components
 */
class MovementSimulator(
    val profile: PlayerPhysicsProfile,
    private val environment: SimulationEnvironment,
    initialState: MovementSimulationState,
    private val inputProvider: MovementInputProvider = MovementInputProvider.none(),
    /**
     * Skips entity collision lookups and the live-player state mutation that
     * normally wraps them. Safe for shortcut/path validation where entities
     * are not part of the simulation's intent. Saves a hot-path allocation
     * and an unsafe player.pos / player.boundingBox swap. Forced on when no
     * [livePlayer] is attached.
     */
    var skipEntityCollisions: Boolean = false,
    private val livePlayer: ClientPlayerEntity? = null,
) {
    /** Client-thread entry: live world environment + profile captured now. */
    constructor(
        player: ClientPlayerEntity,
        initialState: MovementSimulationState = MovementSimulationState.from(player),
        inputProvider: MovementInputProvider = MovementInputProvider.live(player),
    ) : this(
        profile = PlayerPhysicsProfile.capture(player),
        environment = LiveSimulationEnvironment(player.entityWorld, player),
        initialState = initialState,
        inputProvider = inputProvider,
        livePlayer = player,
    )

    private var position = initialState.position
    private var velocity = initialState.velocity
    private var boundingBox = initialState.boundingBox
    private var rotation = initialState.rotation

    private var onGround = initialState.onGround
    private var isJumping = initialState.isJumping
    private var isSprinting = initialState.isSprinting
    private var isSneaking = initialState.isSneaking
    private var jumpingCooldown = initialState.jumpingCooldown
    private var velocityAffectingPos = initialState.velocityAffectingPos
    private var horizontalCollision = initialState.horizontalCollision
    private var collidedSoftly = initialState.collidedSoftly
    private var verticalCollision = initialState.verticalCollision
    private var supportingBlockPos = initialState.supportingBlockPos
    private var forceUpdateSupportingBlockPos = initialState.supportingBlockPos == null

    private var cachedTick: MovementSimulationTick? = null

    val state: MovementSimulationState
        get() = MovementSimulationState(
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
        )

    val lastTick: MovementSimulationTick
        get() = cachedTick ?: buildTick().also { cachedTick = it }

    private fun buildTick() = MovementSimulationTick(
        position = position,
        rotation = rotation,
        velocity = velocity,
        boundingBox = boundingBox,
        eyePos = position + Vec3d(0.0, profile.eyeHeight, 0.0),
        onGround = onGround,
        isJumping = isJumping,
        simulator = this,
    )

    fun reset(state: MovementSimulationState): MovementSimulationTick {
        position = state.position
        velocity = state.velocity
        boundingBox = state.boundingBox
        rotation = state.rotation
        onGround = state.onGround
        isJumping = state.isJumping
        isSprinting = state.isSprinting
        isSneaking = state.isSneaking
        jumpingCooldown = state.jumpingCooldown
        velocityAffectingPos = state.velocityAffectingPos
        horizontalCollision = state.horizontalCollision
        collidedSoftly = state.collidedSoftly
        verticalCollision = state.verticalCollision
        supportingBlockPos = state.supportingBlockPos
        forceUpdateSupportingBlockPos = state.supportingBlockPos == null
        cachedTick = null
        return lastTick
    }

    /** @see net.minecraft.client.network.ClientPlayerEntity.tickMovement */
    fun tickMovement(input: MovementSimulationInput? = null): MovementSimulationTick {
        cachedTick = null
        step(input ?: inputProvider.nextInput(this))
        return lastTick
    }

    /**
     * Planner-facing step that turns fail-closed snapshot reads into a typed
     * result. A rejected step is transactional: every simulator field is
     * restored to its value before the attempted input.
     */
    fun tryTickMovement(input: MovementSimulationInput? = null): MovementSimulationStepResult {
        val before = state
        return try {
            MovementSimulationStepResult.Advanced(tickMovement(input))
        } catch (failure: SimulationEnvironmentException) {
            reset(before)
            MovementSimulationStepResult.Rejected(failure)
        }
    }

    private fun step(input: MovementSimulationInput) {
        rotation = input.rotation ?: rotation
        isSneaking = input.sneak

        var movementInput = Vec2f(
            input.strafe.coerceIn(-1.0, 1.0).toFloat(),
            input.forward.coerceIn(-1.0, 1.0).toFloat(),
        ).normalize()

        // ClientPlayerEntity.tickMovement treats the sprint bit as a request to
        // *start* sprinting, not as the sprint state for this frame. Releasing the
        // key while forward remains held keeps vanilla sprinting; it stops only
        // when forward movement is lost (or a prior horizontal collision blocks
        // it). Flattened trajectory controllers can change their preferred gait at
        // a moving boundary, so assigning `isSprinting = input.sprint` here caused
        // the first post-splice frame to use walking acceleration in simulation
        // while the live player correctly retained sprint acceleration.
        val hasForwardMovement = movementInput.y > FORWARD_MOVEMENT_EPSILON
        if (!isSprinting && input.sprint && hasForwardMovement && !isSneaking) {
            isSprinting = true
        }
        if (isSprinting && (!hasForwardMovement || horizontalCollision && !collidedSoftly)) {
            isSprinting = false
        }

        // ClientPlayerEntity.applyMovementSpeedFactors. The final directional
        // factor is significant in 1.21.11: a full diagonal input recovers a
        // magnitude of 1.0 after the usual 0.98 input damping.
        if (movementInput.lengthSquared() != 0.0F) {
            movementInput = movementInput.multiply(0.98F)

            if (input.useItemSlowdown) {
                movementInput = movementInput.multiply(0.2F)
            }

            if (isSneaking) {
                movementInput = movementInput.multiply(profile.sneakSpeedModifier.toFloat())
            }

            movementInput = applyDirectionalMovementSpeedFactors(movementInput)
        }

        if (jumpingCooldown > 0) {
            --jumpingCooldown
        }

        // LivingEntity has a player-specific horizontal dead zone: the two
        // horizontal components are cleared together only when their squared
        // length is below 0.003^2. Vertical velocity is tested separately.
        val reduceHorizontal = velocity.horizontalLengthSquared() < 9.0E-6
        val reduceY = abs(velocity.y) < 0.003

        if (reduceHorizontal || reduceY) {
            velocity = Vec3d(
                if (reduceHorizontal) 0.0 else velocity.x,
                if (reduceY) 0.0 else velocity.y,
                if (reduceHorizontal) 0.0 else velocity.z
            )
        }

        // ClientPlayerEntity.tickMovementInput stores the held jump input in
        // LivingEntity.jumping. Releasing jump also clears the jump cooldown.
        isJumping = input.jump

        if (isJumping) {
            if (onGround && jumpingCooldown == 0) {
                jumpingCooldown = 10
                jump()
            }
        } else {
            jumpingCooldown = 0
        }

        travel(
            forwardSpeed = movementInput.y.toDouble(),
            strafeSpeed = movementInput.x.toDouble(),
        )
    }

    private fun applyDirectionalMovementSpeedFactors(input: Vec2f): Vec2f {
        val length = input.length()
        if (length <= 0.0F) return input

        val normalized = input.multiply(1.0F / length)
        val absStrafe = abs(normalized.x)
        val absForward = abs(normalized.y)
        val ratio = if (absForward > absStrafe) absStrafe / absForward else absForward / absStrafe
        val directionalMultiplier = MathHelper.sqrt(1.0F + ratio * ratio)
        return normalized.multiply(minOf(length * directionalMultiplier, 1.0F))
    }

    /** @see net.minecraft.entity.LivingEntity.travel */
    private fun travel(
        forwardSpeed: Double,
        strafeSpeed: Double,
    ) {
        // ClientPlayerEntity never writes LivingEntity.upwardSpeed during
        // ordinary walking. Jump and sneak are discrete controls handled in
        // their own branches, not a +/-Y movement input.
        val travelVec = Vec3d(strafeSpeed, 0.0, forwardSpeed)

        val gravity = when {
            velocity.y <= 0.0 && profile.slowFalling -> minOf(profile.gravity, 0.01)
            else -> profile.gravity
        }

        val slipperiness = environment.slipperiness(velocityAffectingPos)
        var friction = 0.91F.toDouble()

        if (onGround) {
            friction *= slipperiness
        }

        applyMovementInput(travelVec, slipperiness)
        move(travelVec)

        velocity += DOWN * gravity
        velocity *= Vec3d(friction, 0.98F.toDouble(), friction)
    }

    /** @see net.minecraft.entity.LivingEntity.applyMovementInput */
    private fun applyMovementInput(travelVec: Vec3d, slipperiness: Double) {
        val movementSpeed = run {
            val slipperinessCubed = slipperiness * slipperiness * slipperiness
            // The transient sprint attribute modifier is applied here rather
            // than read from the live entity: the profile is sprint-neutral,
            // so worker sims get identical ground acceleration no matter what
            // the real player happened to be doing at capture time.
            val movementSpeed = profile.movementSpeed *
                (if (isSprinting) PlayerPhysicsProfile.SPRINT_SPEED_MULTIPLIER else 1.0)

            val groundSpeed = movementSpeed * (0.21600002F / slipperinessCubed)
            val airSpeed = if (isSprinting) 0.026 else 0.02

            if (onGround) groundSpeed else airSpeed
        }.toFloat()

        /** @see net.minecraft.entity.Entity.updateVelocity */
        velocity += movementInputToVelocity(travelVec, movementSpeed, rotation.yawF)
    }

    /** @see net.minecraft.entity.Entity.move */
    private fun move(movementInput: Vec3d) {
        var movement = velocity
        movement = adjustMovementForCollisions(movement)

        if (isSneaking && isNearSimulatedLedge()) {
            movement = movement.multiply(0.0, 1.0, 0.0)
        }

        if (movement.lengthSquared() > 1.0E-7) {
            position += movement
        }

        val xCollide = !MathHelper.approximatelyEquals(movement.x, velocity.x)
        val yCollide = !MathHelper.approximatelyEquals(movement.y, velocity.y)
        val zCollide = !MathHelper.approximatelyEquals(movement.z, velocity.z)

        horizontalCollision = xCollide || zCollide
        collidedSoftly = horizontalCollision && hasCollidedSoftly(movementInput, movement)
        verticalCollision = yCollide

        // Vanilla tests the INTENDED vertical motion, not the collision-
        // adjusted one: standing still presses ~-0.078 into the floor and is
        // adjusted to 0.0, which must still count as grounded.
        onGround = yCollide && velocity.y < 0.0

        if (horizontalCollision) {
            velocity = Vec3d(
                if (xCollide) 0.0 else velocity.x,
                velocity.y,
                if (zCollide) 0.0 else velocity.z
            )
        }

        // Vanilla zeroes vertical velocity on any vertical collision
        // (Block.onEntityLand on touchdown, the head-bonk branch upward).
        // Without this, downward velocity survives a landing and cancels a
        // same-tick or next-tick jump — invisible to single-hop validations
        // that stop at the landing, fatal to anything simulated through it.
        if (verticalCollision) {
            velocity = Vec3d(velocity.x, 0.0, velocity.z)
        }

        val velocityMultiplier = run {
            val f = environment.velocityMultiplier(position.flooredBlockPos)
            val g = environment.velocityMultiplier(velocityAffectingPos)
            if (f == 1.0) g else f
        }

        velocity *= Vec3d(velocityMultiplier, 1.0, velocityMultiplier)

        boundingBox = normalizedBoundingBox().offset(position)
        updateSupportingBlockPos(onGround, movement)
        velocityAffectingPos = posWithYOffset(VELOCITY_AFFECTING_Y_OFFSET)
    }

    /** @see net.minecraft.client.network.ClientPlayerEntity.hasCollidedSoftly */
    private fun hasCollidedSoftly(input: Vec3d, adjustedMovement: Vec3d): Boolean {
        val yawRadians = rotation.yawF * (Math.PI / 180.0).toFloat()
        val sin = MathHelper.sin(yawRadians.toDouble()).toDouble()
        val cos = MathHelper.cos(yawRadians.toDouble()).toDouble()
        val intendedX = input.x * cos - input.z * sin
        val intendedZ = input.z * cos + input.x * sin
        val intendedSquared = intendedX * intendedX + intendedZ * intendedZ
        val adjustedSquared = adjustedMovement.x * adjustedMovement.x + adjustedMovement.z * adjustedMovement.z
        if (intendedSquared < SOFT_COLLISION_MIN_SQUARED || adjustedSquared < SOFT_COLLISION_MIN_SQUARED) {
            return false
        }

        val dot = intendedX * adjustedMovement.x + intendedZ * adjustedMovement.z
        val cosine = dot / kotlin.math.sqrt(intendedSquared * adjustedSquared)
        return kotlin.math.acos(cosine) < SOFT_COLLISION_MAX_ANGLE_RADIANS
    }

    /** @see net.minecraft.entity.Entity.updateSupportingBlockPos */
    private fun updateSupportingBlockPos(onGround: Boolean, movement: Vec3d?) {
        if (!onGround) {
            forceUpdateSupportingBlockPos = false
            supportingBlockPos = null
            return
        }

        // A paper-thin probe box directly beneath the feet.
        val probe = Box(
            boundingBox.minX, boundingBox.minY - 1.0E-6, boundingBox.minZ,
            boundingBox.maxX, boundingBox.minY, boundingBox.maxZ,
        )
        var found = environment.findSupportingBlockPos(probe, position)
        if (found != null || forceUpdateSupportingBlockPos) {
            supportingBlockPos = found
        } else if (movement != null) {
            // Nothing underneath now: vanilla retries where the entity came from,
            // so a step off a ledge keeps the block it actually pushed off.
            val rewound = probe.offset(-movement.x, 0.0, -movement.z)
            found = environment.findSupportingBlockPos(rewound, position)
            supportingBlockPos = found
        }
        forceUpdateSupportingBlockPos = found == null
    }

    /**
     * @see net.minecraft.entity.Entity.getPosWithYOffset
     *
     * The X and Z come from the *supporting block*, not from the column under
     * the entity's centre -- near a block edge those differ, and the difference
     * decides which block's friction the next tick reads.
     */
    private fun posWithYOffset(offset: Double): BlockPos {
        val supporting = supportingBlockPos
            ?: return (position + DOWN * offset).flooredBlockPos

        if (offset <= 1.0E-5) return supporting
        if (environment.isFenceLike(supporting)) return supporting
        return supporting.withY(MathHelper.floor(position.y - offset))
    }

    private companion object {
        /** @see net.minecraft.entity.Entity.getVelocityAffectingPos */
        const val VELOCITY_AFFECTING_Y_OFFSET = 0.500001

        /** @see net.minecraft.client.input.Input.hasForwardMovement */
        const val FORWARD_MOVEMENT_EPSILON = 1.0E-5F

        // ClientPlayerEntity spells both thresholds as float literals. Keeping the
        // float-to-double widening is observable right on the minimum-motion boundary:
        // 1.0E-5F is 9.999999747e-6, not the double literal 1.0E-5.
        private val SOFT_COLLISION_MIN_SQUARED = 1.0E-5F.toDouble()
        private val SOFT_COLLISION_MAX_ANGLE_RADIANS = 0.13962634F.toDouble()
    }

    /** @see net.minecraft.entity.LivingEntity.jump */
    private fun jump() {
        // Vanilla evaluates getJumpVelocity() entirely in float; keeping the
        // width here matters because the result seeds the whole airborne arc.
        val multiplier = run {
            val f = environment.jumpVelocityMultiplier(position.flooredBlockPos)
            val g = environment.jumpVelocityMultiplier(velocityAffectingPos)
            if (f == 1.0) g else f
        }.toFloat()
        val jumpVelocity = profile.jumpStrength.toFloat() * multiplier +
            profile.jumpBoostVelocityModifier.toFloat()
        if (jumpVelocity <= 1.0E-5F) return

        // Vanilla replaces ordinary grounded fall velocity, while preserving
        // a stronger pre-existing upward impulse.
        velocity = Vec3d(velocity.x, maxOf(jumpVelocity.toDouble(), velocity.y), velocity.z)

        if (isSprinting) {
            // Vanilla's sprint-jump boost reads the float yaw and goes through
            // MathHelper's 65536-entry sine table, whose ~5e-5 quantization error
            // is far larger than the differential tolerance. Math.sin would drift.
            val yawRad = rotation.yawF * (Math.PI / 180.0).toFloat()
            velocity += Vec3d(
                -MathHelper.sin(yawRad.toDouble()).toDouble() * 0.2,
                0.0,
                MathHelper.cos(yawRad.toDouble()).toDouble() * 0.2,
            )
        }
    }

    /** @see net.minecraft.entity.Entity.adjustMovementForCollisions */
    private fun adjustMovementForCollisions(movement: Vec3d): Vec3d {
        if (movement.lengthSquared() == 0.0) {
            return movement
        }

        val player = livePlayer
        if (skipEntityCollisions || player == null) {
            // Block-only collision adjustment through the injected environment.
            return environment.adjustMovementForCollisions(
                movement = movement,
                boundingBox = boundingBox,
                onGround = onGround,
                stepHeight = profile.stepHeight,
            )
        }

        return withSimulatedPlayerState(player) {
            // Access-widened vanilla instance method. Unlike the public static
            // collision helper, this includes the player's exact auto-step
            // candidate collection and selection logic.
            player.adjustMovementForCollisions(movement)
        }
    }

    private fun isNearSimulatedLedge(): Boolean {
        // Live-entity probe; planner-side sims never sneak, so a missing
        // live player simply skips the sneak edge clamp.
        val player = livePlayer ?: return false
        return withSimulatedPlayerState(player) {
            player.isNearLedge(0.01, 0.0)
        }
    }

    private fun <T> withSimulatedPlayerState(player: ClientPlayerEntity, block: () -> T): T {
        val prevPos = player.pos
        val prevBox = player.boundingBox
        val prevOnGround = player.isOnGround

        player.pos = position
        player.boundingBox = boundingBox
        player.setOnGround(onGround)

        return try {
            block()
        } finally {
            player.pos = prevPos
            player.boundingBox = prevBox
            player.setOnGround(prevOnGround)
        }
    }

    private fun normalizedBoundingBox(): Box =
        boundingBox.offset(-boundingBox.minX, -boundingBox.minY, -boundingBox.minZ)
            .offset(-boundingBox.lengthX * 0.5, 0.0, -boundingBox.lengthZ * 0.5)

    /**
     * Context-free body of `Entity.movementInputToVelocity`.
     *
     * Keeping this tiny equation local avoids initializing Minecraft's entity
     * registries in JVM-only simulator tests. The float trig operations mirror
     * vanilla; differential GameTests remain the authority for fidelity.
     */
    private fun movementInputToVelocity(input: Vec3d, speed: Float, yaw: Float): Vec3d {
        val lengthSquared = input.lengthSquared()
        if (lengthSquared < 1.0E-7) return Vec3d.ZERO

        val scaled = (if (lengthSquared > 1.0) input.normalize() else input).multiply(speed.toDouble())
        val radians = yaw * (Math.PI / 180.0).toFloat()
        val sin = MathHelper.sin(radians.toDouble())
        val cos = MathHelper.cos(radians.toDouble())
        return Vec3d(
            scaled.x * cos - scaled.z * sin,
            scaled.y,
            scaled.z * cos + scaled.x * sin,
        )
    }
}

data class MovementSimulationInput(
    val forward: Double = 0.0,
    val strafe: Double = 0.0,
    val jump: Boolean = false,
    val sneak: Boolean = false,
    val sprint: Boolean = false,
    val useItemSlowdown: Boolean = false,
    val rotation: Rotation? = null,
) {
    companion object {
        fun from(
            input: Input,
            rotation: Rotation? = null,
            useItemSlowdown: Boolean = false,
        ) = MovementSimulationInput(
            forward = input.forward.toDouble(),
            strafe = input.strafe.toDouble(),
            jump = input.jumping,
            sneak = input.sneaking,
            sprint = input.sprinting,
            useItemSlowdown = useItemSlowdown,
            rotation = rotation,
        )
    }
}

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
    /** Glancing collision which vanilla allows to retain sprint on the next tick. */
    val collidedSoftly: Boolean,
    val verticalCollision: Boolean,
    /**
     * The block the player is standing on. Null while airborne. Its X/Z are not
     * necessarily the column under the player's centre, and [velocityAffectingPos]
     * is derived from it, so it is physics-bearing and must be carried across ticks.
     */
    val supportingBlockPos: BlockPos? = null,
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
            // Vanilla derives this as supportingBlockPos.withY(floor(y - 0.500001)),
            // which is NOT the supporting block itself. Read its own answer.
            velocityAffectingPos: BlockPos = player.velocityAffectingPos,
            horizontalCollision: Boolean = player.horizontalCollision,
            collidedSoftly: Boolean = player.collidedSoftly,
            verticalCollision: Boolean = player.verticalCollision,
            boundingBox: Box = player.boundingBox.offset(position.subtract(player.pos)),
            supportingBlockPos: BlockPos? = player.supportingBlockPos.getOrNull(),
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

        /**
         * Player-free synthetic state with a standard bounding box from the
         * [profile], grounded at [position]. Whether it is safe off-thread is
         * determined by the supplied [SimulationEnvironment].
         */
        fun synthetic(
            profile: PlayerPhysicsProfile,
            position: Vec3d,
            rotation: Rotation,
            velocity: Vec3d = Vec3d.ZERO,
            onGround: Boolean = true,
            isSprinting: Boolean = false,
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
                isSneaking = false,
                jumpingCooldown = jumpingCooldown,
                velocityAffectingPos = (position + DOWN * 0.500001F.toDouble()).flooredBlockPos,
                horizontalCollision = false,
                collidedSoftly = false,
                verticalCollision = false,
            )
        }
    }
}

fun interface MovementInputProvider {
    fun nextInput(simulator: MovementSimulator): MovementSimulationInput

    companion object {
        fun live(player: ClientPlayerEntity) = MovementInputProvider { _ ->
            MovementSimulationInput.from(
                input = player.input,
                rotation = Rotation(player.moveYaw, player.pitch),
                useItemSlowdown = player.isUsingItem,
            ).copy(sprint = player.isSprinting)
        }

        /** For input-driven sims that always pass explicit inputs. */
        fun none() = MovementInputProvider { _ -> MovementSimulationInput() }
    }
}

data class MovementSimulationTick(
    val position: Vec3d,
    val rotation: Rotation,
    val velocity: Vec3d,
    val boundingBox: Box,
    val eyePos: Vec3d,
    val onGround: Boolean,
    val isJumping: Boolean,
    val simulator: MovementSimulator,
) {
    @Deprecated("Use simulator instead", ReplaceWith("simulator"))
    val predictionEntity get() = simulator

    fun next() = skipTicks(1)

    fun next(input: MovementSimulationInput) = simulator.tickMovement(input)

    fun skipTicks(amount: Int) = skipTicks(amount) { _, _ -> null }

    fun skipTicks(
        amount: Int,
        inputProvider: (tick: Int, current: MovementSimulationTick) -> MovementSimulationInput?,
    ) = with(simulator) {
        repeat(amount) { tick ->
            tickMovement(inputProvider(tick, lastTick))
        }

        lastTick
    }

    fun skipUntil(amount: Int = 20, block: (MovementSimulationTick) -> Boolean) =
        skipUntil(amount, { _, _ -> null }, block)

    fun skipUntil(
        amount: Int = 20,
        inputProvider: (tick: Int, current: MovementSimulationTick) -> MovementSimulationInput?,
        block: (MovementSimulationTick) -> Boolean,
    ) = with(simulator) {
        repeat(amount) { tick ->
            val prediction = tickMovement(inputProvider(tick, lastTick))
            if (block(prediction)) return@with prediction
        }

        return@with lastTick
    }
}

sealed interface MovementSimulationStepResult {
    data class Advanced(val tick: MovementSimulationTick) : MovementSimulationStepResult
    data class Rejected(val failure: SimulationEnvironmentException) : MovementSimulationStepResult
}
