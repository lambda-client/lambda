/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.TrajectoryPlanner.withinBudget
import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.debug.PlanDump
import com.lambda.pathing.trajectory.MotionAnchorSearch
import com.lambda.pathing.trajectory.ValueFieldAnchorSearch
import com.lambda.pathing.trajectory.WalkingSeedSearchResult
import org.junit.jupiter.api.Tag
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.test.Test
import kotlin.time.Duration

/**
 * Replays plans dumped from a live refusal (`Dump Failed Plans`), on both engines.
 *
 *     ./gradlew planDumps -PdumpDir=/path/to/neolambda/pathing-dumps
 *
 * The point is to close the loop on field failures: the reporter's terrain becomes a
 * fixture that runs offline in a second, instead of a screenshot to reason about.
 */
@Tag("plan-dumps")
class PlanDumpReplayTest {
    @Test
    fun `replay every dumped plan on both engines`() {
        val directory = Path.of(
            System.getProperty("lambda.pathing.dumpDir") ?: DEFAULT_DIRECTORY
        )
        if (!Files.isDirectory(directory)) {
            println("[plan-dump] no dump directory at $directory; nothing to replay")
            return
        }
        val dumps = Files.list(directory).use { paths ->
            paths.filter { it.extension == "dump" }.sorted().toList()
        }
        if (dumps.isEmpty()) {
            println("[plan-dump] no .dump files in $directory")
            return
        }

        for (path in dumps) {
            val plan = PlanDump.read(path)
            val environment = plan.environment()
            // The dump carries the move options and search config the game actually used;
            // rebuilding with library defaults quietly plans a different graph (the
            // settings' jump drop is 2, the option default is 1) and replays a world the
            // reporter never had.
            val moves = SimpleMoveLibrary.build(
                costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
                options = plan.moveOptions,
            )
            println(
                "[plan-dump] ${path.fileName}: ${plan.start} -> ${plan.goal}, " +
                    "${plan.blocks.size} solid cells; ${plan.note}"
            )

            for (engine in listOf("anchor", "value-field")) {
                val planner = CoarsePlanner(
                    environment.withinBudget(plan.start, plan.goal), moves, plan.start, plan.goal,
                )
                if (!planner.repair(Duration.INFINITE).converged) {
                    println("[plan-dump]   $engine: coarse did not converge")
                    continue
                }
                val started = System.nanoTime()
                val outcome = TrajectoryPlanner.searchWithRerouting(planner, 0L) { route ->
                    if (engine == "anchor") {
                        MotionAnchorSearch.search(
                            route, plan.initialState, plan.profile, environment,
                            plan.searchConfig,
                        )
                    } else {
                        planner.expandField(extraTicks = 36.0, maxExpansions = 20_000)
                        ValueFieldAnchorSearch.search(
                            route, planner.valueField(), plan.initialState, plan.profile, environment,
                            plan.searchConfig,
                        )
                    }
                }
                val millis = (System.nanoTime() - started) / 1_000_000
                println("[plan-dump]   $engine: ${describe(outcome?.result)} in ${millis}ms")
            }
        }
    }

    private fun describe(result: WalkingSeedSearchResult?): String = when (result) {
        is WalkingSeedSearchResult.Success ->
            "SUCCESS ${result.tape.frameCount} frames, ${result.controlSegments} segments, " +
                "launches ${result.parameters.gapLaunchFrames}"

        is WalkingSeedSearchResult.NoSafeStop -> {
            val kinds = result.attempts.mapNotNull { it.diagnostic }
                .groupingBy { it::class.simpleName }.eachCount()
                .entries.sortedByDescending { it.value }.joinToString { "${it.value}x${it.key}" }
            val nearest = result.nearest
            "REFUSED after ${result.attempts.size} attempts [$kinds]" +
                (nearest?.let { "; closest ended %.2f blocks out at %.3f b/t".format(it.finalGoalError, it.finalHorizontalSpeed) } ?: "")
        }

        null -> "no coarse route"
        else -> result::class.simpleName ?: "?"
    }

    private companion object {
        const val DEFAULT_DIRECTORY = "build/pathing-dumps"

    }
}
