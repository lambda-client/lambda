package com.lambda.pathing.launch

import com.lambda.pathing.physics.Vanilla
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToLong

internal object FallReach {
	private val profile = BallisticProfile.VANILLA

	class Flight(
		val airTicks: Int,
		val impactSpeed: Double,
		private val base: Double,
		private val slope: Double,
		val launchOffset: Double,
	) {
		fun reach(entrySpeed: Double): Double = launchOffset + base + slope * entrySpeed
	}

	class Plunge(val depth: Double, val ticks: Int)

	private val flights = ConcurrentHashMap<Long, Flight>()

	private val plunges = ConcurrentHashMap<Long, Plunge>()

	private val missing = Flight(0, 0.0, Double.NaN, 0.0, 0.0)

	fun flight(jumps: Boolean, drop: Double): Flight? {
		val key = ((drop * KEY_STEPS).roundToLong() shl 1) or (if (jumps) 1L else 0L)
		val flight = flights[key] ?: (compute(jumps, drop) ?: missing).also {
			if (flights.size > CACHE_LIMIT) flights.clear()
			flights[key] = it
		}
		return flight.takeUnless { it === missing }
	}

	private fun compute(jumps: Boolean, drop: Double): Flight? {
		val mode = if (jumps) LaunchMode.SPRINT_JUMP else LaunchMode.SPRINT_DROP
		val rise = -drop
		val still = profile.fly(mode, 0.0, rise, holdForward = true, maxTicks = MAX_FALL_TICKS) ?: return null
		val moving = profile.fly(mode, 1.0, rise, holdForward = true, maxTicks = MAX_FALL_TICKS) ?: return null
		return Flight(still.airTicks, impactSpeed(jumps, rise), still.distance, moving.distance - still.distance, LAUNCH_OFFSET)
	}

	private fun impactSpeed(jumps: Boolean, rise: Double): Double {
		var height = 0.0
		var velocity = if (jumps) profile.jumpVelocity else 0.0
		height += velocity
		velocity = (velocity - profile.gravity) * Vanilla.VERTICAL_DRAG
		repeat(MAX_FALL_TICKS) {
			if (velocity < 0.0 && height + velocity <= rise) return -velocity
			height += velocity
			velocity = (velocity - profile.gravity) * Vanilla.VERTICAL_DRAG
		}
		return -velocity
	}

	fun plunge(impactSpeed: Double, waterCells: Int): Plunge {
		val key = ((impactSpeed * KEY_STEPS).roundToLong() shl 8) or waterCells.toLong().coerceIn(0, 255)
		return plunges[key] ?: computePlunge(impactSpeed, waterCells).also {
			if (plunges.size > CACHE_LIMIT) plunges.clear()
			plunges[key] = it
		}
	}

	private fun computePlunge(impactSpeed: Double, waterCells: Int): Plunge {
		val floor = -(waterCells - WATER_SURFACE_GAP)
		var height = 0.0
		var velocity = -impactSpeed
		var deepest = 0.0
		for (tick in 1..MAX_PLUNGE_TICKS) {
			velocity += Vanilla.SWIM_UP_SPEED
			height += velocity
			if (height <= floor) {
				height = floor
				velocity = 0.0
			}
			deepest = minOf(deepest, height)
			velocity = velocity * WATER_DRAG - WATER_GRAVITY
			if (height >= 0.0 && velocity >= 0.0) return Plunge(-deepest, tick)
		}
		return Plunge(-deepest, MAX_PLUNGE_TICKS)
	}

	const val MAX_FALL_TICKS = 1200

	private const val MAX_PLUNGE_TICKS = 200

	private const val LAUNCH_OFFSET = 0.5 + BODY_HALF_WIDTH

	private const val WATER_SURFACE_GAP = 1.0 / 9.0

	private const val WATER_DRAG = 0.8

	private const val WATER_GRAVITY = Vanilla.GRAVITY / 16.0

	private const val KEY_STEPS = 64.0

	private const val CACHE_LIMIT = 50_000
}
