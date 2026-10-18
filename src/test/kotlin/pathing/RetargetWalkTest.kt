/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.core.Stance
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.search.SearchStats
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
import kotlin.test.assertTrue

class RetargetWalkTest {

	@Test
	fun `a coarse leg retargets in place`() {
		val environment = flat()
		val start = Stance(0, 64, 0)
		val first = Stance(0, 64, 40)
		val second = Stance(20, 64, 40)
		val coarse = CoarseService(world = null, legs = listOf(CoarsePlanningState(environment, SimpleMoveOptions(), start, first)), starts = listOf(start), expansionBudget = 200_000)
		coarse.follow(0, start, SpeedClass.STOPPED)
		coarse.settle()
		assertEquals(first, checkNotNull(coarse.frame(0)).route.goal)
		coarse.retarget(0, second)
		assertEquals(second, coarse.goal(0))
		coarse.settle()
		val frame = checkNotNull(coarse.frame(0))
		assertEquals(second, frame.goal)
		assertEquals(second, frame.route.goal)
	}

	@Test
	fun `a walk retargeted mid-execution keeps its executed prefix and ends at the new goal`() {
		val environment = flat()
		val start = Stance(0, 64, 0)
		val first = Stance(0, 64, 60)
		val second = Stance(25, 64, 30)
		val state = CoarsePlanningState(environment, SimpleMoveOptions(), start, first)
		val coarse = CoarseService(world = null, legs = listOf(state), starts = listOf(start), expansionBudget = 200_000)
		coarse.follow(0, start, SpeedClass.STOPPED)
		coarse.settle()
		val frame = checkNotNull(coarse.frame(0))
		val follower = CoarseFollower(coarse, world = null, environment, leg = 0, frame)

		val clock = VirtualSearchClock(microsPerExpansion = 640L)
		val executor = VirtualExecutor(clock)
		val exhaustions = ArrayList<SearchStats>()
		val published = ArrayList<PublishedPath>()
		var retargetedAt: PublishedPath? = null
		var executedAtRetarget = 0
		val result = walkHorizon(
			frame.route, state.planner,
			resting(Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5)),
			ProbeScenarios.PROFILE, environment, MotionConstraints(),
			execution = executor,
			publish = { path, _ ->
				executor.offer(path)
				published += path
				if (published.size == RETARGET_AFTER_PUBLICATIONS) {
					retargetedAt = path
					executedAtRetarget = executor.executedFrames
					coarse.retarget(0, second)
					coarse.settle()
				}
			},
			started = System.currentTimeMillis(),
			clock = clock,
			finalGoal = first,
			field = follower.field,
			onConcluded = { exhaustions += it },
			legs = follower,
			world = follower,
		)

		val path = (result as? PathPlanResult.Planned)?.path ?: error("the retargeted walk did not plan: ${exhaustions.lastOrNull()}")
		assertEquals(second, path.finalGoal)
		assertTrue(!path.partial, "the tape must end at the new goal")
		val end = path.plan.frames.last().state.position
		assertTrue(hypot(end.x - (second.x + 0.5), end.z - (second.z + 0.5)) <= MotionConstraints().goalRadius + 1e-6, "ends at $end, not at $second")
		val before = checkNotNull(retargetedAt)
		assertTrue(executedAtRetarget > 0, "the retarget happened before any execution")
		for (index in 0 until executedAtRetarget) assertEquals(before.plan.tape[index], path.plan.tape[index], "executed frame $index changed")
		assertTrue(published.size > RETARGET_AFTER_PUBLICATIONS, "the search kept publishing after the retarget")
	}

	private fun flat(): SnapshotSimulationEnvironment {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (z in -6..70) for (x in -6..40) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
		return SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-10, 58, -10, 44, 90, 74), blocks)
	}

	private companion object {
		const val RETARGET_AFTER_PUBLICATIONS = 3
	}
}
