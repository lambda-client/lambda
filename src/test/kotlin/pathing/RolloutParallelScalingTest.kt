/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.center
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.movement.LaunchTrigger
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.SegmentFollowerProgram
import com.lambda.pathing.trajectory.TrajectoryRolloutEngine
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotBlockPhysics
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Tag

/**
 * How the rollout kernel -- the search's dominant cost -- scales across threads.
 *
 * A rollout is ~40 simulator ticks over an immutable snapshot: pure reads plus
 * per-rollout allocation. If this scales, batch-parallel expansion (roll the top-K
 * frontier decisions concurrently, admit in rank order to stay deterministic) is worth
 * building; if it does not, the bottleneck is allocation or memory bandwidth and the
 * parallelism story ends here. Non-gating report.
 */
@Tag("bedrock-corpus")
class RolloutParallelScalingTest {
    @Test
    fun `rollout throughput across thread counts`() {
        val pads = buildMap {
            for (x in -2..40) for (z in -2..2) put(BlockPos(x, 99, z), SnapshotBlockPhysics.FULL_CUBE)
            for (x in 0..36 step 4) put(BlockPos(x, 100, 0), SnapshotBlockPhysics.FULL_CUBE)
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-6, 90, -6, 44, 120, 6),
            blocks = pads,
        )
        val from = Stance(0, 100, 0)
        val to = Stance(3, 100, 1)
        val solution = LaunchSolver.solve(from, to).first()

        fun rolloutOnce(seed: Int) {
            val initial = MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(from.x + 0.4 + (seed % 8) * 0.025, from.y.toDouble(), from.z + 0.5),
                rotation = Rotation(-90.0 + (seed % 5), 0.0),
                velocity = Vec3d(0.05 + (seed % 4) * 0.03, -0.0784, 0.0),
                onGround = true,
            )
            val program = SegmentFollowerProgram(
                nodes = listOf(from, to).map { it.center() },
                sprint = solution.sprint,
                lookAheadNodes = 1,
                launch = LaunchTrigger(seed % 3),
                maxYawChange = MotionConstraints().maxYawDegreesPerFrame,
                holdForwardInFlight = solution.holdForward,
                holdTicks = solution.holdTicks,
            )
            TrajectoryRolloutEngine.rollout(initial, PROFILE, environment, program, frameCount = 40)
        }

        repeat(2000) { rolloutOnce(it) } // warmup: JIT + allocation paths

        val work = 24_000
        var single = 0.0
        for (threads in listOf(1, 2, 4, 8, 12)) {
            val pool = Executors.newFixedThreadPool(threads)
            val counter = AtomicInteger()
            val started = System.nanoTime()
            repeat(threads) {
                pool.submit {
                    while (true) {
                        val index = counter.getAndIncrement()
                        if (index >= work) break
                        rolloutOnce(index)
                    }
                }
            }
            pool.shutdown()
            check(pool.awaitTermination(120, TimeUnit.SECONDS))
            val seconds = (System.nanoTime() - started) / 1e9
            val perSecond = work / seconds
            if (threads == 1) single = perSecond
            println("[scaling] threads=%-2d rollouts/s=%,.0f speedup=%.2fx efficiency=%.0f%%".format(
                threads, perSecond, perSecond / single, 100.0 * perSecond / single / threads,
            ))
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
