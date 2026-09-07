/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.ParkourCourseLayout
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.CoarseVoxel
import kotlin.test.Test
import net.minecraft.util.math.BlockPos
import org.junit.jupiter.api.Tag

/**
 * The field protocol, in the harness: generated parkour at production tempo
 * (~600 expansions per body frame), production parallelism, improver on. This is the
 * regime where the user measures with a stopwatch, and it is the one no other probe
 * covered -- every corpus probe runs at 78 expansions per frame with the improver off.
 */
@Tag("bedrock-corpus")
class FieldParkourProbeTest {

    @Test
    fun `generated parkour at field tempo`() {
        var frames = 0
        var bound = 0.0
        for (seed in 1..6) {
            val course = ParkourCourseLayout.course(jumps = 20, seed = seed)
            val scenario = ProbeScenarios.Scenario(
                "field-$seed", courseEnvironment(course), course.start, course.goal,
                SimpleMoveOptions(maxJumpSpan = 3, maxJumpDrop = 2),
            )
            val outcome = ProbeScenarios.plan(
                scenario, parallelism = 4, microsPerExpansion = 83L, improvementBudget = 1500,
            )
            var expansions = 0L
            var admitted = 0L
            var merged = 0L
            var dominated = 0L
            var evicted = 0L
            var capped = 0L
            var drops = 0L
            var improveRollouts = 0L
            var improveSplices = 0L
            var suppressed = 0L
            for (exhaustion in outcome.exhaustions) {
                expansions += exhaustion.expansions.toLong()
                admitted += exhaustion.anchorsAdmitted.toLong()
                merged += (exhaustion.beamDominated + exhaustion.beamEvicted + exhaustion.beamCapped).toLong()
                dominated += exhaustion.beamDominated.toLong()
                evicted += exhaustion.beamEvicted.toLong()
                capped += exhaustion.beamCapped.toLong()
                drops += exhaustion.adoptableDrops.toLong()
                improveRollouts += exhaustion.improvementRollouts.toLong()
                improveSplices += exhaustion.improvementSplices.toLong()
                suppressed += exhaustion.commitSuppressed.toLong()
            }
            val path = (outcome.result as? PathPlanResult.Planned)?.path
            if (path != null && !path.partial) {
                frames += path.plan.frames.size
                bound += path.plan.frames.size / path.excessRatio
                println(
                    ("[field] %-8s frames=%-4d excess=%+5.1f%% expansions=%-7d admitted=%-6d " +
                        "merged=%2.0f%% (dom=%d evict=%d cap=%d) drops=%-5d improver=%d/%dr")
                        .format(
                            scenario.name, path.plan.frames.size, (path.excessRatio - 1.0) * 100,
                            expansions, admitted, 100.0 * merged / admitted.coerceAtLeast(1),
                            dominated, evicted, capped, drops, improveSplices, improveRollouts,
                        ),
                )
            } else {
                println("[field] %-8s FAIL last: %s".format(scenario.name, outcome.exhaustions.lastOrNull()))
            }
        }
        println("[field] total frames=%d vs bound=%.0f".format(frames, bound))
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
