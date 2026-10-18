package com.lambda.pathing.search

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.families.MovementCatalog
import com.lambda.pathing.coarse.SwitchableValueField
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import java.util.Collections
import java.util.IdentityHashMap

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
	}

	class Level(val budget: Int, val stall: Int, val beam: Int, val lastResort: Boolean)

	val sprintModes: List<Boolean> get() = config.sprintModes

	var expansions = 0L
		private set

	var grafts = 0L
		private set

	val outcomes = HashMap<String, Int>()

	private val memos = IdentityHashMap<PlanSegment, PlantMemo>()
	private var memoRevision: Any? = null

	private fun outcome(kind: String): Plan? {
		outcomes.merge(kind, 1, Int::plus)
		return null
	}

	fun refineAt(plan: Plan, index: Int, level: Level, exploration: Exploration? = null, allowance: Int = 0): Plan? {
		val junction = plan.segments.getOrNull(index) as? PlanSegment.Move ?: return null
		val offset = junction.startFrame
		val waypoints = waypointTouches(plan.touches)
		val leg = waypoints.count { it.frame <= offset }.coerceAtMost(legs.lastIndex)
		val end = plan.segments.indexOfLast { it is PlanSegment.Move } + 1
		if (memoRevision !== environment.revision) {
			memos.clear()
			memoRevision = environment.revision
		}
		if (memos.size > plan.segments.size * MEMO_PLANS) memos.keys.retainAll(Collections.newSetFromMap(IdentityHashMap<PlanSegment, Boolean>()).apply { addAll(plan.segments) })
		val steps = plan.segments.subList(index, end).map { segment ->
			val segmentLeg = waypoints.count { it.frame <= segment.startFrame }.coerceAtMost(legs.lastIndex)
			when (segment) {
				is PlanSegment.Move -> SpineStep.Decide(segment.decision, segmentLeg, memos.getOrPut(segment, ::PlantMemo), segment.chain.takeIf { it.isNotEmpty() })
				is PlanSegment.Terminal -> SpineStep.Replay(segment.inputs, segmentLeg)
			}
		}
		val field = SwitchableValueField(legs[leg].field)
		var plantFailure: String? = null
		val loop = SearchLoop(
			legs[leg].route, catalog, field, junction.entry, profile, environment, config,
			SearchConfig(maxExpansions = level.budget, stallExpansions = level.stall, frontierPerKey = level.beam, lastResortFromStart = level.lastResort, exploration = exploration),
			cancelled = cancelled,
			finalGoal = legs.last().route.goal,
			onConcluded = { stats ->
				expansions += stats.expansions
				grafts += stats.grafts
				plantFailure = stats.plantFailure
			},
			parallelism = parallelism,
			probe = probe,
			legs = FixedLegs(legs, leg, field),
			spine = Spine(steps, legs),
		)
		val raw = loop.run()
		val result = raw as? MotionPlanResult.Success ?: return outcome("search ${raw::class.simpleName}${plantFailure?.let { ", plant $it" } ?: ""}")
		val frames = offset + result.plan.frames.size
		if (frames >= plan.frames + allowance) return outcome(if (frames == plan.frames) "same" else "worse")
		if (frames == plan.frames && (result.plan.segments.firstOrNull() as? PlanSegment.Move)?.decision == junction.decision) return outcome("same")
		val suffix = result.plan.segments.map { it.shifted(offset) }
		val touches = plan.touches.filter { it.frame <= offset } + result.legTouches.map { LegTouch(it.frame + offset, it.waypoint) }
		if (waypointTouches(touches).map { it.waypoint } != waypoints.map { it.waypoint }) return outcome("touches")
		outcomes.merge("gain", 1, Int::plus)
		return Plan(plan.segments.subList(0, index) + suffix, touches)
	}

	private fun waypointTouches(touches: List<LegTouch>): List<LegTouch> {
		var next = 0
		return touches.filter { touch -> (next < legs.size && touch.waypoint == legs[next].route.goal).also { if (it) next++ } }
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
				chain = (segment as? PlanSegment.Move)?.chain.orEmpty(),
			)
			if (segment.endFrame in touched) anchor.legRoot = true
		}
		val certifier = Certifier(initialState, profile, environment, cancelled, RouteState(legs.first().route, environment), AttemptAccumulator(SearchProbe.NONE))
		return certifier.certify(Solution(anchor, emptyList())) as? MotionPlanResult.Success
	}

	companion object {
		private const val MEMO_PLANS = 2

		fun of(result: MotionPlanResult.Success): Plan = Plan(result.plan.segments, result.legTouches)

		val LEVELS = listOf(
			Level(budget = 3_000, stall = 800, beam = 3, lastResort = false),
			Level(budget = 6_000, stall = 1_500, beam = 6, lastResort = true),
		)
	}
}

private fun PlanSegment.shifted(offset: Int): PlanSegment = when (this) {
	is PlanSegment.Move -> PlanSegment.Move(decision, entry, exit, inputs, startFrame + offset, chain)
	is PlanSegment.Terminal -> PlanSegment.Terminal(entry, exit, inputs, startFrame + offset)
}
