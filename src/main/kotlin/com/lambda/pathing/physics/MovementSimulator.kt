package com.lambda.pathing.physics

import com.lambda.util.math.DOWN
import com.lambda.util.math.flooredBlockPos
import com.lambda.util.math.plus
import com.lambda.util.math.times
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sign
import kotlin.math.sqrt

class MovementSimulator(
	val profile: PlayerPhysicsProfile,
	private val environment: SimulationEnvironment,
	initialState: MovementSimulationState,
) {

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

	val eyeHeight: Double get() = poseEyeHeight()

	fun reset(state: MovementSimulationState) {
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
	}

	fun tickMovement(input: MovementSimulationInput): MovementSimulationState {
		step(input)
		return state
	}

	fun tryTickMovement(input: MovementSimulationInput): MovementSimulationStepResult {
		val before = state
		return try {
			MovementSimulationStepResult.Advanced(tickMovement(input))
		} catch (failure: SimulationEnvironmentException) {
			reset(before)
			MovementSimulationStepResult.Rejected(failure)
		}
	}

	fun stepFrom(before: MovementSimulationState, input: MovementSimulationInput): MovementSimulationState? {
		lastFailure = null
		return try {
			step(input)
			state
		} catch (failure: SimulationEnvironmentException) {
			reset(before)
			lastFailure = failure
			null
		}
	}

	var lastFailure: SimulationEnvironmentException? = null
		private set

	private fun step(input: MovementSimulationInput) {
		if (doubleTapSprintTicks > 0) doubleTapSprintTicks--
		val hadForward = hadForwardMovement
		val inSneakingPose = isSneaking

		rotation = input.rotation ?: rotation
		isSneaking = input.sneak

		var movementInput = Vec2f(
			input.strafe.coerceIn(-1.0, 1.0).toFloat(),
			input.forward.coerceIn(-1.0, 1.0).toFloat(),
		).normalize()

		val hasForwardMovement = movementInput.y > FORWARD_MOVEMENT_EPSILON
		if (inSneakingPose || input.forward < 0.0) doubleTapSprintTicks = 0

		if (!isSprinting && hasForwardMovement && !inSneakingPose) {
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

		if (movementInput.lengthSquared() != 0.0F) {
			movementInput = movementInput.multiply(0.98F)

			if (input.useItemSlowdown) {
				movementInput = movementInput.multiply(0.2F)
			}

			if (inSneakingPose) {
				movementInput = movementInput.multiply(profile.sneakSpeedModifier.toFloat())
			}

			movementInput = applyDirectionalMovementSpeedFactors(movementInput)
		}

		if (jumpingCooldown > 0) {
			--jumpingCooldown
		}

		val reduceHorizontal = velocity.horizontalLengthSquared() < 9.0E-6
		val reduceY = abs(velocity.y) < 0.003

		if (reduceHorizontal || reduceY) {
			velocity = Vec3d(
				if (reduceHorizontal) 0.0 else velocity.x,
				if (reduceY) 0.0 else velocity.y,
				if (reduceHorizontal) 0.0 else velocity.z
			)
		}

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
		updatePose()
	}

	private fun updatePose() {
		val expected = if (isSneaking) profile.crouchHeight else profile.height
		if (expected == poseHeight) return
		if (expected > poseHeight && environment.isSpaceEmpty(poseBox(expected)) == false) return

		poseHeight = expected
		boundingBox = Box(
			boundingBox.minX, boundingBox.minY, boundingBox.minZ,
			boundingBox.maxX, boundingBox.minY + expected, boundingBox.maxZ,
		)
	}

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

	private fun travel(
		forwardSpeed: Double,
		strafeSpeed: Double,
	) {
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
		if (isClimbing()) velocity = applyClimbingSpeed(velocity)

		move(travelVec)

		if (isClimbing() && (horizontalCollision || isJumping)) {
			velocity = Vec3d(velocity.x, CLIMB_RISE_SPEED, velocity.z)
		}

		velocity += DOWN * gravity
		velocity *= Vec3d(friction, 0.98F.toDouble(), friction)
	}

	private fun isClimbing(): Boolean = environment.isClimbable(position.flooredBlockPos)

	private fun applyClimbingSpeed(motion: Vec3d): Vec3d {
		val holding = isSneaking && motion.y < 0.0
		return Vec3d(
			motion.x.coerceIn(-CLIMB_HORIZONTAL_CAP, CLIMB_HORIZONTAL_CAP),
			if (holding) 0.0 else maxOf(motion.y, -CLIMB_FALL_CAP),
			motion.z.coerceIn(-CLIMB_HORIZONTAL_CAP, CLIMB_HORIZONTAL_CAP),
		)
	}

	private fun applyMovementInput(travelVec: Vec3d, slipperiness: Double) {
		val movementSpeed = run {
			val slipperinessCubed = slipperiness * slipperiness * slipperiness
			val movementSpeed = profile.movementSpeed *
					(if (isSprinting) PlayerPhysicsProfile.SPRINT_SPEED_MULTIPLIER else 1.0)

			val groundSpeed = movementSpeed * (0.21600002F / slipperinessCubed)
			val airSpeed = if (isSprinting) 0.026 else 0.02

			if (onGround) groundSpeed else airSpeed
		}.toFloat()

		velocity += movementInputToVelocity(travelVec, movementSpeed, rotation.yawF)
	}

	private fun move(movementInput: Vec3d) {
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
		onGround = yCollide && requested.y < 0.0

		if (horizontalCollision) {
			velocity = Vec3d(
				if (xCollide) 0.0 else velocity.x,
				velocity.y,
				if (zCollide) 0.0 else velocity.z
			)
		}

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

	private fun onEntityLand() {
		val bounce = if (isSneaking) 0.0 else environment.bounceFactor(posWithYOffset(LANDING_Y_OFFSET))
		velocity = if (bounce > 0.0 && velocity.y < 0.0) {
			Vec3d(velocity.x, -velocity.y * bounce, velocity.z)
		} else {
			Vec3d(velocity.x, 0.0, velocity.z)
		}
	}

	private fun tickBlockCollision() {
		if (!onGround) return
		val vertical = abs(velocity.y)
		if (vertical >= STEPPING_DRAG_MAX_VERTICAL) return
		if (!environment.dampensSteppingSpeed(posWithYOffset(LANDING_Y_OFFSET))) return

		val drag = STEPPING_DRAG_BASE + vertical * STEPPING_DRAG_VERTICAL_SCALE
		velocity = Vec3d(velocity.x * drag, velocity.y, velocity.z * drag)
	}

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
		val cosine = dot / sqrt(intendedSquared * adjustedSquared)
		return acos(cosine) < SOFT_COLLISION_MAX_ANGLE_RADIANS
	}

	private fun updateSupportingBlockPos(onGround: Boolean, movement: Vec3d?) {
		if (!onGround) {
			forceUpdateSupportingBlockPos = false
			supportingBlockPos = null
			return
		}

		val probe = Box(
			boundingBox.minX, boundingBox.minY - 1.0E-6, boundingBox.minZ,
			boundingBox.maxX, boundingBox.minY, boundingBox.maxZ,
		)
		var found = environment.findSupportingBlockPos(probe, position)
		if (found != null || forceUpdateSupportingBlockPos) {
			supportingBlockPos = found
		} else if (movement != null) {
			val rewound = probe.offset(-movement.x, 0.0, -movement.z)
			found = environment.findSupportingBlockPos(rewound, position)
			supportingBlockPos = found
		}
		forceUpdateSupportingBlockPos = found == null
	}

	private fun posWithYOffset(offset: Double): BlockPos {
		val supporting = supportingBlockPos
			?: return (position + DOWN * offset).flooredBlockPos

		if (offset <= 1.0E-5) return supporting
		if (environment.isFenceLike(supporting)) return supporting
		return supporting.withY(MathHelper.floor(position.y - offset))
	}

	private companion object {

		const val LEDGE_CLIP_STEP = 0.05

		const val LEDGE_CLIP_EPSILON = 1.0E-7

		const val POSE_FIT_EPSILON = 1.0E-7

		const val LANDING_Y_OFFSET = 0.2

		const val STEPPING_DRAG_MAX_VERTICAL = 0.1
		const val STEPPING_DRAG_BASE = 0.4
		const val STEPPING_DRAG_VERTICAL_SCALE = 0.2

		const val CLIMB_HORIZONTAL_CAP = 0.15

		const val CLIMB_FALL_CAP = 0.15

		const val CLIMB_RISE_SPEED = 0.2

		const val VELOCITY_AFFECTING_Y_OFFSET = 0.500001

		const val FORWARD_MOVEMENT_EPSILON = 1.0E-5F

		private const val SOFT_COLLISION_MIN_SQUARED = 1.0E-5F.toDouble()
		private const val SOFT_COLLISION_MAX_ANGLE_RADIANS = 0.13962634F.toDouble()
	}

	private fun jump() {
		val multiplier = run {
			val f = environment.jumpVelocityMultiplier(position.flooredBlockPos)
			val g = environment.jumpVelocityMultiplier(velocityAffectingPos)
			if (f == 1.0) g else f
		}.toFloat()
		val jumpVelocity = profile.jumpStrength.toFloat() * multiplier +
				profile.jumpBoostVelocityModifier.toFloat()
		if (jumpVelocity <= 1.0E-5F) return

		velocity = Vec3d(velocity.x, maxOf(jumpVelocity.toDouble(), velocity.y), velocity.z)

		if (isSprinting) {
			val yawRad = rotation.yawF * (Math.PI / 180.0).toFloat()
			velocity += Vec3d(
				-MathHelper.sin(yawRad.toDouble()).toDouble() * 0.2,
				0.0,
				MathHelper.cos(yawRad.toDouble()).toDouble() * 0.2,
			)
		}
	}

	private fun adjustMovementForCollisions(movement: Vec3d): Vec3d {
		if (movement.lengthSquared() == 0.0) {
			return movement
		}
		return environment.adjustMovementForCollisions(
			movement = movement,
			boundingBox = boundingBox,
			onGround = onGround,
			stepHeight = profile.stepHeight,
		)
	}

	private fun adjustMovementForSneaking(movement: Vec3d): Vec3d {
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
		else delta - sign(delta) * LEDGE_CLIP_STEP

	private fun normalizedBoundingBox(): Box =
		boundingBox.offset(-boundingBox.minX, -boundingBox.minY, -boundingBox.minZ)
			.offset(-boundingBox.lengthX * 0.5, 0.0, -boundingBox.lengthZ * 0.5)

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
