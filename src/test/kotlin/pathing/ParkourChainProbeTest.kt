/*
 * Copyright 2026 Lambda
 */

package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.trajectory.MotionAnchorSearch
import com.lambda.pathing.trajectory.ValueFieldAnchorSearch
import com.lambda.pathing.trajectory.WalkingSeedSearchConfig
import com.lambda.pathing.trajectory.WalkingSeedSearchResult
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotBlockPhysics
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * The reported field course, reconstructed: a launch platform, a 2-wide gap rising one
 * block onto a single pad, a corner, then the same jump again turned 90 degrees.
 *
 * Both engines cross it, which is the point of keeping it: the live failure this was
 * built to reproduce is *not* explained by this geometry, so anything that later breaks
 * it is a different, newly introduced fault.
 */
@Tag("bedrock-corpus")
class ParkourChainProbeTest {
    @Test
    fun `chained single block parkour from a standing start`() {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        // The reported course: a launch platform, a 2-wide gap rising one block onto a
        // single pad, then a corner, then the same jump again turned 90 degrees.
        for (x in -8..0) for (z in -1..1) blocks[BlockPos(x, 99, z)] = SnapshotBlockPhysics.FULL_CUBE
        blocks[BlockPos(3, 100, 0)] = SnapshotBlockPhysics.FULL_CUBE
        blocks[BlockPos(3, 101, 3)] = SnapshotBlockPhysics.FULL_CUBE

        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-10, 90, -10, 20, 112, 12),
            blocks = blocks,
        )
        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            options = SimpleMoveOptions(),
        )
        val start = Stance(0, 100, 0)
        val goal = Stance(3, 102, 3)

        for (engine in listOf("anchor", "value-field")) {
            val planner = CoarsePlanner(environment, moves, start, goal)
            check(planner.repair(Duration.INFINITE).converged) { "coarse did not converge" }
            val initial = MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(0.5, 100.0, 0.5),
                rotation = Rotation(-90.0, 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0),
                onGround = true,
            )
            val result = TrajectoryPlanner.searchWithRerouting(planner, 0L) { route ->
                if (engine == "anchor") {
                    MotionAnchorSearch.search(route, initial, PROFILE, environment, WalkingSeedSearchConfig())
                } else {
                    planner.expandField(extraTicks = 36.0, maxExpansions = 20_000)
                    ValueFieldAnchorSearch.search(
                        route, planner.valueField(), initial, PROFILE, environment, WalkingSeedSearchConfig(),
                    )
                }
            }
            val label = when (val r = result?.result) {
                is WalkingSeedSearchResult.Success ->
                    "SUCCESS ${r.tape.frameCount} frames, launches ${r.parameters.gapLaunchFrames}"

                is WalkingSeedSearchResult.NoSafeStop -> {
                    val kinds = r.attempts.mapNotNull { it.diagnostic }
                        .groupingBy { it::class.simpleName }.eachCount()
                    val jumped = r.attempts.count { it.parameters.gapLaunchFrames.isNotEmpty() }
                    "REFUSED after ${r.attempts.size} attempts ($jumped with a launch); $kinds"
                }

                else -> "${r?.let { it::class.simpleName }}"
            }
            println("[parkour] $engine: $label")
            assertTrue(
                result?.result is WalkingSeedSearchResult.Success,
                "$engine could not cross a 2-wide rising gap, a corner, and a second one: $label",
            )
        }
    }

    private companion object {
        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1,
            sneakSpeedModifier = 0.3,
            gravity = 0.08,
            jumpStrength = 0.42,
            stepHeight = 0.6,
            jumpBoostVelocityModifier = 0.0,
            slowFalling = false,
            width = 0.6,
            height = 1.8,
            eyeHeight = 1.62,
        )
    }
}
