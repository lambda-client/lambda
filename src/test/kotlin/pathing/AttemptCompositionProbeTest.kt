package pathing

// Offline probe: prints where trajectory-search attempts go on the bedrock corpus.
// Not a gate -- run it when tuning the action vocabulary and compare compositions.
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.movement.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.BedrockFieldLayout
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.TrajectoryDecision
import com.lambda.pathing.trajectory.SearchProbe
import com.lambda.pathing.trajectory.VirtualSearchClock
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotBlockPhysics
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.time.Duration

class AttemptCompositionProbeTest {
    @Test
    fun `attempt composition over bedrock corpus`() {
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
        val config = MotionConstraints()
        val tally = TallyProbe()
        for ((index, endpoints) in BedrockFieldLayout.randomEndpointPairs(count = 6).withIndex()) {
            val start = Stance(endpoints.first.x, endpoints.first.y, endpoints.first.z)
            val goal = Stance(endpoints.second.x, endpoints.second.y, endpoints.second.z)
            val planner = CoarsePlanner(environment, moves, start, goal)
            if (!planner.repair(Duration.INFINITE).converged) continue
            planner.expandField(extraTicks = 36.0, maxExpansions = 20_000)
            val route = planner.routePlan(index.toLong()) ?: continue
            val dx = (goal.x - start.x).toDouble()
            val dz = (goal.z - start.z).toDouble()
            val initial = MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5),
                rotation = Rotation(Math.toDegrees(atan2(-dx, dz)), 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0), onGround = true,
            )
            val clock = VirtualSearchClock()
            TrajectoryPlanner.walkHorizon(
                route, planner, initial, PROFILE, environment, config,
                cursorFrame = { clock.cursorFrame() },
                publish = { _, _ -> },
                started = System.currentTimeMillis(),
                clock = clock,
                probe = tally,
            )
        }
        val rows = tally.counts.entries.sortedByDescending { it.value[0] }
        var tried = 0; var rejected = 0
        for ((k, v) in rows) { tried += v[0]; rejected += v[1] }
        println("[tally] total tried=$tried rejected=$rejected (${"%.1f".format(100.0*rejected/tried)}%)")
        for ((k, v) in rows) {
            println("[tally] %-42s tried=%6d rejected=%6d (%5.1f%%) avgRejFrame=%.1f".format(
                k, v[0], v[1], 100.0 * v[1] / v[0],
                if (v[1] > 0) v[2].toDouble() / v[1] else 0.0,
            ))
        }
    }

    private class TallyProbe : SearchProbe {
        val counts = java.util.concurrent.ConcurrentHashMap<String, IntArray>()

        override fun decision(action: TrajectoryDecision, rejected: Boolean, frame: Int) {
            val key = when (action) {
                is TrajectoryDecision.Heading -> "Heading(delay=" + action.delayFrames + ")"
                is TrajectoryDecision.Launch -> "Launch(" + action.movement + ",delay=" + action.delayFrames + ")"
                else -> action::class.simpleName + "(" + action.movement + ")"
            }
            val row = counts.computeIfAbsent(key) { IntArray(3) }
            row[0]++
            if (rejected) {
                row[1]++
                row[2] += frame
            }
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
