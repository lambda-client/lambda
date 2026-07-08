/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.bench

import com.lambda.config.blocks.PlannerConfig
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import net.minecraft.util.math.Vec3d

/**
 * One traversal benchmark case: a world fixture (built via server commands),
 * a start pose, a goal node, and the expected outcome.
 *
 * Scenarios are data so the runner can execute, time, and report them
 * uniformly — this is the seed of the WP0 scenario DSL from
 * docs/benchmark-harness-spec.md.
 */
data class TraversalScenario(
    val name: String,
    /** What this scenario measures / which failure mode it reproduces. */
    val purpose: String,
    /** Server commands that build the fixture (run in order, after the area reset). */
    val fixture: List<String>,
    val start: Vec3d,
    val startYaw: Float = 0f,
    val goal: FastVector,
    val timeoutTicks: Int = 400,
    val expectSuccess: Boolean = true,
    val allowJump: Boolean = false,
    /** Distance to the goal node center that counts as arrival. */
    val goalTolerance: Double = 1.1,
    /** Optional node whose successor edges are logged before planning (debug). */
    val probeNode: FastVector? = null,
    /** Scheduled world edits: (tick, server command) executed mid-traversal. */
    val mutations: List<Pair<Int, String>> = emptyList(),
)

/** Plain [PlannerConfig] for benchmarks — independent of any UI config state. */
data class BenchPlannerConfig(
    override val computeBudget: Long = 50L,
    override val maxPathLength: Int = 10_000,
    override val allowDiagonal: Boolean = true,
    override val allowVertical: Boolean = true,
    override val allowJump: Boolean = false,
    override val maxDropHeight: Int = 3,
) : PlannerConfig

object TraversalScenarios {
    // All fixtures live in this region; it is wiped before every scenario.
    // Keep volumes below the /fill block limit (32768).
    private val RESET = listOf(
        "/fill -16 62 -14 24 78 14 minecraft:air",
        "/fill -16 63 -14 24 63 14 minecraft:stone",
    )

    private fun scenario(
        name: String,
        purpose: String,
        vararg fixture: String,
        start: Vec3d = Vec3d(0.5, 64.0, 0.5),
        startYaw: Float = -90f, // facing +x
        goal: FastVector,
        timeoutTicks: Int = 400,
        expectSuccess: Boolean = true,
        allowJump: Boolean = false,
        probeNode: FastVector? = null,
        mutations: List<Pair<Int, String>> = emptyList(),
    ) = TraversalScenario(
        name = name,
        purpose = purpose,
        fixture = RESET + fixture,
        start = start,
        startYaw = startYaw,
        goal = goal,
        timeoutTicks = timeoutTicks,
        expectSuccess = expectSuccess,
        allowJump = allowJump,
        probeNode = probeNode,
        mutations = mutations,
    )

    val all: List<TraversalScenario> = listOf(
        scenario(
            "flat-walk-8",
            "Baseline: straight flat walk, executor sanity.",
            goal = fastVectorOf(8, 64, 0),
        ),

        scenario(
            "flat-diagonal-10",
            "Any-angle steering: goal off-axis, refined path should cut the corner.",
            goal = fastVectorOf(7, 64, 7),
        ),

        scenario(
            "corridor-turn",
            "Topology: wall blocks the straight line; planner must route around, executor must corner.",
            "/fill 4 64 -6 4 66 6 minecraft:stone",
            goal = fastVectorOf(8, 64, 0),
            timeoutTicks = 600,
        ),

        scenario(
            "step-up-single",
            "One 1-block ledge: jump issuance timing (proximity gate) without any ceiling.",
            "/fill 4 64 -14 24 64 14 minecraft:stone",
            goal = fastVectorOf(8, 65, 0),
        ),

        scenario(
            "stair-up-4-open",
            "Four step-ups in open air: repeated jump timing, no headroom hazard.",
            "/fill 3 64 -2 3 64 2 minecraft:stone",
            "/fill 4 64 -2 4 65 2 minecraft:stone",
            "/fill 5 64 -2 5 66 2 minecraft:stone",
            "/fill 6 64 -2 6 67 2 minecraft:stone",
            "/fill 7 64 -2 10 67 2 minecraft:stone",
            goal = fastVectorOf(9, 68, 0),
            timeoutTicks = 600,
        ),

        scenario(
            "stair-up-tunnel-3high",
            "Stairs under a 3-block ceiling: the arc bonks at the apex but a 1-block rise still fits. " +
                "Must succeed — validates that the runtime headroom gate is not overly strict.",
            "/fill 3 64 -2 3 64 2 minecraft:stone",
            "/fill 4 64 -2 4 65 2 minecraft:stone",
            "/fill 5 64 -2 5 66 2 minecraft:stone",
            "/fill 6 64 -2 6 67 2 minecraft:stone",
            "/fill 7 64 -2 10 67 2 minecraft:stone",
            // Ceiling: 3 clear blocks above every standing level along the stairs.
            "/fill 0 67 -2 2 67 2 minecraft:stone",
            "/fill 3 68 -2 3 68 2 minecraft:stone",
            "/fill 4 69 -2 4 69 2 minecraft:stone",
            "/fill 5 70 -2 5 70 2 minecraft:stone",
            "/fill 6 71 -2 10 71 2 minecraft:stone",
            goal = fastVectorOf(9, 68, 0),
            timeoutTicks = 600,
        ),

        scenario(
            "step-up-low-ceiling",
            "Ledge under a full-width 2-high ceiling: the jump is physically impossible everywhere. " +
                "After the hasHeadClearance(+2) fix the planner must not generate the edge — expect a " +
                "clean failure, not an infinite bonk-retry loop (the creative-flight repro).",
            "/fill 4 64 -14 24 64 14 minecraft:stone",
            // 2-high ceiling over the entire approach strip: standing headroom only.
            "/fill 0 66 -14 3 66 14 minecraft:stone",
            goal = fastVectorOf(8, 65, 0),
            timeoutTicks = 200,
            expectSuccess = false,
            probeNode = fastVectorOf(3, 64, 0),
        ),

        scenario(
            "step-up-ceiling-detour",
            "Ledge with a low ceiling over the direct line only: the planner must route around the " +
                "covered strip and step up where headroom exists, instead of bonking under the ceiling.",
            "/fill 4 64 -14 24 64 14 minecraft:stone",
            // 2-high ceiling over the direct corridor only (z -3..3).
            "/fill 0 66 -3 3 66 3 minecraft:stone",
            goal = fastVectorOf(8, 65, 0),
            timeoutTicks = 600,
            probeNode = fastVectorOf(3, 64, 0),
        ),

        scenario(
            "gap-jump-1",
            "One-block gap jump (allowJump): plan-time jump edge plus airborne execution.",
            "/fill 3 63 -14 3 63 14 minecraft:air",
            goal = fastVectorOf(6, 64, 0),
            allowJump = true,
            timeoutTicks = 400,
        ),

        scenario(
            "drop-2",
            "Two-block walk-off drop: the first asymmetric move — only representable with true " +
                "predecessor enumeration (the reverse jump does not exist).",
            "/fill -2 64 -2 2 65 2 minecraft:stone",
            start = Vec3d(0.5, 66.0, 0.5),
            goal = fastVectorOf(8, 64, 0),
            timeoutTicks = 300,
        ),

        scenario(
            "drop-5-too-deep",
            "Five-block tower with maxDropHeight=3 and no stairs: the drop edge must not be " +
                "generated; expect no path rather than a leap of faith.",
            "/fill -2 64 -2 2 68 2 minecraft:stone",
            start = Vec3d(0.5, 69.0, 0.5),
            goal = fastVectorOf(8, 64, 0),
            timeoutTicks = 200,
            expectSuccess = false,
        ),

        scenario(
            "mutation-wall-mid-walk",
            "S2 dynamic repair: a wall drops across the planned line while walking; D* Lite must " +
                "repair around it without a from-scratch replan.",
            goal = fastVectorOf(12, 64, 0),
            timeoutTicks = 600,
            mutations = listOf(15 to "/fill 7 64 -5 7 66 5 minecraft:stone"),
        ),

        scenario(
            "mutation-ceiling-mid-approach",
            "S2 + asymmetry: a low ceiling appears over the direct step-up while approaching; the " +
                "block-update sync must retract the now-invalid step-up edges (incl. predecessor " +
                "side) and reroute around the covered strip.",
            "/fill 4 64 -14 24 64 14 minecraft:stone",
            goal = fastVectorOf(8, 65, 0),
            timeoutTicks = 600,
            mutations = listOf(8 to "/fill 0 66 -3 3 66 3 minecraft:stone"),
        ),
    )
}
