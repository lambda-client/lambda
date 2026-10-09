/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.session.CoarseFollower
import com.lambda.pathing.session.CoarseService
import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.session.walkHorizon
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.core.Stance
import com.lambda.pathing.search.SearchStats
import com.lambda.pathing.search.VirtualSearchClock
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

/**
 * A two-leg route on flat ground with a walk-through waypoint in the middle: one search,
 * one tape. The waypoint must be passed at speed (no standing frames around it), the
 * search must switch legs exactly once, and the tape must still end at rest on the final goal.
 */
class WalkThroughWaypointTest {

    @Test
    fun `a walk-through waypoint is passed without stopping and the tape ends at the final goal`() {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (z in -6..100) for (x in -6..6) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-10, 58, -10, 10, 90, 104), blocks,
        )
        val options = SimpleMoveOptions(maxJumpDrop = 2)
        val start = Stance(0, 64, 0)
        val waypoint = Stance(0, 64, 45)
        val goal = Stance(0, 64, 90)

        val first = CoarsePlanningState(environment, options, start, waypoint)
        val second = CoarsePlanningState(environment, options, waypoint, goal)
        val coarse = CoarseService(world = null, legs = listOf(first, second), starts = listOf(start, waypoint), expansionBudget = 200_000)
        coarse.follow(0, start, SpeedClass.STOPPED)
        coarse.settle()
        val frame = checkNotNull(coarse.frame(0))
        val route = frame.route
        val chain = CoarseFollower(coarse, world = null, environment, leg = 0, frame)
        val field = chain.field

        val clock = VirtualSearchClock(microsPerExpansion = 640L)
        val executor = VirtualExecutor(clock)
        val exhaustions = ArrayList<SearchStats>()
        val result = walkHorizon(
            route, first.planner,
            resting(Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5)),
            ProbeScenarios.PROFILE, environment, MotionConstraints(),
            execution = executor,
            publish = { path, _ -> executor.offer(path) },
            started = System.currentTimeMillis(),
            clock = clock,
            finalGoal = goal,
            field = field,
            onConcluded = { exhaustions += it },
            legs = chain,
        )

        val path = (result as? PathPlanResult.Planned)?.path
            ?: error("the two-leg walk did not plan: ${exhaustions.lastOrNull()}")
        assertTrue(!path.partial, "the tape must end at the final goal")
        assertEquals(1, path.legTouches.size, "one waypoint touch on the tape")

        val touch = path.legTouches.single()
        assertEquals(waypoint, touch.waypoint)
        val frames = path.plan.frames
        val at = frames[touch.frame - 1].state.position
        assertTrue(
            hypot(at.x - (waypoint.x + 0.5), at.z - (waypoint.z + 0.5)) <= 1.0,
            "touch frame ${touch.frame} is at $at, not on the waypoint",
        )

        // Passed at speed: no standing frame from the launch until well past the waypoint.
        val slow = (12 until minOf(frames.size, touch.frame + 20)).filter {
            frames[it].state.velocity.horizontalLength() <= 0.05
        }
        assertTrue(slow.isEmpty(), "the body stood still around the waypoint at frames $slow")

        // The body followed published prefixes through the handoff: it was already past
        // the waypoint when the finish sealed, on tape the search kept extending.
        assertTrue(
            executor.executedFrames >= touch.frame,
            "the virtual body executed only ${executor.executedFrames} frames by the finish; the touch is at ${touch.frame}",
        )

        val end = frames.last().state.position
        assertTrue(
            hypot(end.x - (goal.x + 0.5), end.z - (goal.z + 0.5)) <= MotionConstraints().goalRadius + 1e-6,
            "the tape ends at $end, not at rest on the final goal",
        )
        println(
            "[legs] frames=${frames.size} touch@${touch.frame} executed=${executor.executedFrames} adoptions=${executor.adoptions} ${path.movementProfile()} ${exhaustions.lastOrNull()}",
        )
    }
}
