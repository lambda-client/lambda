package com.lambda.pathing.search

import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.core.MovementKeys
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.random.Random

internal class PlanImprover(
	private val refiner: PlanRefiner,
	initial: PlanRefiner.Plan,
	private val random: Random,
	private val levels: List<PlanRefiner.Level> = PlanRefiner.LEVELS,
) {
	var best: PlanRefiner.Plan = initial
		private set

	var iterations = 0
		private set

	private var current = initial
	var temperature = START_TEMPERATURE
		private set
	private var sinceBest = 0
	private val tries = HashMap<JunctionKey, Int>()

	private data class JunctionKey(val decision: TrajectoryDecision, val x: Long, val z: Long)

	fun reset(plan: PlanRefiner.Plan) {
		best = plan
		current = plan
		sinceBest = 0
	}

	fun step(minFrame: Int, annealing: Boolean): PlanRefiner.Plan? {
		iterations++
		if (!annealing && current !== best) current = best
		val junctions = current.segments.indices.filter { current.segments[it] is PlanSegment.Move && current.segments[it].startFrame >= minFrame }
		if (junctions.isEmpty()) return null
		val index = pick(junctions)
		val junction = current.segments[index] as PlanSegment.Move
		tries.merge(keyOf(junction), 1, Int::plus)
		val exploration = Exploration(
			seed = random.nextLong(),
			corridorSteps = MIN_CORRIDOR_STEPS + random.nextInt(CORRIDOR_STEP_SPREAD),
			corridorMarginTicks = MIN_CORRIDOR_MARGIN_TICKS + random.nextDouble() * CORRIDOR_MARGIN_SPREAD_TICKS,
			launchSteps = MIN_LAUNCH_STEPS + random.nextInt(LAUNCH_STEP_SPREAD),
			jitterTicks = random.nextDouble() * MAX_JITTER_TICKS,
			taboo = junction.decision.takeIf { random.nextDouble() < TABOO_CHANCE },
			seeds = headings(current, index),
			followDepth = FOLLOW_DEPTH,
		)
		val level = levels[random.nextInt(levels.size)]
		val allowance = if (annealing) ceil(temperature).toInt() else 0
		val candidate = refiner.refineAt(current, index, level, exploration, allowance)
		temperature = (temperature * COOLING).coerceAtLeast(MIN_TEMPERATURE)
		sinceBest++
		if (candidate != null) {
			val delta = candidate.frames - current.frames
			if (delta < 0 || random.nextDouble() < exp(-delta / temperature)) current = candidate
			if (candidate.frames < best.frames) {
				best = candidate
				current = candidate
				sinceBest = 0
				return candidate
			}
		}
		if (sinceBest >= REHEAT_ITERATIONS) {
			current = best
			temperature = START_TEMPERATURE
			sinceBest = 0
		}
		return null
	}

	private fun headings(plan: PlanRefiner.Plan, index: Int): List<TrajectoryDecision> {
		val junction = plan.segments[index] as PlanSegment.Move
		val entry = junction.entry
		if (!entry.onGround || entry.touchingWater) return emptyList()
		val horizon = junction.startFrame + HEADING_LOOKAHEAD_FRAMES
		val ahead = plan.segments.subList(index, plan.segments.size).firstOrNull { it.endFrame >= horizon } ?: plan.segments.last()
		val dx = ahead.exit.position.x - entry.position.x
		val dz = ahead.exit.position.z - entry.position.z
		if (hypot(dx, dz) < MIN_HEADING_REACH) return emptyList()
		val bearing = Math.toDegrees(atan2(dz, dx)) - 90.0
		val seeds = ArrayList<TrajectoryDecision>()
		var offset = -HEADING_SPREAD_DEGREES + random.nextDouble() * HEADING_YAW_STEP_DEGREES
		while (offset <= HEADING_SPREAD_DEGREES) {
			for (delay in 0..MAX_HEADING_DELAY) for (sprint in refiner.sprintModes) for (air in AIRBORNE_KEYS) {
				seeds += TrajectoryDecision.Heading(sprint, junction.decision.step, bearing + offset, delay, airborneKeys = air)
			}
			offset += HEADING_YAW_STEP_DEGREES
		}
		return seeds
	}

	private fun keyOf(junction: PlanSegment.Move) = JunctionKey(
		junction.decision,
		Math.round(junction.entry.position.x * KEY_RESOLUTION),
		Math.round(junction.entry.position.z * KEY_RESOLUTION),
	)

	private fun pick(junctions: List<Int>): Int {
		val weights = DoubleArray(junctions.size) { slot ->
			var waste = 0.0
			val first = junctions[slot]
			for (index in first until minOf(current.segments.size, first + WASTE_WINDOW_SEGMENTS)) waste += waste(current.segments[index])
			(1.0 + waste) / (1 + (tries[keyOf(current.segments[first] as PlanSegment.Move)] ?: 0))
		}
		var roll = random.nextDouble() * weights.sum()
		for (slot in junctions.indices) {
			roll -= weights[slot]
			if (roll <= 0.0) return junctions[slot]
		}
		return junctions.last()
	}

	private fun waste(segment: PlanSegment): Double {
		val frames = segment.endFrame - segment.startFrame
		val travelled = hypot(segment.exit.position.x - segment.entry.position.x, segment.exit.position.z - segment.entry.position.z)
		return (frames - travelled / CRUISE_BLOCKS_PER_TICK).coerceAtLeast(0.0)
	}

	private companion object {
		const val START_TEMPERATURE = 3.0
		const val MIN_TEMPERATURE = 0.3
		const val COOLING = 0.995
		const val REHEAT_ITERATIONS = 200
		const val TABOO_CHANCE = 0.5
		const val MIN_CORRIDOR_STEPS = 3
		const val CORRIDOR_STEP_SPREAD = 4
		const val MIN_CORRIDOR_MARGIN_TICKS = 4.0
		const val CORRIDOR_MARGIN_SPREAD_TICKS = 12.0
		const val MIN_LAUNCH_STEPS = 2
		const val LAUNCH_STEP_SPREAD = 3
		const val MAX_JITTER_TICKS = 4.0
		const val WASTE_WINDOW_SEGMENTS = 3
		const val CRUISE_BLOCKS_PER_TICK = 0.33
		const val FOLLOW_DEPTH = 2
		const val HEADING_LOOKAHEAD_FRAMES = 20
		const val MIN_HEADING_REACH = 1.0
		const val HEADING_SPREAD_DEGREES = 20.0
		const val HEADING_YAW_STEP_DEGREES = 1.0
		const val MAX_HEADING_DELAY = 10
		const val KEY_RESOLUTION = 10.0
		val AIRBORNE_KEYS = listOf(MovementKeys.FORWARD, MovementKeys.NONE)
	}
}
