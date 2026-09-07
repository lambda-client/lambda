/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.center
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.actions.LaunchTrigger
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SegmentFollowerProgram
import com.lambda.pathing.search.TrajectoryRollout
import com.lambda.pathing.search.TrajectoryRolloutEngine
import com.lambda.pathing.physics.MovementSimulationState
import net.minecraft.util.math.Vec3d
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import kotlin.test.Test
import kotlin.time.Duration
import org.junit.jupiter.api.Tag
import pathing.ProbeScenarios.PROFILE
import pathing.ProbeScenarios.moveLibrary

/** Ground truth for the (3,2) off-axis jump the coarse graph reportedly misses. */
@Tag("bedrock-corpus")
class OffAxisJumpProbeTest {
    @Test
    fun `standing reachability ground truth for the jump template matrix`() {
        val from = Stance(0, 100, 0)
        for ((dx, dz) in listOf(2 to 2, 3 to 0, 3 to 1, 3 to 2, 3 to 3, 4 to 0, 4 to 1, 4 to 2, 5 to 1)) {
            for (rise in listOf(0, 1, -1)) {
                val to = Stance(dx, 100 + rise, dz)
                val solutions = LaunchSolver.solve(from, to)
                var certified = 0
                var tried = 0
                var example = "-"
                for (solution in solutions) {
                    for (delay in 0..8) {
                        tried++
                        val rollout = rollFromRest(pads(from, to), from, to, solution, delay)
                        val landing = rollout.frames.firstOrNull { frame ->
                            frame.state.onGround && frame.index > delay &&
                                Math.floor(frame.state.position.y).toInt() == to.y &&
                                frame.state.position.x > to.x - 0.3 && frame.state.position.x < to.x + 1.3 &&
                                frame.state.position.z > to.z - 0.3 && frame.state.position.z < to.z + 1.3
                        } ?: continue
                        certified++
                        if (example == "-") example = "mode=${solution.mode} offset=%.1f delay=%d".format(solution.launchOffset, delay)
                    }
                }
                val airGap = Math.hypot(
                    (Math.abs(dx) - 1).coerceAtLeast(0).toDouble(),
                    (Math.abs(dz) - 1).coerceAtLeast(0).toDouble(),
                )
                println("[standing] ($dx,$dz,rise=$rise) centre=%.2f air=%.2f solutions=%d certified=%d/%d %s".format(
                    Math.hypot(dx.toDouble(), dz.toDouble()), airGap, solutions.size, certified, tried, example,
                ))
            }
        }
    }

    private fun rollFromRest(
        environment: SnapshotSimulationEnvironment,
        from: Stance,
        to: Stance,
        solution: LaunchSolution,
        delay: Int,
    ): TrajectoryRollout {
        val dx = (to.x - from.x).toDouble()
        val dz = (to.z - from.z).toDouble()
        val yaw = Math.toDegrees(Math.atan2(-dx, dz))
        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(from.x + 0.5, from.y.toDouble(), from.z + 0.5),
            rotation = Rotation(yaw, 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        )
        val program = SegmentFollowerProgram(
            nodes = listOf(from, to).map { it.center() },
            sprint = solution.sprint,
            lookAheadNodes = 1,
            launch = LaunchTrigger(delay),
            maxYawChange = MotionConstraints().maxYawDegreesPerFrame,
            holdForwardInFlight = solution.holdForward,
            holdTicks = solution.holdTicks,
        )
        return TrajectoryRolloutEngine.rollout(initial, PROFILE, environment, program, frameCount = 40)
    }

    @Test
    fun `a span-3 offset-2 jump across coarse graph configurations`() {
        val from = Stance(0, 100, 0)
        for ((dx, dz) in listOf(3 to 2, 4 to 2)) {
            val to = Stance(dx, 100, dz)
            val solutions = LaunchSolver.solve(from, to)
            println("[offaxis] solver (0,0)->($dx,$dz) dist=%.3f solutions=%d best=%s".format(
                Math.hypot(dx.toDouble(), dz.toDouble()), solutions.size,
                solutions.firstOrNull()?.let { "mode=${it.mode} speed=%.3f slack=%.3f".format(it.speed, it.speedSlack) } ?: "-",
            ))
            for (options in listOf(
                "span=5" to SimpleMoveOptions(),
                "span=4 (live default)" to SimpleMoveOptions(maxJumpSpan = 4),
                "span=3 (parkour fixtures)" to SimpleMoveOptions(maxJumpSpan = 3, maxJumpDrop = 2),
            )) {
                val environment = pads(from, to)
                val moves = moveLibrary(options.second)
                val planner = CoarsePlanner(environment, moves, from, to)
                val converged = planner.repair(Duration.INFINITE).converged
                val route = if (converged) planner.routePlan(0L) else null
                println("[offaxis]   ${options.first}: converged=$converged route=" +
                    (route?.nodes?.joinToString("->") { "(${it.x},${it.y},${it.z})" } ?: "-"))
            }
        }
    }

    private fun pads(vararg stances: Stance): SnapshotSimulationEnvironment =
        SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-8, 90, -8, 12, 112, 12),
            blocks = stances.associate {
                BlockPos(it.x, it.y - 1, it.z) to SnapshotBlockPhysics.FULL_CUBE
            },
        )
}
