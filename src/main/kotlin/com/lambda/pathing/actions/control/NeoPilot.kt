package com.lambda.pathing.actions.control

import com.lambda.interaction.managers.rotating.MouseHand
import com.lambda.pathing.actions.NeoTerrain
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.physics.Vanilla
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal class NeoPilot(
	private val terrain: NeoTerrain,
	private val landingForward: Int,
	private val landingSide: Int,
	private val landingUp: Int,
	private val groundHeading: Double,
	private val preferredMargin: Double,
	private val maxTurn: Double,
	private val turnAcceleration: Double = Double.POSITIVE_INFINITY,
	private val turnStep: Double = 0.0,
) {
	class Command(val heading: Double, val jump: Boolean)

	private class Body {
		var forward = 0.0
		var side = 0.0
		var up = 0.0
		var forwardSpeed = 0.0
		var sideSpeed = 0.0
		var upSpeed = 0.0
		var heading = 0.0
		var turnRate = 0.0
		var grounded = false
		var sprinting = true

		fun copyFrom(other: Body) {
			forward = other.forward
			side = other.side
			up = other.up
			forwardSpeed = other.forwardSpeed
			sideSpeed = other.sideSpeed
			upSpeed = other.upSpeed
			heading = other.heading
			turnRate = other.turnRate
			grounded = other.grounded
			sprinting = other.sprinting
		}
	}

	private val bearing = Math.toDegrees(atan2(landingSide.toDouble(), landingForward.toDouble()))

	private val headings = DoubleArray(HEADING_COUNT) { bearing + (it - HEADING_COUNT / 2) * HEADING_STEP_DEGREES }

	private val alongs = DoubleArray(HEADING_COUNT) { cos(Math.toRadians(headings[it])) }

	private val acrosses = DoubleArray(HEADING_COUNT) { sin(Math.toRadians(headings[it])) }

	private val start = Body()
	private val trial = Body()
	private val lowest = min(0, landingUp) - FALL_LIMIT_BLOCKS

	fun command(
		forward: Double, side: Double, up: Double,
		forwardSpeed: Double, sideSpeed: Double, upSpeed: Double,
		heading: Double, turnRate: Double, grounded: Boolean, sprinting: Boolean, launched: Boolean,
	): Command {
		start.forward = forward
		start.side = side
		start.up = up
		start.forwardSpeed = forwardSpeed
		start.sideSpeed = sideSpeed
		start.upSpeed = upSpeed
		start.heading = heading
		start.turnRate = turnRate
		start.grounded = grounded
		start.sprinting = sprinting
		if (launched && grounded) return Command(towardLanding(start), jump = false)
		if (launched || !grounded) return Command(bestFlight(launched, preferredMargin) ?: bestFlight(launched, 0.0) ?: heading, jump = false)
		bestLaunch(preferredMargin)?.let { return Command(it, jump = true) }
		if (!holdsGround()) bestLaunch(0.0)?.let { return Command(it, jump = true) }
		return Command(groundHeading, jump = false)
	}

	private fun bestLaunch(margin: Double): Double? {
		var best = Int.MAX_VALUE
		var chosen = -1
		for (jump in 0 until HEADING_COUNT) for (air in 0 until HEADING_COUNT) {
			val ticks = complete(jump, air, launch = true, launched = false, margin = margin, bound = best - 1)
			if (ticks in 0 until best) {
				best = ticks
				chosen = jump
			}
		}
		return if (chosen < 0) null else headings[chosen]
	}

	private fun bestFlight(launched: Boolean, margin: Double): Double? {
		var best = Int.MAX_VALUE
		var chosen = -1
		for (air in 0 until HEADING_COUNT) {
			val ticks = complete(air, air, launch = false, launched = launched, margin = margin, bound = best - 1)
			if (ticks in 0 until best) {
				best = ticks
				chosen = air
			}
		}
		return if (chosen < 0) null else headings[chosen]
	}

	private fun holdsGround(): Boolean {
		trial.copyFrom(start)
		return supported(trial.forward, trial.side, floor(trial.up + EPSILON).toInt(), 0.0) && step(trial, groundHeading, Double.NaN, Double.NaN, jump = false, margin = 0.0)
	}

	private fun complete(jumpHeading: Int, airHeading: Int, launch: Boolean, launched: Boolean, margin: Double, bound: Int): Int {
		val body = trial
		body.copyFrom(start)
		var airborne = launched
		for (tick in 1..min(MAX_TICKS, bound)) {
			val jump = launch && tick == 1
			val wasGrounded = body.grounded
			val stepped = when {
				jump -> step(body, headings[jumpHeading], alongs[jumpHeading], acrosses[jumpHeading], jump = true, margin = margin)
				airborne && body.grounded -> step(body, towardLanding(body), Double.NaN, Double.NaN, jump = false, margin = margin)
				else -> step(body, headings[airHeading], alongs[airHeading], acrosses[airHeading], jump = false, margin = margin)
			}
			if (!stepped) return -1
			if (jump) airborne = true
			if (airborne && body.grounded) {
				if (arrived(body)) return tick
				if (!wasGrounded && !onLanding(body, margin)) return -1
			}
			if (body.up < lowest) return -1
		}
		return -1
	}

	private fun onLanding(body: Body, margin: Double): Boolean =
		abs(body.up - landingUp) < EPSILON && overlaps(body.forward, landingForward, margin) && overlaps(body.side, landingSide, margin)

	private fun arrived(body: Body): Boolean =
		floor(body.forward).toInt() == landingForward && floor(body.side).toInt() == landingSide && abs(body.up - landingUp) < EPSILON

	private fun towardLanding(body: Body): Double =
		Math.toDegrees(atan2(landingSide + 0.5 - body.side, landingForward + 0.5 - body.forward))

	private fun step(body: Body, target: Double, targetAlong: Double, targetAcross: Double, jump: Boolean, margin: Double): Boolean {
		val heading = turned(body, target)
		val along: Double
		val across: Double
		if (heading == target && !targetAlong.isNaN()) {
			along = targetAlong
			across = targetAcross
		} else {
			val radians = Math.toRadians(heading)
			along = cos(radians)
			across = sin(radians)
		}
		body.heading = heading
		val grounded = body.grounded
		val sprinting = body.sprinting
		if (jump) {
			if (!grounded) return false
			body.upSpeed = max(JUMP_VELOCITY, body.upSpeed)
			if (sprinting) {
				body.forwardSpeed += SPRINT_JUMP_BOOST * along
				body.sideSpeed += SPRINT_JUMP_BOOST * across
			}
		}
		val acceleration = when {
			grounded -> if (sprinting) SPRINT_GROUND_ACCELERATION else WALK_GROUND_ACCELERATION
			else -> if (sprinting) SPRINT_AIR_ACCELERATION else WALK_AIR_ACCELERATION
		}
		body.forwardSpeed += acceleration * along
		body.sideSpeed += acceleration * across
		val rise = body.upSpeed
		var landed = false
		if (rise < 0.0) {
			val top = landingTop(body, rise)
			if (top != NO_TOP) {
				if (!supported(body.forward, body.side, top, margin)) return false
				body.up = top.toDouble()
				landed = true
			} else {
				body.up += rise
			}
		} else if (rise > 0.0) {
			if (ceilingWithin(body, rise)) return false
			body.up += rise
		}
		val forwardMove = body.forwardSpeed
		val sideMove = body.sideSpeed
		val movedForward: Double
		val movedSide: Double
		if (abs(forwardMove) >= abs(sideMove)) {
			movedForward = clipForward(body, forwardMove)
			if (movedForward.isNaN()) return false
			body.forward += movedForward
			movedSide = clipSide(body, sideMove)
			if (movedSide.isNaN()) return false
			body.side += movedSide
		} else {
			movedSide = clipSide(body, sideMove)
			if (movedSide.isNaN()) return false
			body.side += movedSide
			movedForward = clipForward(body, forwardMove)
			if (movedForward.isNaN()) return false
			body.forward += movedForward
		}
		val hitForward = abs(movedForward - forwardMove) >= COLLISION_TOLERANCE
		val hitSide = abs(movedSide - sideMove) >= COLLISION_TOLERANCE
		if (hitForward) body.forwardSpeed = 0.0
		if (hitSide) body.sideSpeed = 0.0
		body.sprinting = !(hitForward || hitSide) || soft(along, across, movedForward, movedSide)
		val drag = if (grounded) GROUND_FRICTION else AIR_DRAG
		body.forwardSpeed *= drag
		body.sideSpeed *= drag
		body.upSpeed = ((if (landed) 0.0 else rise) - GRAVITY) * VERTICAL_DRAG
		body.grounded = landed
		return true
	}

	private fun soft(along: Double, across: Double, movedForward: Double, movedSide: Double): Boolean {
		val moved = movedForward * movedForward + movedSide * movedSide
		if (moved < SOFT_COLLISION_MIN_SQUARED) return false
		val cosine = (along * movedForward + across * movedSide) / sqrt(moved)
		return acos(cosine.coerceIn(-1.0, 1.0)) < SOFT_COLLISION_MAX_ANGLE
	}

	private fun turned(body: Body, target: Double): Double {
		var delta = (target - body.heading) % 360.0
		if (delta > 180.0) delta -= 360.0
		if (delta <= -180.0) delta += 360.0
		if (turnAcceleration.isInfinite()) return body.heading + delta.coerceIn(-maxTurn, maxTurn)
		val rate = MouseHand.velocity(delta, 0.0, body.turnRate, 0.0, maxTurn, turnAcceleration).yaw
		body.turnRate = if (turnStep > 0.0) (rate / turnStep).roundToInt() * turnStep else rate
		return body.heading + body.turnRate
	}

	private fun landingTop(body: Body, rise: Double): Int {
		val low = body.up + rise
		var level = floor(body.up + EPSILON).toInt()
		while (level >= low - EPSILON) {
			if (occupied(body.forward, body.side, level - 1)) return level
			level--
		}
		return NO_TOP
	}

	private fun occupied(forward: Double, side: Double, up: Int): Boolean {
		for (f in cell(forward - HALF_WIDTH + EPSILON)..cell(forward + HALF_WIDTH - EPSILON)) {
			for (s in cell(side - HALF_WIDTH + EPSILON)..cell(side + HALF_WIDTH - EPSILON)) {
				if (terrain.at(f, s, up) != NeoTerrain.CLEAR) return true
			}
		}
		return false
	}

	private fun supported(forward: Double, side: Double, top: Int, margin: Double): Boolean {
		var floored = false
		for (f in cell(forward - HALF_WIDTH + EPSILON)..cell(forward + HALF_WIDTH - EPSILON)) {
			for (s in cell(side - HALF_WIDTH + EPSILON)..cell(side + HALF_WIDTH - EPSILON)) {
				when (terrain.at(f, s, top - 1)) {
					NeoTerrain.BLOCK -> return false
					NeoTerrain.FLOOR -> if (overlaps(forward, f, margin) && overlaps(side, s, margin)) floored = true
				}
			}
		}
		return floored
	}

	private fun overlaps(centre: Double, cell: Int, margin: Double): Boolean {
		val reach = HALF_WIDTH - max(margin, EPSILON)
		return centre + reach > cell && centre - reach < cell + 1
	}

	private fun ceilingWithin(body: Body, rise: Double): Boolean {
		val fromUp = cell(body.up + HEIGHT - EPSILON)
		val toUp = cell(body.up + rise + HEIGHT - EPSILON)
		if (toUp == fromUp) return false
		for (s in cell(body.side - HALF_WIDTH + EPSILON)..cell(body.side + HALF_WIDTH - EPSILON)) {
			for (f in cell(body.forward - HALF_WIDTH + EPSILON)..cell(body.forward + HALF_WIDTH - EPSILON)) {
				if (terrain.occupiedLevels(f, s, fromUp + 1, toUp)) return true
			}
		}
		return false
	}

	private fun clipForward(body: Body, delta: Double): Double {
		if (delta == 0.0) return 0.0
		val minSide = cell(body.side - HALF_WIDTH + EPSILON)
		val maxSide = cell(body.side + HALF_WIDTH - EPSILON)
		val minUp = cell(body.up + EPSILON)
		val maxUp = cell(body.up + HEIGHT - EPSILON)
		if (delta > 0.0) {
			val face = body.forward + HALF_WIDTH
			for (f in ceil(face - EPSILON).toInt() until ceil(face + delta).toInt()) {
				for (s in minSide..maxSide) if (terrain.occupiedLevels(f, s, minUp, maxUp)) {
					return if (outside(f, s, minUp, maxUp)) Double.NaN else f - face
				}
			}
		} else {
			val face = body.forward - HALF_WIDTH
			for (f in floor(face + EPSILON).toInt() - 1 downTo floor(face + delta).toInt()) {
				for (s in minSide..maxSide) if (terrain.occupiedLevels(f, s, minUp, maxUp)) {
					return if (outside(f, s, minUp, maxUp)) Double.NaN else f + 1 - face
				}
			}
		}
		return delta
	}

	private fun clipSide(body: Body, delta: Double): Double {
		if (delta == 0.0) return 0.0
		val minForward = cell(body.forward - HALF_WIDTH + EPSILON)
		val maxForward = cell(body.forward + HALF_WIDTH - EPSILON)
		val minUp = cell(body.up + EPSILON)
		val maxUp = cell(body.up + HEIGHT - EPSILON)
		if (delta > 0.0) {
			val face = body.side + HALF_WIDTH
			for (s in ceil(face - EPSILON).toInt() until ceil(face + delta).toInt()) {
				for (f in minForward..maxForward) if (terrain.occupiedLevels(f, s, minUp, maxUp)) {
					return if (outside(f, s, minUp, maxUp)) Double.NaN else s - face
				}
			}
		} else {
			val face = body.side - HALF_WIDTH
			for (s in floor(face + EPSILON).toInt() - 1 downTo floor(face + delta).toInt()) {
				for (f in minForward..maxForward) if (terrain.occupiedLevels(f, s, minUp, maxUp)) {
					return if (outside(f, s, minUp, maxUp)) Double.NaN else s + 1 - face
				}
			}
		}
		return delta
	}

	private fun outside(forward: Int, side: Int, fromUp: Int, toUp: Int): Boolean =
		forward < terrain.minForward || forward >= terrain.minForward + terrain.forwardSize ||
				side < terrain.minSide || side >= terrain.minSide + terrain.sideSize ||
				fromUp < terrain.minUp || toUp >= terrain.minUp + terrain.upSize

	private fun cell(value: Double): Int = floor(value).toInt()

	companion object {
		private val SPRINT_GROUND_ACCELERATION = BallisticProfile.VANILLA.sprintGroundAcceleration

		private val WALK_GROUND_ACCELERATION = BallisticProfile.VANILLA.walkGroundAcceleration

		private val GROUND_FRICTION = BallisticProfile.VANILLA.groundFriction

		private const val SPRINT_AIR_ACCELERATION = BallisticProfile.SPRINT_AIR_ACCELERATION

		private const val WALK_AIR_ACCELERATION = BallisticProfile.WALK_AIR_ACCELERATION

		private const val AIR_DRAG = BallisticProfile.HORIZONTAL_DRAG

		private val GRAVITY = BallisticProfile.VANILLA.gravity

		private val JUMP_VELOCITY = BallisticProfile.VANILLA.jumpVelocity

		private const val SPRINT_JUMP_BOOST = Vanilla.SPRINT_JUMP_BOOST

		private const val VERTICAL_DRAG = Vanilla.VERTICAL_DRAG.toDouble()

		private const val HALF_WIDTH = Vanilla.PLAYER_HALF_WIDTH.toDouble()

		private const val HEIGHT = Vanilla.PLAYER_HEIGHT.toDouble()

		private const val EPSILON = Vanilla.COLLISION_EPSILON

		private const val COLLISION_TOLERANCE = 1.0E-5

		private const val SOFT_COLLISION_MIN_SQUARED = Vanilla.SOFT_COLLISION_MIN_SQUARED.toDouble()

		private const val SOFT_COLLISION_MAX_ANGLE = Vanilla.SOFT_COLLISION_MAX_ANGLE.toDouble()

		private const val NO_TOP = Int.MIN_VALUE

		private const val FALL_LIMIT_BLOCKS = 1.5

		private const val MAX_TICKS = 40

		private const val HEADING_COUNT = 19

		private const val HEADING_STEP_DEGREES = 10.0
	}
}
