/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.LegChain
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.search.SearchExhaustion
import com.lambda.pathing.search.SearchProbe
import com.lambda.pathing.search.VirtualSearchClock
import com.lambda.pathing.session.RouteResolution
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import java.util.concurrent.Executor
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

/**
 * A two-leg route on flat ground with a walk-through waypoint in the middle: one search,
 * one tape. The waypoint must be passed at speed (no standing frames around it), the
 * search must switch legs exactly once, and the tape must still end at rest on the final goal.
 */
class LegChainTest {

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
        first.repairFrom(start, emptySet(), emptySet())
        check(first.planner.repair(Duration.INFINITE).converged)
        first.planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
        val route = checkNotNull(first.planner.routePlan(0L))
        val field = first.planner.valueField()

        val second = CoarsePlanningState(environment, options, waypoint, goal)
        val chain = LegChain(
            world = null,
            states = listOf(second),
            starts = listOf(waypoint),
            coarseExpansionBudget = 200_000,
            snapshotRevision = 0L,
            cancelled = { false },
            probe = SearchProbe.NONE,
            executor = Executor { it.run() },
            fieldExpansionTicks = 36.0,
            fieldExpansionBudget = Duration.INFINITE,
            fieldExpansionNodes = 20_000,
        )
        chain.begin(LegChain.Leg(first, route, field, RouteResolution(first), null))

        val clock = VirtualSearchClock(microsPerExpansion = 640L)
        val executor = VirtualExecutor(clock)
        val exhaustions = ArrayList<SearchExhaustion>()
        val result = TrajectoryPlanner.walkHorizon(
            route, first.planner,
            MovementSimulationState.synthetic(
                profile = ProbeScenarios.PROFILE,
                position = Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5),
                rotation = Rotation(0.0, 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0),
                onGround = true,
            ),
            ProbeScenarios.PROFILE, environment, MotionConstraints(),
            cursorFrame = { executor.cursorFrame() },
            publish = { path, _ -> executor.offer(path) },
            started = System.currentTimeMillis(),
            clock = clock,
            adoptedSequence = executor::adoptedSequence,
            finalGoal = goal,
            field = field,
            onExhaustion = { exhaustions += it },
            fieldExpansionBudget = Duration.INFINITE,
            legs = chain,
        )

        val path = (result as? PathPlanResult.Planned)?.path
            ?: error("the two-leg walk did not plan: ${exhaustions.lastOrNull()}")
        assertTrue(!path.partial, "the tape must end at the final goal")
        assertEquals(1, chain.switches, "exactly one leg handoff")
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
            "[legs] frames=${frames.size} touch@${touch.frame} executed=${executor.executedFrames} adoptions=${executor.adoptions} switches=${chain.switches} ${path.movementProfile()} ${exhaustions.lastOrNull()}",
        )
    }
}
