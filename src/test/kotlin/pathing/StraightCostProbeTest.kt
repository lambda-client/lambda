/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.TrajectoryPlanner.withinBudget
import com.lambda.pathing.coarse.*
import com.lambda.pathing.trajectory.*
import com.lambda.util.player.prediction.*
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration

/** What does the simplest possible walk actually cost the search? */
@Tag("bedrock-corpus")
class StraightCostProbeTest {
    @Test
    fun `cost of a five block straight walk`() {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -10..10) for (z in -10..10) blocks[BlockPos(x, 99, z)] = SnapshotBlockPhysics.FULL_CUBE
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-12, 92, -12, 12, 112, 12), blocks,
        )
        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            options = SimpleMoveOptions(maxJumpDrop = 2),
        )
        val start = Stance(0, 100, 0)
        val goal = Stance(0, 100, 5)
        for (engine in listOf("anchor", "value-field")) {
            val planner = CoarsePlanner(environment.withinBudget(start, goal), moves, start, goal)
            check(planner.repair(Duration.INFINITE).converged)
            val initial = MovementSimulationState.synthetic(
                profile = PROFILE, position = Vec3d(0.5, 100.0, 0.5),
                rotation = Rotation(0.0, 0.0), velocity = Vec3d(0.0, -0.0784, 0.0), onGround = true,
            )
            val started = System.nanoTime()
            val outcome = TrajectoryPlanner.searchWithRerouting(planner, 0L) { route ->
                if (engine == "anchor") {
                    MotionAnchorSearch.search(route, initial, PROFILE, environment, WalkingSeedSearchConfig())
                } else {
                    planner.expandField(extraTicks = 36.0, maxExpansions = 20_000)
                    ValueFieldAnchorSearch.search(
                        route, planner.valueField(), initial, PROFILE, environment, WalkingSeedSearchConfig(),
                    )
                }
            }
            val ms = (System.nanoTime() - started) / 1_000_000
            val r = outcome?.result as? WalkingSeedSearchResult.Success
            val rollouts = r?.attempts?.size ?: 0
            r?.safePrefix?.let {
                println("[anytime] $engine: safe ${it.frames}-frame tape after ${it.rolloutsToFind} " +
                    "rollouts; full plan took ${it.rolloutsToFull}")
            }
            println("[straight] $engine: ${r?.tape?.frameCount} frames from $rollouts rollouts in ${ms}ms")
            // The cheapest possible route is the honest floor on search cost. Expanding
            // every action at every anchor before looking at any result put this at 4,498
            // rollouts for five blocks; diving on the best action put it back to hundreds.
            // A regression here means the search has gone back to enumerating.
            assertTrue(rollouts in 1..MAX_ROLLOUTS, "$engine spent $rollouts rollouts on a straight walk")
        }
    }

    private companion object {
        /** Generous: the measured cost is ~450, and the point is to catch a return to thousands. */
        const val MAX_ROLLOUTS = 1200

        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8, eyeHeight = 1.62,
        )
    }
}
