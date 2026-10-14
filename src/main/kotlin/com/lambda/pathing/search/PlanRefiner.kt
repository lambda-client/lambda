package com.lambda.pathing.search

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.families.MovementCatalog
import com.lambda.pathing.coarse.SwitchableValueField
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment

internal class PlanRefiner(
	private val catalog: MovementCatalog,
	private val legs: List<PlanLeg>,
	private val initialState: MovementSimulationState,
	private val profile: PlayerPhysicsProfile,
	private val environment: SnapshotSimulationEnvironment,
	private val config: MotionConstraints,
	private val parallelism: Int,
	private val cancelled: () -> Boolean,
	private val probe: SearchProbe = SearchProbe.NONE,
) {
	class Plan(val segments: List<PlanSegment>, val touches: List<LegTouch>) {
		val frames: Int get() = segments.lastOrNull()?.endFrame ?: 0

		fun legAt(frame: Int): Int = touches.count { it.frame <= frame }
	}

	class Level(val budget: Int, val stall: Int, val beam: Int, val lastResort: Boolean)

	val sprintModes: List<Boolean> get() = config.sprintModes

	var expansions = 0L
		private set

	var grafts = 0L
		private set

	fun refineAt(plan: Plan, index: Int, level: Level, exploration: Exploration? = null, allowance: Int = 0): Plan? {
		val junction = plan.segments.getOrNull(index) as? PlanSegment.Move ?: return null
		val offset = junction.startFrame
		val leg = plan.legAt(offset).coerceAtMost(legs.lastIndex)
		val steps = plan.segments.subList(index, plan.segments.size).map { segment ->
			val segmentLeg = plan.legAt(segment.startFrame).coerceAtMost(legs.lastIndex)
			when (segment) {
				is PlanSegment.Move -> SpineStep.Decide(segment.decision, segmentLeg)
				is PlanSegment.Terminal -> SpineStep.Replay(segment.inputs, segmentLeg)
			}
		}
		val field = SwitchableValueField(legs[leg].field)
		val loop = SearchLoop(
			legs[leg].route, catalog, field, junction.entry, profile, environment, config,
			SearchConfig(maxExpansions = level.budget, stallExpansions = level.stall, frontierPerKey = level.beam, lastResortFromStart = level.lastResort, exploration = exploration),
			cancelled = cancelled,
			finalGoal = legs.last().route.goal,
			onConcluded = { stats ->
				expansions += stats.expansions
				grafts += stats.grafts
			},
			parallelism = parallelism,
			probe = probe,
			legs = FixedLegs(legs, leg, field),
			spine = Spine(steps, legs),
		)
		val result = loop.run() as? MotionPlanResult.Success ?: return null
		val frames = offset + result.plan.frames.size
		if (frames >= plan.frames + allowance) return null
		if (frames == plan.frames && (result.plan.segments.firstOrNull() as? PlanSegment.Move)?.decision == junction.decision) return null
		val suffix = result.plan.segments.map { it.shifted(offset) }
		val touches = plan.touches.filter { it.frame <= offset } + result.legTouches.map { LegTouch(it.frame + offset, it.waypoint) }
		return Plan(plan.segments.subList(0, index) + suffix, touches)
	}

	fun certify(plan: Plan): MotionPlanResult.Success? {
		val view = legs.first().field.view
		var anchor = ValueAnchor(initialState, heldStanceOf(view, initialState), 0, 0, 0, null, emptyList())
		val touched = plan.touches.associate { it.frame to it.waypoint }
		for (segment in plan.segments) {
			anchor = ValueAnchor(
				state = segment.exit,
				stance = touched[segment.endFrame] ?: heldStanceOf(view, segment.exit),
				elapsed = segment.endFrame,
				collisionEvents = 0,
				inputSwitches = 0,
				parent = anchor,
				inputs = segment.inputs,
				decision = (segment as? PlanSegment.Move)?.decision,
			)
			if (segment.endFrame in touched) anchor.legRoot = true
		}
		val certifier = Certifier(initialState, profile, environment, cancelled, RouteState(legs.first().route, environment), AttemptAccumulator(SearchProbe.NONE))
		return certifier.certify(Solution(anchor, emptyList())) as? MotionPlanResult.Success
	}

	companion object {
		fun of(result: MotionPlanResult.Success): Plan = Plan(result.plan.segments, result.legTouches)

		val LEVELS = listOf(
			Level(budget = 20_000, stall = 3_000, beam = 3, lastResort = false),
			Level(budget = 40_000, stall = 6_000, beam = 6, lastResort = true),
			Level(budget = 80_000, stall = 12_000, beam = 12, lastResort = true),
		)
	}
}

private fun PlanSegment.shifted(offset: Int): PlanSegment = when (this) {
	is PlanSegment.Move -> PlanSegment.Move(decision, entry, exit, inputs, startFrame + offset)
	is PlanSegment.Terminal -> PlanSegment.Terminal(entry, exit, inputs, startFrame + offset)
}
