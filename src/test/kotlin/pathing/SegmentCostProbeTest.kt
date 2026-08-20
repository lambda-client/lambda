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

/**
 * Where does a certified tape actually lose its time?
 *
 * The whole-tape number says a path is worse than the coarse bound without saying which
 * part of it is. This prints the attribution, so "the path is not optimal but it is hard
 * to say what it does wrong" becomes a ranked list of stretches.
 */
@Tag("bedrock-corpus")
class SegmentCostProbeTest {
    @Test
    fun `where the tape loses its time`() {
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
        val config = WalkingSeedSearchConfig()
        var totalFrames = 0
        var totalExcess = 0.0
        val worst = ArrayList<Triple<Int, SegmentCost, String>>()

        for ((index, endpoints) in BedrockFieldLayout.randomEndpointPairs(count = 12).withIndex()) {
            val start = Stance(endpoints.first.x, endpoints.first.y, endpoints.first.z)
            val goal = Stance(endpoints.second.x, endpoints.second.y, endpoints.second.z)
            val planner = CoarsePlanner(environment.withinBudget(start, goal), moves, start, goal)
            if (!planner.repair(Duration.INFINITE).converged) continue
            planner.expandField(extraTicks = 36.0, maxExpansions = 20_000)
            val field = planner.valueField()
            val dx = (goal.x - start.x).toDouble()
            val dz = (goal.z - start.z).toDouble()
            val initial = MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5),
                rotation = Rotation(Math.toDegrees(atan2(-dx, dz)), 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0), onGround = true,
            )
            val outcome = TrajectoryPlanner.searchWithRerouting(planner, index.toLong()) { route ->
                ValueFieldAnchorSearch.search(route, field, initial, PROFILE, environment, config)
            }
            val success = outcome?.result as? WalkingSeedSearchResult.Success ?: continue

            val boundaries = success.planDecisions?.boundaries ?: success.spliceFrames
            val costs = SegmentCosts.attribute(
                success.rollout.initialState, success.rollout.frames, boundaries, field,
            )
            val excess = costs.filterNot { it.terminal }.sumOf { it.excessTicks }
            totalFrames += success.tape.frameCount
            totalExcess += excess

            val decisions = success.planDecisions?.decisions.orEmpty()
            costs.filterNot { it.terminal }.sortedByDescending { it.excessTicks }.take(2).forEach { cost ->
                val decision = boundaries.indexOfFirst { it >= cost.toFrame }
                    .takeIf { it >= 0 }?.let { decisions.getOrNull(it) }
                worst += Triple(index, cost, decision?.let { it::class.simpleName } ?: "-")
            }

            println(
                "[cost] case %2d: %3d frames, bound %5.1f, excess %5.1f (%4.1f%%) over %d segments"
                    .format(
                        index, success.tape.frameCount,
                        costs.sumOf { it.boundTicks }, excess,
                        100.0 * excess / success.tape.frameCount, costs.size,
                    )
            )
        }

        println("[cost] total %d frames, %.1f excess ticks (%.1f%%)"
            .format(totalFrames, totalExcess, 100.0 * totalExcess / totalFrames))
        println("[cost] worst stretches:")
        worst.sortedByDescending { it.second.excessTicks }.take(10).forEach { (case, cost, kind) ->
            println(
                "[cost]   case %2d frames %3d-%3d: spent %3d, worth %5.1f, excess %5.1f (%.2f/frame) after %s"
                    .format(
                        case, cost.fromFrame, cost.toFrame, cost.actualTicks,
                        cost.boundTicks, cost.excessTicks, cost.excessRate, kind,
                    )
            )
        }
    }

    private companion object {
        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8, eyeHeight = 1.62,
        )
    }
}
