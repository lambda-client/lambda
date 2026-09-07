/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import kotlin.math.hypot
import kotlin.test.Test
import net.minecraft.util.math.BlockPos
import org.junit.jupiter.api.Tag

/**
 * How a sprinting body ends at its goal. For each course the tape's last stretch is
 * reduced to: the frame it first comes within a block of the goal, the frames it then
 * spends before resting, the furthest it strays from the goal after first being near,
 * and whether it leaves the goal's neighbourhood and returns (a loop). Non-gating.
 */
@Tag("bedrock-corpus")
class FinishApproachProbeTest {

    /** The goal is where a jump lands: a pad after a gap, alone or as the first block of a strip. */
    @Test
    fun `how the tape ends on a landing goal`() {
        data class Course(val name: String, val padDepth: Int, val gap: Int, val goalOffset: Int, val rise: Int = 0)
        val courses = listOf(
            Course("pad1-gap3", padDepth = 1, gap = 3, goalOffset = 0),
            Course("pad2-gap3", padDepth = 2, gap = 3, goalOffset = 0),
            Course("strip5-first", padDepth = 5, gap = 3, goalOffset = 0),
            Course("strip5-second", padDepth = 5, gap = 3, goalOffset = 1),
            Course("strip5-last", padDepth = 5, gap = 3, goalOffset = 4),
            Course("pad1-gap2-up", padDepth = 1, gap = 2, goalOffset = 0, rise = 1),
            Course("strip3-gap2-up", padDepth = 3, gap = 2, goalOffset = 0, rise = 1),
        )
        for (course in courses) {
            val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
            val runway = 12
            for (z in -4 until runway) for (x in -3..3) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
            val padStart = runway + course.gap
            for (z in padStart until padStart + course.padDepth) for (x in -1..1) {
                blocks[BlockPos(x, 63 + course.rise, z)] = SnapshotBlockPhysics.FULL_CUBE
            }
            val environment = SnapshotSimulationEnvironment.synthetic(
                SimulationSnapshotBounds(-10, 50, -10, 10, 90, 60), blocks,
            )
            val goal = Stance(0, 64 + course.rise, padStart + course.goalOffset)
            val scenario = ProbeScenarios.Scenario(course.name, environment, Stance(0, 64, 0), goal, SimpleMoveOptions(maxJumpDrop = 2))
            report(course.name, scenario)
            report(course.name + "/touch", scenario, MotionConstraints(touchArrival = true))
        }
    }

    @Test
    fun `how the tape ends at the goal`() {
        val courses = listOf(
            Triple("flat-12", Stance(0, 64, 0), Stance(0, 64, 12)),
            Triple("flat-20", Stance(0, 64, 0), Stance(0, 64, 20)),
            Triple("flat-37", Stance(0, 64, 0), Stance(0, 64, 37)),
            Triple("flat-60", Stance(0, 64, 0), Stance(0, 64, 60)),
            Triple("diag-30", Stance(0, 64, 0), Stance(3, 64, 30)),
            Triple("flat-100", Stance(0, 64, 0), Stance(0, 64, 100)),
        )
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (z in -6..110) for (x in -6..6) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-10, 58, -10, 10, 90, 114), blocks,
        )
        for ((name, start, goal) in courses) {
            report(name, ProbeScenarios.Scenario(name, environment, start, goal, SimpleMoveOptions(maxJumpDrop = 2)))
        }
    }

    private fun report(name: String, scenario: ProbeScenarios.Scenario, constraints: MotionConstraints = MotionConstraints()) {
        val goal = scenario.goal
        run {
            val outcome = ProbeScenarios.plan(scenario, constraints = constraints)
            val path = (outcome.result as? PathPlanResult.Planned)?.path
            if (path == null || path.partial) {
                println("[finish] $name FAIL ${outcome.exhaustions.lastOrNull()}")
                return
            }
            val gx = goal.x + 0.5
            val gz = goal.z + 0.5
            val frames = path.plan.frames
            val distances = frames.map { hypot(it.state.position.x - gx, it.state.position.z - gz) }
            val firstNear = distances.indexOfFirst { it <= 1.0 }
            val tail = if (firstNear >= 0) distances.subList(firstNear, distances.size) else emptyList()
            val stray = tail.maxOrNull() ?: Double.NaN
            var left = false
            var loops = 0
            tail.forEach { d ->
                if (d > 1.5) left = true
                if (left && d <= 1.0) { loops++; left = false }
            }
            val final = distances.last()
            val standing = frames.count { it.state.velocity.horizontalLength() <= 0.012 }
            println(
                "[finish] %-8s frames=%-4d near@%-4d tail=%-3d stray=%.2f loops=%d final=%.3f standing=%d bound=%.0f %s"
                    .format(name, frames.size, firstNear, tail.size, stray, loops, final, standing, path.route.lowerBoundTicks, path.movementProfile()),
            )
            if (firstNear >= 0) {
                val trace = (maxOf(0, firstNear - 10) until frames.size).joinToString(" ") { i ->
                    val s = frames[i].state
                    "%.2f%s".format(distances[i], if (s.onGround) "" else "^")
                }
                println("[finish]   d/frame: $trace")
                var at = 0
                val movements = path.segments.mapNotNull { segment ->
                    val start = at
                    at += segment.frames
                    if (at > firstNear - 10) "${segment.movement.key}@$start+${segment.frames}" else null
                }.joinToString(" ")
                println("[finish]   segments: $movements")
            }
            println("[finish]   route tail: ${path.route.nodes.takeLast(6).joinToString(" ") { "(${it.x},${it.y},${it.z})" }}")
        }
    }
}
