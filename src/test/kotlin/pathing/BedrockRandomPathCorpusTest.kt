/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.debug.BedrockFieldLayout
import com.lambda.pathing.trajectory.WalkingSeedSearch
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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

/**
 * Opt-in end-to-end pressure corpus:
 *
 *     ./gradlew bedrockCorpus
 *
 * A fixed terrain and fixed random endpoint sequence make attempt explosions,
 * refusals, and route-quality changes reproducible. It intentionally does not run as
 * part of the fast JVM suite.
 */
@Tag("bedrock-corpus")
class BedrockRandomPathCorpusTest {
    @Test
    fun `random paths across a large bedrock field produce an honest corpus report`() {
        val cells = BedrockFieldLayout.solidCells()
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(
                -2, 56, -BedrockFieldLayout.HALF_WIDTH - 2,
                BedrockFieldLayout.LENGTH + 1, 71, BedrockFieldLayout.HALF_WIDTH + 2,
            ),
            blocks = cells.associate {
                BlockPos(it.x, it.y, it.z) to SnapshotBlockPhysics.FULL_CUBE
            },
        )
        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            options = SimpleMoveOptions(),
        )
        val scenarios = BedrockFieldLayout.randomEndpointPairs(count = SCENARIOS)
        val report = ArrayList<String>()
        report += "case,start,goal,coarseEdges,jumpEdges,result,attempts,frames,segments,millis,reroutes"

        for ((index, endpoints) in scenarios.withIndex()) {
            val start = endpoints.first.toStance()
            val goal = endpoints.second.toStance()
            val planner = CoarsePlanner(environment, moves, start, goal)
            val coarseStarted = System.nanoTime()
            if (!planner.repair(Duration.INFINITE).converged) {
                report += "$index,${start.csv()},${goal.csv()},0,0,NO_ROUTE,0,0,0,${elapsedMillis(coarseStarted)},0"
                continue
            }

            val initial = initialState(start, goal)
            var routeEdges = 0
            var jumpEdges = 0
            val outcome = TrajectoryPlanner.searchWithRerouting(
                planner,
                snapshotRevision = index.toLong(),
            ) { route ->
                routeEdges = route.edges.size
                jumpEdges = route.edges.count {
                    it.kind == com.lambda.pathing.coarse.CoarseMoveKind.JUMP_CANDIDATE
                }
                WalkingSeedSearch.searchContinuously(
                    route, initial, PROFILE, environment, WalkingSeedSearchConfig(),
                )
            }
            val millis = elapsedMillis(coarseStarted)
            val result = outcome?.result
            val attempts = when (result) {
                is WalkingSeedSearchResult.Success -> result.attempts.size
                is WalkingSeedSearchResult.NoSafeStop -> result.attempts.size
                else -> 0
            }
            val frames = (result as? WalkingSeedSearchResult.Success)?.tape?.frameCount ?: 0
            val segments = when (result) {
                is WalkingSeedSearchResult.Success -> result.controlSegments
                is WalkingSeedSearchResult.NoSafeStop -> result.completedSegments
                else -> 0
            }
            val kind = result?.let { it::class.simpleName } ?: "NO_ROUTE"
            val reroutes = outcome?.reroutes ?: 0
            report += "$index,${start.csv()},${goal.csv()},$routeEdges,$jumpEdges,$kind,$attempts,$frames,$segments,$millis,$reroutes"

            // No current search result claims all-entry impossibility, so this corpus
            // must never rewrite the graph and call ordinary gap candidates infeasible.
            assertEquals(0, reroutes, "case $index falsely blacklisted a coarse jump")
        }

        val path = Path.of("build/reports/pathing/bedrock-random-corpus.csv")
        Files.createDirectories(path.parent)
        Files.write(path, report)
        report.forEach { println("[bedrock-corpus] $it") }
    }

    private fun initialState(start: Stance, goal: Stance): MovementSimulationState {
        val dx = (goal.x - start.x).toDouble()
        val dz = (goal.z - start.z).toDouble()
        val yaw = Math.toDegrees(atan2(-dx, dz))
        return MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5),
            rotation = Rotation(yaw, 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        )
    }

    private fun BedrockFieldLayout.SurfacePoint.toStance() = Stance(x, y, z)

    private fun Stance.csv(): String = "$x:$y:$z"

    private fun elapsedMillis(started: Long): Long =
        (System.nanoTime() - started) / 1_000_000

    private companion object {
        const val SCENARIOS = 12
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
