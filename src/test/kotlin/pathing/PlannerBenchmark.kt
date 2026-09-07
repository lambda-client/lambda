/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.center
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.movement.LaunchTrigger
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.SegmentFollowerProgram
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.trajectory.TrajectoryRolloutEngine
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.measureTime
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import pathing.ProbeScenarios.PROFILE

/**
 * Wall-time and allocation gate for the planner: microseconds per coarse node, per
 * simulated tick, and per trajectory expansion on fixed fixtures.
 *
 * The corpus is frame-deterministic and blind to speed; this is the instrument the
 * performance stages of the reimplementation are measured against. Run with
 * `./gradlew bench`; every run appends to `build/bench/` and prints the same lines.
 * Nothing here asserts -- see [AllocationGateTest] for the hard bound.
 */
@Tag("bench")
class PlannerBenchmark {
    @Test
    fun benchmark() {
        val lines = ArrayList<String>()
        fun record(line: String) {
            println("[bench] $line")
            lines += line
        }

        record(coarseNodes())
        record(rolloutTicks())
        ProbeScenarios.sample().forEach { record(search(it)) }

        val dir = Path.of("build", "bench")
        Files.createDirectories(dir)
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        Files.writeString(dir.resolve("$stamp.txt"), lines.joinToString("\n") + "\n")
    }

    private fun coarseNodes(): String {
        val scenario = ProbeScenarios.all().first { it.name == "bedrock-traverse" }
        val library = ProbeScenarios.moveLibrary(scenario.options)
        // Warm the arc-probe caches and the JIT on a throwaway planner.
        CoarsePlanner(scenario.environment, library, scenario.start, scenario.goal).repair(Duration.INFINITE)

        val planner = CoarsePlanner(scenario.environment, library, scenario.start, scenario.goal)
        val time = measureTime { planner.repair(Duration.INFINITE) }
        val nodes = planner.graphSize
        return "coarse     nodes=%-6d total=%-8s us/node=%.1f".format(
            nodes, time.inWholeMilliseconds.toString() + "ms", time.inWholeMicroseconds.toDouble() / nodes,
        )
    }

    private fun rolloutTicks(): String {
        val fixture = RolloutFixture()
        repeat(2000) { fixture.rolloutOnce(it) }

        val rollouts = 20_000
        val frames = RolloutFixture.FRAMES
        val allocated = allocatedBytes { repeat(rollouts) { fixture.rolloutOnce(it) } }
        val time = measureTime { repeat(rollouts) { fixture.rolloutOnce(it) } }
        val ticks = rollouts.toLong() * frames
        return "rollout    ticks=%-6d us/tick=%.2f bytes/tick=%d".format(
            ticks, time.inWholeMicroseconds.toDouble() / ticks, allocated / ticks,
        )
    }

    private fun search(scenario: ProbeScenarios.Scenario): String {
        ProbeScenarios.plan(scenario) // warm up
        var outcome: ProbeScenarios.Outcome
        val time = measureTime { outcome = ProbeScenarios.plan(scenario) }
        val expansions = outcome.exhaustions.lastOrNull()?.expansions ?: 0
        val frames = (outcome.result as? com.lambda.pathing.PathPlanResult.Planned)?.path?.plan?.frames?.size ?: 0
        val perExpansion = if (expansions > 0) time.inWholeMicroseconds.toDouble() / expansions else Double.NaN
        return "search     %-18s wall=%-7s expansions=%-7d us/expansion=%.1f frames=%d".format(
            scenario.name, time.inWholeMilliseconds.toString() + "ms", expansions, perExpansion, frames,
        )
    }

    companion object {
        /** Bytes allocated by the current thread while running [block]. */
        fun allocatedBytes(block: () -> Unit): Long {
            val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
            val id = Thread.currentThread().id
            val before = bean.getThreadAllocatedBytes(id)
            block()
            return bean.getThreadAllocatedBytes(id) - before
        }
    }
}

/** The jump-onto-pad rollout the scaling probe uses, shared by the benchmark and the allocation gate. */
internal class RolloutFixture {
    private val environment: SnapshotSimulationEnvironment
    private val from = Stance(0, 100, 0)
    private val to = Stance(3, 100, 1)
    private val solution = LaunchSolver.solve(from, to).first()

    init {
        val pads = buildMap {
            for (x in -2..40) for (z in -2..2) put(BlockPos(x, 99, z), SnapshotBlockPhysics.FULL_CUBE)
            for (x in 0..36 step 4) put(BlockPos(x, 100, 0), SnapshotBlockPhysics.FULL_CUBE)
        }
        environment = SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-6, 90, -6, 44, 120, 6),
            blocks = pads,
        )
    }

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
        TrajectoryRolloutEngine.rollout(initial, PROFILE, environment, program, frameCount = FRAMES)
    }

    companion object {
        const val FRAMES = 40
    }
}
