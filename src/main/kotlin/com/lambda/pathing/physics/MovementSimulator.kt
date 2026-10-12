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
	private val profile: PlayerPhysicsProfile,
	private val environment: SimulationEnvironment,
	initialState: MovementSimulationState,
) {
	private var position = initialState.position
	private var velocity = initialState.velocity
	private var boundingBox = initialState.boundingBox
	private var pose = Pose.entries.minBy { abs(it.height() - initialState.boundingBox.lengthY) }
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
	private var forceUpdateSupportingBlockPos = initialState.supportingBlockPos == null && initialState.onGround
	private var touchingWater = initialState.touchingWater
	private var waterHeight = initialState.waterHeight
	private var eyeInWater = initialState.eyeInWater
	private var submergedInWater = initialState.submergedInWater
	private var swimming = initialState.swimming
	private var air = initialState.air
	private val cell = BlockPos.Mutable()
	private var lastMovementY = 0.0

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
			touchingWater = touchingWater,
			waterHeight = waterHeight,
			eyeInWater = eyeInWater,
			submergedInWater = submergedInWater,
			swimming = swimming,
			air = air,
		)

	val burningFeet: Boolean
		get() = onGround && !isSneaking && environment.hotFloor(posWithYOffset(Vanilla.LANDING_POS_OFFSET.toDouble()))

	fun burnsNextTick(input: MovementSimulationInput): Boolean {
		if (isSneaking || !nearHotFloor()) return false
		val next = MovementSimulator(profile, environment, state)
		return try {
			next.tickMovement(input)
			next.burningFeet
		} catch (_: SimulationEnvironmentException) {
			false
		}
	}

	private fun nearHotFloor(): Boolean {
		val reach = boundingBox.stretch(velocity).expand(1.0)
		return environment.mayContainHotFloor(
			MathHelper.floor(reach.minX), MathHelper.floor(reach.minY), MathHelper.floor(reach.minZ),
			MathHelper.floor(reach.maxX), MathHelper.floor(reach.maxY), MathHelper.floor(reach.maxZ),
		)
	}

	fun tickMovement(input: MovementSimulationInput): MovementSimulationState {
		submergedInWater = eyeInWater
		updateWaterState()
		eyeInWater = isEyeInWater()
		air = if (eyeInWater && profile.canDrown && !eyeInBubbles()) air - 1 else minOf(air + Vanilla.AIR_REFILL_PER_TICK, Vanilla.MAX_AIR)
		updateSwimming()

		if (doubleTapSprintTicks > 0) doubleTapSprintTicks--
		val hadForward = hadForwardMovement
		val inSneakingPose = !swimming && if (pose == Pose.STANDING) isSneaking else canChangeInto(Pose.CROUCHING) && (isSneaking || !canChangeInto(Pose.STANDING))
		val slowedDown = inSneakingPose || pose == Pose.SWIMMING && !touchingWater
		val partlyInWater = touchingWater && !submergedInWater

		rotation = input.rotation ?: rotation
		isSneaking = input.sneak

		var movementInput = Vec2f(
			input.strafe.coerceIn(-1.0, 1.0).toFloat(),
			input.forward.coerceIn(-1.0, 1.0).toFloat(),
		).normalize()

		val hasForwardMovement = movementInput.y > Vanilla.FORWARD_INPUT_EPSILON
		if (inSneakingPose || input.forward < 0.0) doubleTapSprintTicks = 0

		if (!isSprinting && profile.canSprint && hasForwardMovement && !partlyInWater && (!slowedDown || submergedInWater)) {
			if (!hadForward) {
				if (doubleTapSprintTicks > 0) isSprinting = true
				else doubleTapSprintTicks = profile.sprintWindowTicks
			}
			if (input.sprint) isSprinting = true
		}
		val stopsSprinting = if (swimming) {
			!touchingWater || !hasForwardMovement && !onGround && !input.sneak
		} else {
			!profile.canSprint || partlyInWater || !hasForwardMovement || horizontalCollision && !collidedSoftly
		}
		if (isSprinting && stopsSprinting) isSprinting = false
		if (touchingWater && input.sneak) velocity = velocity.add(0.0, -Vanilla.WATER_SINK_SPEED.toDouble(), 0.0)
		hadForwardMovement = hasForwardMovement

		if (movementInput.lengthSquared() != 0.0F) {
			movementInput = movementInput.multiply(Vanilla.INPUT_DAMPING)

			if (input.useItemSlowdown) {
				movementInput = movementInput.multiply(Vanilla.USING_ITEM_SPEED_FACTOR)
			}

			if (slowedDown) {
				movementInput = movementInput.multiply(profile.sneakSpeedModifier.toFloat())
			}

			movementInput = applyDirectionalMovementSpeedFactors(movementInput)
		}

		if (jumpingCooldown > 0) {
			--jumpingCooldown
		}

		val reduceHorizontal = velocity.horizontalLengthSquared() < Vanilla.MIN_PLAYER_HORIZONTAL_SPEED_SQUARED
		val reduceY = abs(velocity.y) < Vanilla.MIN_VERTICAL_SPEED

		if (reduceHorizontal || reduceY) {
			velocity = Vec3d(
				if (reduceHorizontal) 0.0 else velocity.x,
				if (reduceY) 0.0 else velocity.y,
				if (reduceHorizontal) 0.0 else velocity.z
			)
		}

		isJumping = input.jump

		if (isJumping) {
			val inWater = touchingWater && waterHeight > 0.0
			if (!inWater || onGround && !(waterHeight > profile.swimHeight)) {
				if ((onGround || inWater && waterHeight <= profile.swimHeight) && jumpingCooldown == 0) {
					jumpingCooldown = Vanilla.JUMP_COOLDOWN_TICKS
					jump()
				}
			} else {
				velocity = velocity.add(0.0, Vanilla.SWIM_UP_SPEED.toDouble(), 0.0)
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
		return state
	}

	private enum class Pose { STANDING, CROUCHING, SWIMMING }

	private fun Pose.height(): Double = when (this) {
		Pose.STANDING -> profile.height
		Pose.CROUCHING -> profile.crouchHeight
		Pose.SWIMMING -> profile.swimmingHeight
	}

	private fun Pose.eyeHeight(): Double = when (this) {
		Pose.STANDING -> profile.standingEyeHeight
		Pose.CROUCHING -> profile.crouchingEyeHeight
		Pose.SWIMMING -> profile.swimmingEyeHeight
	}

	private fun updatePose() {
		val expected = when {
			swimming -> Pose.SWIMMING
			isSneaking -> Pose.CROUCHING
			else -> Pose.STANDING
		}
		if (expected == pose || !canChangeInto(Pose.SWIMMING)) return
		val next = when {
			canChangeInto(expected) -> expected
			canChangeInto(Pose.CROUCHING) -> Pose.CROUCHING
			else -> Pose.SWIMMING
		}
		if (next == pose) return
		pose = next
		boundingBox = poseBox(next)
	}

	private fun canChangeInto(pose: Pose): Boolean = environment.isSpaceEmpty(poseBox(pose).contract(Vanilla.POSE_CLEARANCE))

	private fun poseBox(pose: Pose): Box {
		val halfWidth = profile.width / 2.0
		return Box(
			position.x - halfWidth, position.y, position.z - halfWidth,
			position.x + halfWidth, position.y + pose.height(), position.z + halfWidth,
		)
	}

	private fun updateWaterState() {
		val bottom = boundingBox.minY + Vanilla.FLUID_BOX_CONTRACTION
		val x0 = MathHelper.floor(boundingBox.minX + Vanilla.FLUID_BOX_CONTRACTION)
		val x1 = MathHelper.ceil(boundingBox.maxX - Vanilla.FLUID_BOX_CONTRACTION)
		val y0 = MathHelper.floor(bottom)
		val y1 = MathHelper.ceil(boundingBox.maxY - Vanilla.FLUID_BOX_CONTRACTION)
		val z0 = MathHelper.floor(boundingBox.minZ + Vanilla.FLUID_BOX_CONTRACTION)
		val z1 = MathHelper.ceil(boundingBox.maxZ - Vanilla.FLUID_BOX_CONTRACTION)
		waterHeight = 0.0
		touchingWater = false
		if (!environment.mayContainWater(x0, y0, z0, x1 - 1, y1 - 1, z1 - 1)) return

		var depth = 0.0
		var touching = false
		var push = Vec3d.ZERO
		var cells = 0
		for (x in x0 until x1) {
			for (y in y0 until y1) {
				for (z in z0 until z1) {
					val water = environment.water(cell.set(x, y, z)) ?: continue
					val surface = (y + water.height).toDouble()
					if (surface < bottom) continue
					touching = true
					depth = maxOf(surface - bottom, depth)
					push = push.add(if (depth < Vanilla.SHALLOW_PUSH_DEPTH) water.flow.multiply(depth) else water.flow)
					cells++
				}
			}
		}
		if (push.length() > 0.0) {
			if (cells > 0) push = push.multiply(1.0 / cells)
			push = push.multiply(Vanilla.WATER_PUSH_SPEED)
			if (abs(velocity.x) < Vanilla.WATER_MIN_PUSH_SPEED && abs(velocity.z) < Vanilla.WATER_MIN_PUSH_SPEED && push.length() < Vanilla.WATER_MIN_PUSH) {
				push = push.normalize().multiply(Vanilla.WATER_MIN_PUSH)
			}
			velocity = velocity.add(push)
		}
		waterHeight = depth
		touchingWater = touching
	}

	private fun isEyeInWater(): Boolean {
		if (!touchingWater) return false
		val eyeY = position.y + pose.eyeHeight()
		val water = environment.water(cell.set(position.x, eyeY, position.z)) ?: return false
		return (cell.y + water.height).toDouble() > eyeY
	}

	private fun updateSwimming() {
		swimming = if (swimming) {
			isSprinting && touchingWater
		} else {
			isSprinting && submergedInWater && environment.water(cell.set(position.x, position.y, position.z)) != null
		}
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

	private fun travel(
		forwardSpeed: Double,
		strafeSpeed: Double,
	) {
		val travelVec = Vec3d(strafeSpeed, 0.0, forwardSpeed)
		if (swimming) steerSwimming()
		if (touchingWater) travelInWater(travelVec) else travelMidAir(travelVec)
	}

	private fun effectiveGravity(): Double = when {
		velocity.y <= 0.0 && profile.slowFalling -> minOf(profile.gravity, Vanilla.SLOW_FALLING_MAX_GRAVITY)
		else -> profile.gravity
	}

	private fun steerSwimming() {
		val look = -MathHelper.sin((rotation.pitchF * MathHelper.RADIANS_PER_DEGREE).toDouble()).toDouble()
		val rate = if (look < Vanilla.SWIM_DIVING_LOOK) Vanilla.SWIM_STEER_DIVING else Vanilla.SWIM_STEER
		if (look <= 0.0 || isJumping || environment.water(cell.set(position.x, position.y + 1.0 - 0.1, position.z)) != null) {
			velocity = velocity.add(0.0, (look - velocity.y) * rate, 0.0)
		}
	}

	private fun travelInWater(travelVec: Vec3d) {
		val falling = velocity.y <= 0.0
		val startY = position.y
		val gravity = effectiveGravity()
		var drag = if (isSprinting) Vanilla.SPRINT_WATER_DRAG else Vanilla.WATER_DRAG
		var acceleration = Vanilla.WATER_ACCELERATION
		var efficiency = profile.waterMovementEfficiency.toFloat()
		if (!onGround) efficiency *= 0.5F
		if (efficiency > 0.0F) {
			drag += (Vanilla.DEPTH_STRIDER_WATER_DRAG - drag) * efficiency
			acceleration += (movementSpeed() - acceleration) * efficiency
		}
		if (profile.dolphinsGrace) drag = Vanilla.DOLPHINS_GRACE_WATER_DRAG

		velocity += movementInputToVelocity(travelVec, acceleration, rotation.yawF)
		move(travelVec)

		var motion = velocity
		if (horizontalCollision && isClimbing()) motion = Vec3d(motion.x, Vanilla.WATER_CLIMB_SPEED, motion.z)
		motion = motion.multiply(drag.toDouble(), Vanilla.WATER_VERTICAL_DRAG.toDouble(), drag.toDouble())
		velocity = if (gravity != 0.0 && !isSprinting) {
			val sinking = falling && abs(motion.y - Vanilla.WATER_SINK_SNAP) >= Vanilla.WATER_SINK_SNAP_WINDOW &&
					abs(motion.y - gravity / Vanilla.WATER_GRAVITY_DIVISOR) < Vanilla.WATER_SINK_SNAP_WINDOW
			Vec3d(motion.x, if (sinking) Vanilla.WATER_SINK_SNAP_SPEED else motion.y - gravity / Vanilla.WATER_GRAVITY_DIVISOR, motion.z)
		} else motion

		if (horizontalCollision) {
			val raised = boundingBox.offset(velocity.x, velocity.y + Vanilla.WATER_EXIT_CLEARANCE - position.y + startY, velocity.z)
			if (environment.isSpaceEmpty(raised) && !environment.containsFluid(raised)) {
				velocity = Vec3d(velocity.x, Vanilla.WATER_EXIT_SPEED.toDouble(), velocity.z)
			}
		}
	}

	private fun travelMidAir(travelVec: Vec3d) {
		val gravity = effectiveGravity()

		val slipperiness = environment.slipperiness(velocityAffectingPos).toFloat()
		val friction = ((if (onGround) slipperiness else 1.0F) * Vanilla.HORIZONTAL_DRAG).toDouble()

		applyMovementInput(travelVec, slipperiness)
		if (isClimbing()) velocity = applyClimbingSpeed(velocity)

		move(travelVec)

		if (isClimbing() && (horizontalCollision || isJumping)) {
			velocity = Vec3d(velocity.x, Vanilla.CLIMB_RISE_SPEED, velocity.z)
		}

		velocity += DOWN * gravity
		velocity *= Vec3d(friction, Vanilla.VERTICAL_DRAG.toDouble(), friction)
	}

	private fun isClimbing(): Boolean = environment.isClimbable(position.flooredBlockPos)

	private fun applyClimbingSpeed(motion: Vec3d): Vec3d {
		val cap = Vanilla.CLIMB_SPEED_CAP.toDouble()
		val holding = isSneaking && motion.y < 0.0
		return Vec3d(
			motion.x.coerceIn(-cap, cap),
			if (holding) 0.0 else maxOf(motion.y, -cap),
			motion.z.coerceIn(-cap, cap),
		)
	}

	private fun applyMovementInput(travelVec: Vec3d, slipperiness: Float) {
		val speed = when {
			onGround -> movementSpeed() *
					(Vanilla.GROUND_ACCELERATION_SCALE / (slipperiness * slipperiness * slipperiness))
			isSprinting -> Vanilla.SPRINT_AIR_ACCELERATION
			else -> Vanilla.AIR_ACCELERATION
		}
		velocity += movementInputToVelocity(travelVec, speed, rotation.yawF)
	}

	private fun movementSpeed(): Float =
		(if (isSprinting) profile.movementSpeed * Vanilla.SPRINT_SPEED_MULTIPLIER else profile.movementSpeed).toFloat()

	private fun move(movementInput: Vec3d) {
		val requested = adjustMovementForSneaking(velocity)
		val movement = adjustMovementForCollisions(requested)

		if (movement.lengthSquared() > Vanilla.MIN_MOVEMENT_SQUARED) {
			position += movement
		}
		lastMovementY = movement.y

		val xCollide = !MathHelper.approximatelyEquals(movement.x, requested.x)
		val yCollide = !MathHelper.approximatelyEquals(movement.y, requested.y)
		val zCollide = !MathHelper.approximatelyEquals(movement.z, requested.z)

		horizontalCollision = xCollide || zCollide
		collidedSoftly = horizontalCollision && hasCollidedSoftly(movementInput, movement)
		verticalCollision = yCollide
		onGround = yCollide && requested.y < 0.0

		boundingBox = normalizedBoundingBox().offset(position)
		updateSupportingBlockPos(movement)
		if (!touchingWater) updateWaterState()

		if (horizontalCollision) {
			velocity = Vec3d(
				if (xCollide) 0.0 else velocity.x,
				velocity.y,
				if (zCollide) 0.0 else velocity.z
			)
		}

		if (verticalCollision) onEntityLand()

		velocityAffectingPos = posWithYOffset(Vanilla.VELOCITY_AFFECTING_POS_OFFSET.toDouble())
		val velocityMultiplier = blockMultiplier(environment::velocityMultiplier)
		velocity *= Vec3d(velocityMultiplier, 1.0, velocityMultiplier)
	}

	private fun onEntityLand() {
		val bounce = if (isSneaking) 0.0 else environment.bounceFactor(posWithYOffset(Vanilla.LANDING_POS_OFFSET.toDouble()))
		velocity = if (bounce > 0.0 && velocity.y < 0.0) {
			Vec3d(velocity.x, -velocity.y * bounce, velocity.z)
		} else {
			Vec3d(velocity.x, 0.0, velocity.z)
		}
	}

	private fun tickBlockCollision() {
		dampenSlimeStep()
		applyBubbleColumns()
	}

	private fun dampenSlimeStep() {
		if (!onGround) return
		val vertical = abs(velocity.y)
		if (vertical >= Vanilla.SLIME_STEP_MAX_VERTICAL_SPEED) return
		if (!environment.dampensSteppingSpeed(posWithYOffset(Vanilla.LANDING_POS_OFFSET.toDouble()))) return

		val drag = Vanilla.SLIME_STEP_DRAG + vertical * Vanilla.SLIME_STEP_DRAG_PER_VERTICAL_SPEED
		velocity = Vec3d(velocity.x * drag, velocity.y, velocity.z * drag)
	}

	private fun applyBubbleColumns() {
		val box = boundingBox.contract(Vanilla.BUBBLE_CONTRACTION.toDouble())
		val x0 = MathHelper.floor(box.minX)
		val y0 = MathHelper.floor(box.minY)
		val z0 = MathHelper.floor(box.minZ)
		val x1 = MathHelper.floor(box.maxX)
		val y1 = MathHelper.floor(box.maxY)
		val z1 = MathHelper.floor(box.maxZ)
		if (!environment.mayContainBubbles(x0, y0, z0, x1, y1, z1)) return
		val ys = if (lastMovementY >= 0.0) y0..y1 else y1 downTo y0
		for (y in ys) for (x in x0..x1) for (z in z0..z1) {
			val bubbles = environment.water(cell.set(x, y, z))?.bubbles ?: continue
			val surface = environment.water(cell.set(x, y + 1, z)) == null &&
				environment.isSpaceEmpty(Box(cell).contract(Vanilla.POSE_CLEARANCE))
			val vy = velocity.y
			val pushed = when {
				bubbles == BubbleColumn.LIFT && surface -> minOf(Vanilla.BUBBLE_SURFACE_LIFT_MAX, vy + Vanilla.BUBBLE_SURFACE_LIFT)
				bubbles == BubbleColumn.LIFT -> minOf(Vanilla.BUBBLE_LIFT_MAX, vy + Vanilla.BUBBLE_LIFT)
				surface -> maxOf(-Vanilla.BUBBLE_SURFACE_DRAG_MAX, vy - Vanilla.BUBBLE_DRAG)
				else -> maxOf(-Vanilla.BUBBLE_DRAG_MAX, vy - Vanilla.BUBBLE_DRAG)
			}
			velocity = Vec3d(velocity.x, pushed, velocity.z)
		}
	}

	private fun eyeInBubbles(): Boolean =
		environment.water(cell.set(position.x, position.y + pose.eyeHeight(), position.z))?.bubbles != null

	private fun hasCollidedSoftly(input: Vec3d, adjustedMovement: Vec3d): Boolean {
		val yawRadians = rotation.yawF * MathHelper.RADIANS_PER_DEGREE
		val sin = MathHelper.sin(yawRadians.toDouble()).toDouble()
		val cos = MathHelper.cos(yawRadians.toDouble()).toDouble()
		val intendedX = input.x * cos - input.z * sin
		val intendedZ = input.z * cos + input.x * sin
		val intendedSquared = intendedX * intendedX + intendedZ * intendedZ
		val adjustedSquared = adjustedMovement.x * adjustedMovement.x + adjustedMovement.z * adjustedMovement.z
		if (intendedSquared < Vanilla.SOFT_COLLISION_MIN_SQUARED || adjustedSquared < Vanilla.SOFT_COLLISION_MIN_SQUARED) {
			return false
		}

		val dot = intendedX * adjustedMovement.x + intendedZ * adjustedMovement.z
		val cosine = dot / sqrt(intendedSquared * adjustedSquared)
		return acos(cosine) < Vanilla.SOFT_COLLISION_MAX_ANGLE
	}

	private fun updateSupportingBlockPos(movement: Vec3d) {
		if (!onGround) {
			forceUpdateSupportingBlockPos = false
			supportingBlockPos = null
			return
		}

		val probe = Box(
			boundingBox.minX, boundingBox.minY - Vanilla.SUPPORTING_BLOCK_PROBE_DEPTH, boundingBox.minZ,
			boundingBox.maxX, boundingBox.minY, boundingBox.maxZ,
		)
		var found = environment.findSupportingBlockPos(probe, position)
		if (found != null || forceUpdateSupportingBlockPos) {
			supportingBlockPos = found
		} else {
			val rewound = probe.offset(-movement.x, 0.0, -movement.z)
			found = environment.findSupportingBlockPos(rewound, position)
			supportingBlockPos = found
		}
		forceUpdateSupportingBlockPos = found == null
	}

	private inline fun blockMultiplier(read: (BlockPos) -> Double): Double {
		val underfoot = read(position.flooredBlockPos)
		val affecting = read(velocityAffectingPos)
		return if (underfoot == 1.0) affecting else underfoot
	}

	private fun posWithYOffset(offset: Double): BlockPos {
		val supporting = supportingBlockPos
			?: return (position + DOWN * offset).flooredBlockPos

		if (offset <= Vanilla.MIN_POS_OFFSET) return supporting
		if (environment.isFenceLike(supporting)) return supporting
		return supporting.withY(MathHelper.floor(position.y - offset))
	}

	private fun jump() {
		val multiplier = blockMultiplier(environment::jumpVelocityMultiplier).toFloat()
		val jumpVelocity = profile.jumpStrength.toFloat() * multiplier +
				profile.jumpBoostVelocityModifier.toFloat()
		if (jumpVelocity <= Vanilla.MIN_JUMP_VELOCITY) return

		velocity = Vec3d(velocity.x, maxOf(jumpVelocity.toDouble(), velocity.y), velocity.z)

		if (isSprinting) {
			val yawRad = rotation.yawF * MathHelper.RADIANS_PER_DEGREE
			velocity += Vec3d(
				-MathHelper.sin(yawRad.toDouble()).toDouble() * Vanilla.SPRINT_JUMP_BOOST,
				0.0,
				MathHelper.cos(yawRad.toDouble()).toDouble() * Vanilla.SPRINT_JUMP_BOOST,
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

		var dx = movement.x
		var dz = movement.z

		while (dx != 0.0 && isSpaceUnderFeetEmpty(dx, 0.0)) dx = shrinkTowardsZero(dx)
		while (dz != 0.0 && isSpaceUnderFeetEmpty(0.0, dz)) dz = shrinkTowardsZero(dz)
		while (dx != 0.0 && dz != 0.0 && isSpaceUnderFeetEmpty(dx, dz)) {
			dx = shrinkTowardsZero(dx)
			dz = shrinkTowardsZero(dz)
		}

		return if (dx == movement.x && dz == movement.z) movement else Vec3d(dx, movement.y, dz)
	}

	private fun isSpaceUnderFeetEmpty(offsetX: Double, offsetZ: Double): Boolean =
		environment.isSpaceEmpty(
			Box(
				boundingBox.minX + Vanilla.COLLISION_EPSILON + offsetX,
				boundingBox.minY - profile.stepHeight - Vanilla.COLLISION_EPSILON,
				boundingBox.minZ + Vanilla.COLLISION_EPSILON + offsetZ,
				boundingBox.maxX - Vanilla.COLLISION_EPSILON + offsetX,
				boundingBox.minY,
				boundingBox.maxZ - Vanilla.COLLISION_EPSILON + offsetZ,
			)
		)

	private fun shrinkTowardsZero(delta: Double): Double =
		if (abs(delta) <= Vanilla.LEDGE_CLIP_STEP) 0.0
		else delta - sign(delta) * Vanilla.LEDGE_CLIP_STEP

	private fun normalizedBoundingBox(): Box =
		boundingBox.offset(-boundingBox.minX, -boundingBox.minY, -boundingBox.minZ)
			.offset(-boundingBox.lengthX * 0.5, 0.0, -boundingBox.lengthZ * 0.5)

	private fun movementInputToVelocity(input: Vec3d, speed: Float, yaw: Float): Vec3d {
		val lengthSquared = input.lengthSquared()
		if (lengthSquared < Vanilla.MIN_INPUT_LENGTH_SQUARED) return Vec3d.ZERO

		val scaled = (if (lengthSquared > 1.0) input.normalize() else input).multiply(speed.toDouble())
		val radians = yaw * MathHelper.RADIANS_PER_DEGREE
		val sin = MathHelper.sin(radians.toDouble())
		val cos = MathHelper.cos(radians.toDouble())
		return Vec3d(
			scaled.x * cos - scaled.z * sin,
			scaled.y,
			scaled.z * cos + scaled.x * sin,
		)
	}
}
