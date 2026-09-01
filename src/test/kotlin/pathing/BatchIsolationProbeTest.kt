/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.BedrockFieldLayout
import com.lambda.pathing.debug.ParkourCourseLayout
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.trajectory.VirtualSearchClock
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import kotlin.math.atan2
import kotlin.test.Test
import kotlin.time.Duration
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Tag
import pathing.ProbeScenarios.PROFILE
import pathing.ProbeScenarios.bedrockEnvironment
import pathing.ProbeScenarios.moveLibrary

/** Same price per expansion at every parallelism: isolates search quality from throughput. */
@Tag("bedrock-corpus")
class BatchIsolationProbeTest {
    @Test
    fun `batch width at a fixed expansion price`() {
        val scenarios = buildList {
            for (seed in 2..2) {
                val course = ParkourCourseLayout.course(jumps = 20, seed = seed)
                add(Triple("course-$seed", courseEnvironment(course), course.start to course.goal))
            }
            val bedrock = bedrockEnvironment()
            val surface = BedrockFieldLayout.standableSurface(BedrockFieldLayout.solidCells())
            val head = surface.filter { it.x <= 2 }.minByOrNull { it.z * it.z }!!
            val tail = surface.filter { it.x >= BedrockFieldLayout.LENGTH - 3 }.minByOrNull { it.z * it.z }!!
            add(Triple("bedrock-traverse", bedrock, Stance(head.x, head.y, head.z) to Stance(tail.x, tail.y, tail.z)))
        }

        // Serial against the shipping default. The full 1/2/4/8 ladder is what showed
        // batching to be neutral-to-better per expansion; the endpoints keep that honest
        // for a fraction of the wall clock this task has to share.
        for (parallelism in listOf(1, 4)) {
            val per = StringBuilder()
            var frames = 0
            var arrived = 0
            var excessSum = 0.0
            var excessCount = 0
            val esc = ArrayList<Pair<Int, Double>>()
            for ((name, environment, endpoints) in scenarios) {
                val (start, goal) = endpoints
                val options =
                    if (name.startsWith("course")) SimpleMoveOptions(maxJumpSpan = 3, maxJumpDrop = 2)
                    else SimpleMoveOptions(maxJumpDrop = 2)
                val moves = moveLibrary(options)
                val planner = CoarsePlanner(environment, moves, start, goal)
                check(planner.repair(Duration.INFINITE).converged)
                planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
                val route = checkNotNull(planner.routePlan(0L))

                val dx = (goal.x - start.x).toDouble()
                val dz = (goal.z - start.z).toDouble()
                val clock = VirtualSearchClock(microsPerExpansion = 640L)
                val executor = VirtualExecutor(clock)
                val outcome = TrajectoryPlanner.walkHorizon(
                    route, planner,
                    MovementSimulationState.synthetic(
                        profile = PROFILE,
                        position = Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5),
                        rotation = Rotation(Math.toDegrees(atan2(-dx, dz)), 0.0),
                        velocity = Vec3d(0.0, -0.0784, 0.0),
                        onGround = true,
                    ),
                    PROFILE, environment, MotionConstraints(),
                    cursorFrame = { executor.cursorFrame() },
                    publish = { path, _ -> executor.offer(path) },
                    started = System.currentTimeMillis(),
                    clock = clock,
                    adoptedSequence = executor::adoptedSequence,
                    parallelism = parallelism,
                    onExhaustion = { esc.add(it.guideExpansions to it.temperature) },
                )
                val path = (outcome as? PathPlanResult.Planned)?.path
                if (path != null && !path.partial) {
                    arrived++
                    frames += path.plan.frames.size
                    per.append(" ").append(name).append("=").append(path.plan.frames.size)
                    if (path.excessRatio.isFinite()) {
                        excessSum += (path.excessRatio - 1.0) * 100
                        excessCount++
                    }
                } else per.append(" ").append(name).append("=FAIL")
            }
            println(
                "[batch] threads=%-2d arrived=%d/%d frames=%-5d excess=%+.1f%% |%s".format(
                    parallelism, arrived, scenarios.size, frames,
                    if (excessCount > 0) excessSum / excessCount else 0.0, per,
                ),
            )
            println(
                "[esc]   threads=%-2d exits=%d maxGuide=%d meanGuide=%.2f maxTemp=%.2f meanTemp=%.2f".format(
                    parallelism, esc.size,
                    esc.maxOfOrNull { it.first } ?: -1,
                    esc.map { it.first }.average(),
                    esc.maxOfOrNull { it.second } ?: -1.0,
                    esc.map { it.second }.average(),
                ),
            )
        }
    }

    private fun courseEnvironment(course: ParkourCourseLayout.Course): SnapshotSimulationEnvironment =
        SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(
                course.pads.minOf { it.x } - 4, 90, course.pads.minOf { it.z } - 6,
                course.pads.maxOf { it.x } + 4, 120, course.pads.maxOf { it.z } + 6,
            ),
            blocks = course.pads.associate {
                BlockPos(it.x, it.y, it.z) to SnapshotBlockPhysics(
                    ParkourCourseLayout.PadShape.BLOCK.shape(), coarseVoxel = CoarseVoxel.FULL_BLOCK,
                )
            },
        )

}
