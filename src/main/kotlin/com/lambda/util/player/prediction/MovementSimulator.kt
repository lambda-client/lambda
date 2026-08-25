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
import com.lambda.mixin.entity.ClientPlayerEntityAccessor
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
    private var poseHeight = initialState.boundingBox.lengthY
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
    private var doubleTapSprintTicks = initialState.doubleTapSprintTicks
    private var hadForwardMovement = initialState.hadForwardMovement
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
            doubleTapSprintTicks = doubleTapSprintTicks,
            hadForwardMovement = hadForwardMovement,
        )

    val lastTick: MovementSimulationTick
        get() = cachedTick ?: buildTick().also { cachedTick = it }

    private fun buildTick() = MovementSimulationTick(
        position = position,
        rotation = rotation,
        velocity = velocity,
        boundingBox = boundingBox,
        eyePos = position + Vec3d(0.0, poseEyeHeight(), 0.0),
        onGround = onGround,
        isJumping = isJumping,
        simulator = this,
    )

    fun reset(state: MovementSimulationState): MovementSimulationTick {
        position = state.position
        velocity = state.velocity
        boundingBox = state.boundingBox
        poseHeight = state.boundingBox.lengthY
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
        doubleTapSprintTicks = state.doubleTapSprintTicks
        hadForwardMovement = state.hadForwardMovement
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
        // ClientPlayerEntity.tickMovement decrements the double-tap window first, and
        // reads sneak and forward *before* `Input.tick()` -- which is where the manager
        // writes the tape, so both describe the frame before this one.
        if (doubleTapSprintTicks > 0) doubleTapSprintTicks--
        val hadForward = hadForwardMovement
        // Vanilla's `inSneakingPose`, which it latches from `isSneaking()` before taking
        // this tick's input -- so it is last tick's sneak.
        val inSneakingPose = isSneaking
        val wasSneaking = inSneakingPose

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
        if (wasSneaking || input.forward < 0.0) doubleTapSprintTicks = 0

        // `canStartSprinting` is gated on `shouldSlowDown()`, which reads the *pose* rather
        // than the key, so it lags with everything else the pose drives.
        if (!isSprinting && hasForwardMovement && !inSneakingPose) {
            // Double-tap-to-sprint. Releasing forward for a tick and pressing it again is
            // a double tap whether a human or a tape does it, and vanilla starts sprinting
            // from it with no sprint key held at all. The simulator not modelling this is
            // what made a certified tape diverge live at a splice boundary: the sim walked
            // the frame after the gap while the body sprinted it.
            if (!hadForward) {
                if (doubleTapSprintTicks > 0) isSprinting = true
                else doubleTapSprintTicks = profile.sprintWindowTicks
            }
            if (input.sprint) isSprinting = true
        }
        if (isSprinting && (!hasForwardMovement || horizontalCollision && !collidedSoftly)) {
            isSprinting = false
        }
        hadForwardMovement = hasForwardMovement

        // ClientPlayerEntity.applyMovementSpeedFactors. The final directional
        // factor is significant in 1.21.11: a full diagonal input recovers a
        // magnitude of 1.0 after the usual 0.98 input damping.
        if (movementInput.lengthSquared() != 0.0F) {
            movementInput = movementInput.multiply(0.98F)

            if (input.useItemSlowdown) {
                movementInput = movementInput.multiply(0.2F)
            }

            // The slowdown comes from the sneaking *pose*, not the sneak key, and vanilla
            // computes the pose before `Input.tick()` overwrites the input -- so the factor
            // applied this tick is the one the body was in last tick.
            //
            // Applying it on the tick the key goes down made the simulation a tenth of a
            // block per tick slower than the client on the first tick of every brake, which
            // is a position deviation an order of magnitude past the replay tolerance.
            // Nothing noticed while no tape ever sneaked.
            //
            // Only the slowdown lags. `clipAtLedge` reads `isSneaking()` live, and that is
            // resolved during `move` below, after the input has been taken.
            if (inSneakingPose) {
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

        tickBlockCollision()

        // Last, exactly as in vanilla: PlayerEntity.tick() calls updatePose() after
        // super.tick() has already run the movement. So a tick that presses sneak moves
        // with the standing box and ends holding the crouching one.
        updatePose()
    }

    /**
     * Swaps the body between the standing and crouching boxes.
     *
     * Two things about the timing, and both were needed to match the client. It runs at the
     * *end* of the tick, after the move, so the box a tick moves with is the pose it started
     * in. And it reads the sneak key live rather than the lagged `inSneakingPose` -- vanilla
     * derives the pose from `isSneaking()`, so the box shrinks on the very tick the key goes
     * down even though the speed multiplier does not arrive until the next one.
     *
     * That combination is why a tape that sneaks diverged on `box.maxY` alone: every other
     * number matched to the last digit because the movement really was identical, and only
     * the height the body ended the tick at was wrong.
     *
     * Standing up is refused when the taller box would not fit, which is what keeps a body
     * crouched under a slab instead of clipping its head through one.
     *
     * @see net.minecraft.entity.player.PlayerEntity.updatePose
     */
    private fun updatePose() {
        val expected = if (isSneaking) profile.crouchHeight else profile.height
        if (expected == poseHeight) return
        // Shrinking always fits; only standing up has to ask the world. An environment that
        // does not answer space queries is taken to allow it -- being unable to check is not
        // a reason to trap the body in a crouch.
        //
        // The live client's stand-up tick is not exactly reproducible: measured over one
        // descending walk, it stood on the release tick at one ledge and one tick later at
        // the next, with bit-identical positions, velocities and inputs at both. Whatever
        // vanilla conditions that on is invisible in the state this simulator carries, so
        // the model stays the source-faithful same-tick swap and the replay comparator
        // deliberately does not gate on the box height -- a pose that diverges in a way
        // that matters shows up in position within a frame and is caught there.
        if (expected > poseHeight && environment.isSpaceEmpty(poseBox(expected)) == false) return

        poseHeight = expected
        boundingBox = Box(
            boundingBox.minX, boundingBox.minY, boundingBox.minZ,
            boundingBox.maxX, boundingBox.minY + expected, boundingBox.maxZ,
        )
    }

    /** @see net.minecraft.entity.player.PlayerEntity.canChangeIntoPose */
    private fun poseBox(height: Double): Box {
        val halfWidth = boundingBox.lengthX * 0.5
        return Box(
            position.x - halfWidth + POSE_FIT_EPSILON,
            position.y + POSE_FIT_EPSILON,
            position.z - halfWidth + POSE_FIT_EPSILON,
            position.x + halfWidth - POSE_FIT_EPSILON,
            position.y + height - POSE_FIT_EPSILON,
            position.z + halfWidth - POSE_FIT_EPSILON,
        )
    }

    private fun poseEyeHeight(): Double =
        if (poseHeight < profile.height) profile.crouchEyeHeight else profile.eyeHeight

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

        // The clamp reads the hold the body starts the tick in; vanilla applies it before
        // moving, so a body that is on a ladder now has its motion capped now.
        if (isClimbing()) velocity = applyClimbingSpeed(velocity)

        move(travelVec)

        // Vanilla re-asserts a fixed rise the tick *after* moving, so the displacement a
        // climb actually makes is what gravity and drag leave of it: ~0.1176 per tick, not
        // the 0.2 written here.
        //
        // And it re-tests the hold at the position the move *ended* at, not the one it
        // started from. Reusing the pre-move answer kept the rise going for one tick after
        // the body had already left the column, which is a tenth of a block per tick of
        // pure invention -- enough that a certified tape and the live client disagree about
        // vertical velocity the moment a climb is stepped off, and replay aborts.
        if (isClimbing() && (horizontalCollision || isJumping)) {
            velocity = Vec3d(velocity.x, CLIMB_RISE_SPEED, velocity.z)
        }

        velocity += DOWN * gravity
        velocity *= Vec3d(friction, 0.98F.toDouble(), friction)
    }

    /** @see net.minecraft.entity.LivingEntity.isClimbing */
    private fun isClimbing(): Boolean = environment.isClimbable(position.flooredBlockPos)

    /**
     * Clamps a climbing body's motion.
     *
     * A climb is not a gait -- there is no acceleration to it. Horizontal motion is capped,
     * the fall is floored into a controlled slide, and sneaking stops the slide entirely,
     * which is how a player parks on a ladder.
     *
     * @see net.minecraft.entity.LivingEntity.applyClimbingSpeed
     */
    private fun applyClimbingSpeed(motion: Vec3d): Vec3d {
        val holding = isSneaking && motion.y < 0.0
        return Vec3d(
            motion.x.coerceIn(-CLIMB_HORIZONTAL_CAP, CLIMB_HORIZONTAL_CAP),
            if (holding) 0.0 else maxOf(motion.y, -CLIMB_FALL_CAP),
            motion.z.coerceIn(-CLIMB_HORIZONTAL_CAP, CLIMB_HORIZONTAL_CAP),
        )
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
        // Vanilla clips a sneaking body at a ledge *before* resolving collisions, and the
        // order matters: the clip decides how far the body is allowed to try to go, and the
        // collision pass then resolves whatever is left against the world.
        //
        // What matters just as much is that vanilla *reassigns* its movement local with the
        // clipped value -- `movement = this.adjustMovementForSneaking(movement, type)` --
        // so every collision comparison below is against the clipped request rather than
        // the raw velocity. A ledge clip is therefore not a collision. It shortens the step
        // and leaves the body's speed entirely alone.
        val requested = adjustMovementForSneaking(velocity)
        val movement = adjustMovementForCollisions(requested)

        if (movement.lengthSquared() > 1.0E-7) {
            position += movement
        }

        val xCollide = !MathHelper.approximatelyEquals(movement.x, requested.x)
        val yCollide = !MathHelper.approximatelyEquals(movement.y, requested.y)
        val zCollide = !MathHelper.approximatelyEquals(movement.z, requested.z)

        horizontalCollision = xCollide || zCollide
        collidedSoftly = horizontalCollision && hasCollidedSoftly(movementInput, movement)
        verticalCollision = yCollide

        // Vanilla tests the INTENDED vertical motion, not the collision-
        // adjusted one: standing still presses ~-0.078 into the floor and is
        // adjusted to 0.0, which must still count as grounded. The sneak clip
        // never touches y, so the clipped request is the intended motion here.
        onGround = yCollide && requested.y < 0.0

        if (horizontalCollision) {
            velocity = Vec3d(
                if (xCollide) 0.0 else velocity.x,
                velocity.y,
                if (zCollide) 0.0 else velocity.z
            )
        }

        // Vanilla settles the supporting block before it asks what the body landed on, and
        // the landing probe reads through it, so the order is not cosmetic.
        boundingBox = normalizedBoundingBox().offset(position)
        updateSupportingBlockPos(onGround, movement)

        if (verticalCollision) onEntityLand()

        val velocityMultiplier = run {
            val f = environment.velocityMultiplier(position.flooredBlockPos)
            val g = environment.velocityMultiplier(velocityAffectingPos)
            if (f == 1.0) g else f
        }

        velocity *= Vec3d(velocityMultiplier, 1.0, velocityMultiplier)

        velocityAffectingPos = posWithYOffset(VELOCITY_AFFECTING_Y_OFFSET)
    }

    /**
     * What the block underfoot does to the body's vertical velocity on touchdown.
     *
     * "Landing stops you" is not a rule of the engine -- it is one block behaviour among
     * several, and it happens to be the default. Slime overrides it to reflect, which is the
     * whole of the bounce: the body leaves with exactly the speed it arrived with, and its
     * horizontal velocity is never touched, so a bounce carries momentum straight through.
     *
     * The block asked is the one 0.2 blocks below the feet, not the one being stood on. That
     * is what makes a carpet laid over a slime block still bounce -- the carpet is only
     * 0.0625 thick, so the probe passes through it and finds the slime. Anything modelled
     * off the supporting block instead would silently get that case wrong.
     *
     * Sneaking suppresses it. That interacts with the drop control, which presses sneak to
     * pin itself at a ledge: a descent that sneaks onto slime does not bounce.
     *
     * @see net.minecraft.entity.Entity.move
     * @see net.minecraft.block.SlimeBlock.onEntityLand
     */
    private fun onEntityLand() {
        val bounce = if (isSneaking) 0.0 else environment.bounceFactor(posWithYOffset(LANDING_Y_OFFSET))
        velocity = if (bounce > 0.0 && velocity.y < 0.0) {
            Vec3d(velocity.x, -velocity.y * bounce, velocity.z)
        } else {
            Vec3d(velocity.x, 0.0, velocity.z)
        }
    }

    /**
     * Slime's drag on a body that is walking rather than bouncing.
     *
     * Separate from the bounce and read off the same block. The velocity threshold is what
     * keeps the two apart: a body still carrying vertical speed is mid-bounce and is left
     * alone, while one that has settled is dragged to roughly half speed.
     *
     * Runs after the move, where vanilla runs it -- `LivingEntity.tickMovement` calls
     * `travel` and then `tickBlockCollision`.
     *
     * @see net.minecraft.block.SlimeBlock.onSteppedOn
     */
    private fun tickBlockCollision() {
        if (!onGround) return
        val vertical = abs(velocity.y)
        if (vertical >= STEPPING_DRAG_MAX_VERTICAL) return
        if (!environment.dampensSteppingSpeed(posWithYOffset(LANDING_Y_OFFSET))) return

        val drag = STEPPING_DRAG_BASE + vertical * STEPPING_DRAG_VERTICAL_SCALE
        velocity = Vec3d(velocity.x * drag, velocity.y, velocity.z * drag)
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
        /** @see net.minecraft.entity.player.PlayerEntity.adjustMovementForSneaking */
        const val LEDGE_CLIP_STEP = 0.05

        /** @see net.minecraft.entity.player.PlayerEntity.isSpaceAroundPlayerEmpty */
        const val LEDGE_CLIP_EPSILON = 1.0E-7

        /** @see net.minecraft.entity.player.PlayerEntity.canChangeIntoPose */
        const val POSE_FIT_EPSILON = 1.0E-7

        /** @see net.minecraft.entity.Entity.getLandingPos */
        const val LANDING_Y_OFFSET = 0.2

        /** @see net.minecraft.block.SlimeBlock.onSteppedOn */
        const val STEPPING_DRAG_MAX_VERTICAL = 0.1
        const val STEPPING_DRAG_BASE = 0.4
        const val STEPPING_DRAG_VERTICAL_SCALE = 0.2

        /** @see net.minecraft.entity.LivingEntity.applyClimbingSpeed */
        const val CLIMB_HORIZONTAL_CAP = 0.15

        const val CLIMB_FALL_CAP = 0.15

        /** Re-asserted each tick a climbing body is pressed into its hold. */
        const val CLIMB_RISE_SPEED = 0.2

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

    /**
     * Vanilla's sneak ledge clip: shrink the movement until it has somewhere to land.
     *
     * One axis at a time, in five-hundredth steps, testing whether the box the body would
     * occupy -- dropped by a step height -- has anything under it. This is what stops a
     * sneaking player walking off a block, and it is a *movement* clamp rather than a speed
     * one: the body still carries its velocity, it simply is not allowed to leave the edge.
     *
     * Previously this needed a live entity to probe, so worker simulations answered "no
     * ledge" everywhere and could not model sneaking at all. Asking the environment instead
     * makes the behaviour available to the planner, which is what lets a control program use
     * sneak as an instrument rather than only as a speed multiplier.
     *
     * @see net.minecraft.entity.player.PlayerEntity.adjustMovementForSneaking
     */
    private fun adjustMovementForSneaking(movement: Vec3d): Vec3d {
        // Vanilla's guard is `clipAtLedge() && isStandingOnSurface(stepHeight)`, where the
        // first is simply "is sneaking". The second is `isOnGround()` plus a fall-distance
        // grace for a body that has only just left the floor -- the simulator carries no
        // fall distance, so being grounded stands in for it.
        if (!isSneaking || !onGround || movement.y > 0.0) return movement

        val step = profile.stepHeight
        var dx = movement.x
        var dz = movement.z

        while (dx != 0.0 && isSpaceUnderFeetEmpty(dx, 0.0, step)) dx = shrinkTowardsZero(dx)
        while (dz != 0.0 && isSpaceUnderFeetEmpty(0.0, dz, step)) dz = shrinkTowardsZero(dz)
        while (dx != 0.0 && dz != 0.0 && isSpaceUnderFeetEmpty(dx, dz, step)) {
            dx = shrinkTowardsZero(dx)
            dz = shrinkTowardsZero(dz)
        }

        return if (dx == movement.x && dz == movement.z) movement else Vec3d(dx, movement.y, dz)
    }

    /**
     * Whether the floor is missing under where the feet would land.
     *
     * A thin slab beneath the body rather than the body's own box dropped by a step, and
     * the difference is not cosmetic: the full box would catch on anything beside the body
     * at head height and report solid ground where there is none. Only what is under the
     * feet decides whether there is somewhere to stand.
     *
     * @see net.minecraft.entity.player.PlayerEntity.isSpaceAroundPlayerEmpty
     */
    private fun isSpaceUnderFeetEmpty(offsetX: Double, offsetZ: Double, stepHeight: Double): Boolean =
        environment.isSpaceEmpty(
            Box(
                boundingBox.minX + LEDGE_CLIP_EPSILON + offsetX,
                boundingBox.minY - stepHeight - LEDGE_CLIP_EPSILON,
                boundingBox.minZ + LEDGE_CLIP_EPSILON + offsetZ,
                boundingBox.maxX - LEDGE_CLIP_EPSILON + offsetX,
                boundingBox.minY,
                boundingBox.maxZ - LEDGE_CLIP_EPSILON + offsetZ,
            )
        ) == true

    private fun shrinkTowardsZero(delta: Double): Double =
        if (abs(delta) <= LEDGE_CLIP_STEP) 0.0
        else delta - Math.signum(delta) * LEDGE_CLIP_STEP

    private fun <T> withSimulatedPlayerState(player: ClientPlayerEntity, block: () -> T): T {
        val prevPos = player.pos
        val prevBox = player.boundingBox
        val prevOnGround = player.isOnGround

        player.pos = position
        player.boundingBox = boundingBox
	    player.isOnGround = onGround

        return try {
            block()
        } finally {
            player.pos = prevPos
            player.boundingBox = prevBox
	        player.isOnGround = prevOnGround
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
    /**
     * Ticks left in vanilla's double-tap-to-sprint window.
     *
     * Physics-bearing across ticks: within it, re-pressing forward starts a sprint with
     * no sprint key. @see net.minecraft.client.network.ClientPlayerEntity.tickMovement
     */
    val doubleTapSprintTicks: Int = 0,
    /** Whether the *previous* tick's input had forward movement; opens the window above. */
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
            // Vanilla derives this as supportingBlockPos.withY(floor(y - 0.500001)),
            // which is NOT the supporting block itself. Read its own answer.
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
