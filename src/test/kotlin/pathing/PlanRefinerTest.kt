/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.core.Stance
import com.lambda.pathing.search.LegTouch
import com.lambda.pathing.search.PlanRefiner
import com.lambda.pathing.search.PlanSegment
import com.lambda.pathing.search.VirtualSearchClock
import com.lambda.pathing.session.CoarseFollower
import com.lambda.pathing.session.CoarseService
import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.session.walkHorizon
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A plan stitched from two separate walks stops at the waypoint between them. Refining it must
 * graft a walk that keeps its speed through the waypoint onto the second leg's decisions: the
 * stop goes, the waypoint is still touched, and the spliced plan certifies frame for frame.
 */
class PlanRefinerTest {
	@Test
	fun `refining a plan that stops at a walk-through waypoint removes the stop`() {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (z in -6..100) for (x in -6..6) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
		val environment = SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-10, 58, -10, 10, 90, 104), blocks)
		val options = SimpleMoveOptions(maxJumpDrop = 2)
		val start = Stance(0, 64, 0)
		val waypoint = Stance(0, 64, 45)
		val goal = Stance(0, 64, 90)
		val first = CoarsePlanningState(environment, options, start, waypoint)
		val second = CoarsePlanningState(environment, options, waypoint, goal)
		val coarse = CoarseService(world = null, legs = listOf(first, second), starts = listOf(start, waypoint), expansionBudget = 200_000)
		coarse.follow(0, start, SpeedClass.STOPPED)
		coarse.settle()
		coarse.follow(1, waypoint, SpeedClass.STOPPED)
		coarse.settle()
		val follower = CoarseFollower(coarse, world = null, environment, leg = 0, checkNotNull(coarse.frame(0)))
		val legs = assertNotNull(follower.planLegs(0), "both legs carry a coarse frame")
		val initial = resting(Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5))

		fun walk(leg: Int, from: com.lambda.pathing.physics.MovementSimulationState, to: Stance): PathPlanResult {
			val clock = VirtualSearchClock(microsPerExpansion = 640L)
			val executor = VirtualExecutor(clock)
			return walkHorizon(
				legs[leg].route, (if (leg == 0) first else second).planner, from, ProbeScenarios.PROFILE, environment, MotionConstraints(),
				execution = executor,
				publish = { path, _ -> executor.offer(path) },
				started = System.currentTimeMillis(),
				clock = clock,
				finalGoal = to,
				field = legs[leg].field,
			)
		}

		val toWaypoint = assertNotNull((walk(0, initial, waypoint) as? PathPlanResult.Planned)?.path, "the first leg must plan")
		val stopped = toWaypoint.plan.frames.last().state
		val toGoal = assertNotNull((walk(1, stopped, goal) as? PathPlanResult.Planned)?.path, "the second leg must plan from rest")
		val offset = toWaypoint.plan.frames.size
		val segments = toWaypoint.plan.segments + toGoal.plan.segments.map {
			when (it) {
				is PlanSegment.Move -> PlanSegment.Move(it.decision, it.entry, it.exit, it.inputs, it.startFrame + offset)
				is PlanSegment.Terminal -> PlanSegment.Terminal(it.entry, it.exit, it.inputs, it.startFrame + offset)
			}
		}
		val spine = PlanRefiner.Plan(segments, listOf(LegTouch(offset, waypoint)))

		val refiner = PlanRefiner(second.planner.moves.catalog, legs, initial, ProbeScenarios.PROFILE, environment, MotionConstraints(), parallelism = 1, cancelled = { false })
		var plan = spine
		var index = 0
		while (index < plan.segments.size) {
			refiner.refineAt(plan, index, PlanRefiner.LEVELS.first())?.let { plan = it }
			index++
		}

		assertTrue(plan.frames < spine.frames, "refining must remove the stop: ${spine.frames} -> ${plan.frames} frames")
		val certified = assertNotNull(refiner.certify(plan), "the refined plan must certify")
		assertEquals(plan.frames, certified.plan.frames.size, "the certified plan is the refined plan")
		assertEquals(listOf(waypoint), certified.legTouches.map { it.waypoint }, "the waypoint is still touched")
		val touch = certified.legTouches.single().frame
		val frames = certified.plan.frames
		val slow = (touch - 10 until minOf(frames.size, touch + 10)).filter { frames[it].state.velocity.horizontalLength() <= 0.05 }
		assertTrue(slow.isEmpty(), "the refined plan still stands at the waypoint at frames $slow")
		val end = frames.last().state.position
		assertTrue(hypot(end.x - (goal.x + 0.5), end.z - (goal.z + 0.5)) <= MotionConstraints().goalRadius + 1e-6, "the plan ends at $end, not on the goal")
		println("[refine] spine=${spine.frames} refined=${plan.frames} touch@$touch grafts=${refiner.grafts} expansions=${refiner.expansions}")
	}
}
