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
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.time.Duration

/**
 * What the improvement loop actually does on a walk that lasts half a minute.
 *
 * Every other refinement bench here advances the cursor once per *attempt*, which caps the
 * work at one attempt per frame and makes a cheap improver look identical to an expensive
 * one. Real walks are limited by the clock, not by attempt count, and the long routes are
 * exactly where the reports of "it improves twice and then goes quiet" come from -- so
 * this one runs against a wall clock, with no client and no player.
 */
@Tag("bedrock-corpus")
class LongPathRefinementProbeTest {
    @Test
    fun `improvement over a full length walk`() {
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

        // End to end across the field: the longest walk this fixture can produce, so the
        // improver gets the same half-minute of runway it gets in game.
        val surface = BedrockFieldLayout.standableSurface(BedrockFieldLayout.solidCells())
        val head = checkNotNull(surface.filter { it.x <= 2 }.minByOrNull { it.z * it.z })
        val tail = checkNotNull(
            surface.filter { it.x >= BedrockFieldLayout.LENGTH - 3 }.minByOrNull { it.z * it.z }
        )
        val start = Stance(head.x, head.y, head.z)
        val goal = Stance(tail.x, tail.y, tail.z)

        val planner = CoarsePlanner(environment.withinBudget(start, goal), moves, start, goal)
        check(planner.repair(Duration.INFINITE).converged) { "no coarse route across the field" }
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
        val outcome = TrajectoryPlanner.searchWithRerouting(planner, 0L) { route ->
            ValueFieldAnchorSearch.search(route, field, initial, PROFILE, environment, config)
        }
        val first = outcome?.result as? WalkingSeedSearchResult.Success
            ?: error("no certified tape across the field")

        val boundaries = first.planDecisions?.boundaries ?: first.spliceFrames
        val before = SegmentCosts.attribute(
            first.rollout.initialState, first.rollout.frames, boundaries, field,
        ).filterNot { it.terminal }
        println(
            "[long] planned %d frames (%.1f s of walking), excess %.1f ticks over %d segments"
                .format(
                    first.tape.frameCount, first.tape.frameCount * 0.05,
                    before.sumOf { it.excessTicks }, before.size,
                )
        )

        val decisions = first.planDecisions?.decisions.orEmpty()
        println("[long] worst stretches of the planned tape:")
        before.sortedByDescending { it.excessTicks }.take(8).forEach { cost ->
            val index = boundaries.indexOfFirst { it >= cost.toFrame }
            val kind = index.takeIf { it >= 0 }?.let { decisions.getOrNull(it) }
            println(
                "[long]   frames %3d-%3d: spent %2d, worth %5.1f, excess %5.1f after %s"
                    .format(
                        cost.fromFrame, cost.toFrame, cost.actualTicks, cost.boundTicks,
                        cost.excessTicks, kind?.let { it::class.simpleName } ?: "-",
                    )
            )
        }

        // A cursor on the clock, exactly as the executor is: one frame every 50 ms.
        val startedAt = System.nanoTime()
        val walkedFrames = AtomicLong(0)
        val improvements = ArrayList<Pair<Long, Int>>()
        TrajectoryPlanner.refineWhileWalking(
            first, outcome.route, planner, PROFILE, environment, config,
            cursorFrame = {
                val elapsedTicks = (System.nanoTime() - startedAt) / 50_000_000L
                walkedFrames.set(elapsedTicks)
                elapsedTicks.toInt().takeIf { it < first.tape.frameCount }
            },
            onImprovement = { path ->
                improvements += (System.nanoTime() - startedAt) / 1_000_000L to path.plan.tape.frameCount
            },
            started = System.currentTimeMillis(),
        )

        println(
            "[long] %d attempts, %d landed, %d -> %d frames"
                .format(
                    TrajectoryPlanner.attempts, TrajectoryPlanner.improvements,
                    first.tape.frameCount, improvements.lastOrNull()?.second ?: first.tape.frameCount,
                )
        )
        println("[long] decision refusals: " + ValueFieldAnchorSearch.rerunDiagnostics.entries
            .sortedByDescending { it.value }.joinToString { "${it.key}=${it.value}" })
        println("[long] refusals: " + TrajectoryPlanner.refusals.entries
            .sortedByDescending { it.value }.joinToString { "${it.key}=${it.value}" })
        improvements.forEach { (millis, frames) ->
            println("[long]   at %5d ms (frame %3d): %d frames".format(millis, millis / 50, frames))
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
