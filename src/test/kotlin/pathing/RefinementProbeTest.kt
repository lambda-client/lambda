/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.TrajectoryPlanner.withinBudget
import com.lambda.pathing.coarse.*
import com.lambda.pathing.debug.BedrockFieldLayout
import com.lambda.pathing.trajectory.*
import com.lambda.util.player.prediction.*
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Tag
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.time.Duration

/** Does the published trajectory actually get better while the body walks it? */
@Tag("bedrock-corpus")
class RefinementProbeTest {
    @Test
    fun `refinement improves the tape it publishes`() {
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(
                -2, 56, -BedrockFieldLayout.HALF_WIDTH - 2,
                BedrockFieldLayout.LENGTH + 1, 71, BedrockFieldLayout.HALF_WIDTH + 2,
            ),
            blocks = BedrockFieldLayout.solidCells().associate {
                BlockPos(it.x, it.y, it.z) to SnapshotBlockPhysics.FULL_CUBE
            },
        )
        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            options = SimpleMoveOptions(maxJumpDrop = 2),
        )
        var improvedCases = 0
        var firstTotal = 0
        var finalTotal = 0

        for ((index, endpoints) in BedrockFieldLayout.randomEndpointPairs(count = 12).withIndex()) {
            val start = Stance(endpoints.first.x, endpoints.first.y, endpoints.first.z)
            val goal = Stance(endpoints.second.x, endpoints.second.y, endpoints.second.z)
            val planner = CoarsePlanner(environment.withinBudget(start, goal), moves, start, goal)
            if (!planner.repair(Duration.INFINITE).converged) continue
            planner.expandField(extraTicks = 36.0, maxExpansions = 20_000)

            val dx = (goal.x - start.x).toDouble()
            val dz = (goal.z - start.z).toDouble()
            val initial = MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5),
                rotation = Rotation(Math.toDegrees(atan2(-dx, dz)), 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0),
                onGround = true,
            )
            val outcome = TrajectoryPlanner.searchWithRerouting(planner, index.toLong()) { route ->
                ValueFieldAnchorSearch.search(
                    route, planner.valueField(), initial, PROFILE, environment, WalkingSeedSearchConfig(),
                )
            }
            val first = outcome?.result as? WalkingSeedSearchResult.Success ?: continue

            val improvements = ArrayList<Int>()
            val walked = java.util.concurrent.atomic.AtomicInteger(0)
            TrajectoryPlanner.refineWhileWalking(
                first, outcome.route, planner, PROFILE, environment, WalkingSeedSearchConfig(),
                // A cursor that advances models the walk the refiner is racing: it ends
                // the sweep the way the real executor does, instead of standing at frame
                // zero forever while the loop keeps finding new cut points.
                cursorFrame = { walked.getAndIncrement().takeIf { it < first.tape.frameCount } },
                onImprovement = { improvements += it.plan.tape.frameCount },
                started = System.currentTimeMillis(),
            )
            firstTotal += first.tape.frameCount
            finalTotal += improvements.lastOrNull() ?: first.tape.frameCount
            if (improvements.isNotEmpty()) improvedCases++
            println(
                "[refine] case $index: ${first.tape.frameCount} frames -> ${improvements.joinToString()
                    .ifEmpty { "no improvement" }}"
            )
        }
        println("[refine] improved $improvedCases cases; total $firstTotal -> $finalTotal frames")
    }

    private companion object {
        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8, eyeHeight = 1.62,
        )
    }
}
