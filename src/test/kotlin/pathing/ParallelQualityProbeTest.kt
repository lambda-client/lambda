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

/**
 * What extra search throughput buys against the walking body.
 *
 * The virtual clock prices one expansion at the wall time it takes; parallel batches
 * make expansions cheaper by the MEASURED kernel speedup (RolloutParallelScalingTest:
 * 3.74x at 4 threads, 6.87x at 8 on this machine), so the body consumes frames while
 * the search produces proportionally more work. Quality is read off the usual columns.
 * Non-gating report.
 */
@Tag("bedrock-corpus")
class ParallelQualityProbeTest {
    @Test
    fun `tape quality at measured parallel search speeds`() {
        // (parallelism, micros per expansion) -- 640 / measured speedup.
        val settings = listOf(1 to 640L, 4 to 171L, 8 to 93L)
        val scenarios = buildList {
            for (seed in 1..4) {
                val course = ParkourCourseLayout.course(jumps = 20, seed = seed)
                add(Triple("course-$seed", courseEnvironment(course), course.start to course.goal))
            }
            val bedrock = bedrockEnvironment()
            val surface = BedrockFieldLayout.standableSurface(BedrockFieldLayout.solidCells())
            val head = surface.filter { it.x <= 2 }.minByOrNull { it.z * it.z }!!
            val tail = surface.filter { it.x >= BedrockFieldLayout.LENGTH - 3 }.minByOrNull { it.z * it.z }!!
            add(Triple("bedrock-traverse", bedrock, Stance(head.x, head.y, head.z) to Stance(tail.x, tail.y, tail.z)))
        }

        for ((parallelism, micros) in settings) {
            var frames = 0
            var stalled = 0
            var collisions = 0
            var arrived = 0
            var excessSum = 0.0
            var excessCount = 0
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
                val clock = VirtualSearchClock(microsPerExpansion = micros)
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
                )
                val path = (outcome as? PathPlanResult.Planned)?.path
                if (path != null && !path.partial) {
                    arrived++
                    val tape = path.plan.frames
                    frames += tape.size
                    collisions += tape.count { it.state.horizontalCollision }
                    stalled += stalledFrames(tape.map { it.state.velocity.horizontalLength() })
                    if (path.excessRatio.isFinite()) {
                        excessSum += (path.excessRatio - 1.0) * 100
                        excessCount++
                    }
                }
            }
            println(
                "[quality] threads=%-2d arrived=%d/%d frames=%-5d excess=%+.1f%% collisions=%d stalled=%d".format(
                    parallelism, arrived, scenarios.size, frames,
                    if (excessCount > 0) excessSum / excessCount else 0.0, collisions, stalled,
                ),
            )
        }
    }

    private fun stalledFrames(speeds: List<Double>): Int {
        val stopped = MotionConstraints().stoppedSpeed
        var total = 0
        var run = 0
        speeds.forEachIndexed { index, speed ->
            if (speed <= stopped) run++ else {
                if (run >= 4) total += run
                run = 0
            }
        }
        return total
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
