package pathing

// Repro for the gated-run failure: a retained journey on the 120-length bedrock field
// walks to the edge of its first horizon ring, the ring then advances over the goal,
// and the repaired backward field must still reach the new start.
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.session.RouteResolution
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.BedrockFieldLayout
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration

class HorizonRingAdvanceTest {
    @Test
    fun `a retained journey routes on after its ring advances over the goal`() {
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(
                -2, 56, -BedrockFieldLayout.HALF_WIDTH - 2,
                121, 71, BedrockFieldLayout.HALF_WIDTH + 2,
            ),
            blocks = BedrockFieldLayout.solidCells(length = 120).associate {
                BlockPos(it.x, it.y, it.z) to SnapshotBlockPhysics.FULL_CUBE
            },
        )
        val start = Stance(1, 63, 0)
        val goal = Stance(118, 63, 0)
        val state = CoarsePlanningState(
            environment, SimpleMoveOptions(maxJumpDrop = 2), start, goal, horizonChunks = 4,
        )
        state.repairFrom(start, emptySet(), emptySet())
        state.planner.repair(Duration.INFINITE, maxExpansions = 1_000_000)
        val first = assertNotNull(
            RouteResolution(state).resolve(start, 1L, 1_000_000), "the first leg must route",
        )
        println("[ring] first leg ends at ${first.goal}, nodes=${state.planner.graphSize}")
        // Resolving the terminal's fiction must keep the route off one-way pockets: the
        // raw extraction used to end at (79, 63, 0), a dead end the reveal exposes.
        kotlin.test.assertTrue(
            state.planner.moves.successorCosts(state.planner.view, first.goal).isNotEmpty() &&
                state.planner.stanceCost(first.goal).isFinite(),
            "the resolved terminal ${first.goal} must be routable onward",
        )

        // The walk ends where the first route did (at or near the ring's edge); the
        // ring then re-grants around it, revealing the rest of the field and the goal.
        val second = first.goal
        state.repairFrom(second, emptySet(), emptySet())
        val repair = state.planner.repair(Duration.INFINITE, maxExpansions = 1_000_000)
        println("[ring] second repair expansions=${repair.processedNodes} nodes=${state.planner.graphSize} converged=${repair.converged}")
        val route = RouteResolution(state).resolve(second, 2L, 1_000_000)
        if (route == null) println("[ring] FAILURE ${state.planner.routeFailureReport()}")
        assertNotNull(route, "the retained journey must route after the ring advanced")
    }
}
