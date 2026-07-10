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
import com.lambda.util.math.MathUtils.toDouble
import com.lambda.util.math.MathUtils.toRadian
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
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import kotlin.jvm.optionals.getOrNull
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Reusable Minecraft-style movement simulator based on the client's own movement code.
 *
 * This is a refactor of the older prediction helper into an input-driven simulator
 * that can be reused by pathing, jump validation, shortcut refinement, and existing
 * callers like fall-damage prediction.
 *
 * World reads and player constants are injected ([SimulationEnvironment],
 * [PlayerPhysicsProfile]), so a simulation over a snapshot environment is
 * legal on any thread — the planner worker's discovery/validation sims use
 * this. Live-entity extras (entity collisions, the sneak ledge clamp) exist
 * only when a [livePlayer] is attached, i.e. on the client thread.
 *
 * Still intentionally unsupported for now:
 * - fluids
 * - ladders / vines
 * - webs
 * - elytra
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
    private var verticalCollision = initialState.verticalCollision

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
            verticalCollision = verticalCollision,
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
        verticalCollision = state.verticalCollision
        cachedTick = null
        return lastTick
    }

    /** @see net.minecraft.client.network.ClientPlayerEntity.tickMovement */
    fun tickMovement(input: MovementSimulationInput? = null): MovementSimulationTick {
        cachedTick = null
        step(input ?: inputProvider.nextInput(this))
        return lastTick
    }

    private fun step(input: MovementSimulationInput) {
        rotation = input.rotation ?: rotation
        isSprinting = input.sprint
        isSneaking = input.sneak

        var forwardMovement = input.forward.coerceIn(-1.0, 1.0)
        var strafeMovement = input.strafe.coerceIn(-1.0, 1.0)

        val inputMagnitude = hypot(forwardMovement, strafeMovement)
        if (inputMagnitude > 1.0) {
            forwardMovement /= inputMagnitude
            strafeMovement /= inputMagnitude
        }

        var forwardSpeed = forwardMovement
        var strafeSpeed = strafeMovement

        if (input.useItemSlowdown) {
            forwardSpeed *= 0.2
            strafeSpeed *= 0.2
        }

        if (isSneaking) {
            forwardSpeed *= profile.sneakSpeedModifier
            strafeSpeed *= profile.sneakSpeedModifier
        }

        if (jumpingCooldown > 0) {
            --jumpingCooldown
        }

        val reduceX = abs(velocity.x) < 0.03
        val reduceY = abs(velocity.y) < 0.03
        val reduceZ = abs(velocity.z) < 0.03

        if (reduceX || reduceY || reduceZ) {
            velocity = Vec3d(
                if (reduceX) 0.0 else velocity.x,
                if (reduceY) 0.0 else velocity.y,
                if (reduceZ) 0.0 else velocity.z
            )
        }

        isJumping = false

        if (input.jump && onGround && jumpingCooldown == 0) {
            jumpingCooldown = 10
            isJumping = true
            jump()
        }

        forwardSpeed *= 0.98
        strafeSpeed *= 0.98

        travel(
            forwardSpeed = forwardSpeed,
            strafeSpeed = strafeSpeed,
            verticalMovement = input.jump.toDouble() - input.sneak.toDouble(),
        )
    }

    /** @see net.minecraft.entity.LivingEntity.travel */
    private fun travel(
        forwardSpeed: Double,
        strafeSpeed: Double,
        verticalMovement: Double,
    ) {
        val travelVec = Vec3d(strafeSpeed, verticalMovement, forwardSpeed)

        val gravity = when {
            velocity.y < 0.0 && profile.slowFalling -> 0.01
            else -> 0.08
        }

        val slipperiness = environment.slipperiness(velocityAffectingPos)
        var friction = 0.91

        if (onGround) {
            friction *= slipperiness
        }

        applyMovementInput(travelVec, slipperiness)
        move()

        velocity += DOWN * gravity
        velocity *= Vec3d(friction, 0.98, friction)
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

            val groundSpeed = movementSpeed * (0.216 / slipperinessCubed)
            val airSpeed = if (isSprinting) 0.026 else 0.02

            if (onGround) groundSpeed else airSpeed
        }.toFloat()

        /** @see net.minecraft.entity.Entity.updateVelocity */
        velocity += Entity.movementInputToVelocity(travelVec, movementSpeed, rotation.yawF)
    }

    /** @see net.minecraft.entity.Entity.move */
    private fun move() {
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
        velocityAffectingPos = (position + DOWN * 0.001).flooredBlockPos
    }

    /** @see net.minecraft.entity.LivingEntity.jump */
    private fun jump() {
        if (isSprinting) {
            // Vanilla's sprint-jump boost along the facing yaw (the context-
            // free body of MovementUtils.movementVector).
            val yawRad = rotation.yaw.toRadian()
            velocity += Vec3d(-kotlin.math.sin(yawRad), 0.0, kotlin.math.cos(yawRad)) * 0.2
        }

        val jumpHeight = run {
            val f = environment.jumpVelocityMultiplier(position.flooredBlockPos)
            val g = environment.jumpVelocityMultiplier(velocityAffectingPos)
            if (f == 1.0) g else f
        } * 0.42 + profile.jumpBoostVelocityModifier

        velocity += Vec3d(0.0, jumpHeight, 0.0)
    }

    /** @see net.minecraft.entity.Entity.adjustMovementForCollisions */
    private fun adjustMovementForCollisions(movement: Vec3d): Vec3d {
        if (movement.lengthSquared() == 0.0) {
            return movement
        }

        val player = livePlayer
        if (skipEntityCollisions || player == null) {
            // Block-only collision adjustment through the environment: the
            // live one defers to vanilla, the snapshot one resolves against
            // trait-table shapes and is legal on any thread.
            return environment.adjustMovementForCollisions(movement, boundingBox)
        }

        return withSimulatedPlayerState(player) {
            val world = player.entityWorld
            val list = world.getEntityCollisions(player, boundingBox.stretch(movement))
            Entity.adjustMovementForCollisions(player, movement, boundingBox, world, list)
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

        player.pos = position
        player.boundingBox = boundingBox

        return try {
            block()
        } finally {
            player.pos = prevPos
            player.boundingBox = prevBox
        }
    }

    private fun normalizedBoundingBox(): Box =
        boundingBox.offset(-boundingBox.minX, -boundingBox.minY, -boundingBox.minZ)
            .offset(-boundingBox.lengthX * 0.5, 0.0, -boundingBox.lengthZ * 0.5)
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
    val verticalCollision: Boolean,
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
            velocityAffectingPos: BlockPos = player.supportingBlockPos.getOrNull()
                ?: (position + DOWN * 0.001).flooredBlockPos,
            horizontalCollision: Boolean = player.horizontalCollision,
            verticalCollision: Boolean = player.verticalCollision,
            boundingBox: Box = player.boundingBox.offset(position.subtract(player.pos)),
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
            verticalCollision = verticalCollision,
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
            verticalCollision = false,
        )

        /**
         * Player-free synthetic state for off-thread simulations: standard
         * bounding box from the [profile], grounded at [position]. This is
         * the discovery/validation entry point — it must never touch the
         * live entity.
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
                velocityAffectingPos = (position + DOWN * 0.001).flooredBlockPos,
                horizontalCollision = false,
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
            )
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
