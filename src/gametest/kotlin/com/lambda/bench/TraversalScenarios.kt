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
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.util.Random

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
    /** Optional bulk fixture construction on the integrated-server thread. */
    val serverFixture: ((MinecraftServer) -> Unit)? = null,
    val start: Vec3d,
    val startYaw: Float = 0f,
    val goal: FastVector,
    val timeoutTicks: Int = 400,
    val expectSuccess: Boolean = true,
    val allowJump: Boolean = false,
    /**
     * Distance to the goal node center that counts as arrival — the oracle
     * additionally requires standing still (grounded, braked) at goal level.
     * The default accepts feet anywhere on the goal block, nothing looser.
     */
    val goalTolerance: Double = 0.5,
    /** Optional node whose successor edges are logged before planning (debug). */
    val probeNode: FastVector? = null,
    /** Scheduled world edits: (tick, server command) executed mid-traversal. */
    val mutations: List<Pair<Int, String>> = emptyList(),
    /**
     * Whether a miss fails the suite. H6 *baseline* scenarios run ungated:
     * they measure the current executor's maneuver reliability (the number
     * WP3 must beat) and are promoted to gated once envelope-checked
     * execution makes them dependable.
     */
    val gated: Boolean = true,
    /** Optional quality gates beyond eventual goal arrival. */
    val maxFirstFollowingTick: Int? = null,
    val maxPlanningPauseTicks: Int? = null,
    val maxLongestPlanningPauseTicks: Int? = null,
    val maxLongestMovementStallTicks: Int? = null,
    val maxLongestExecutorLostBurstTicks: Int? = null,
    val maxReplansRequested: Int? = null,
    val minJumpLandingSuccessRate: Double? = null,
)

/** Plain [PlannerConfig] for benchmarks — independent of any UI config state. */
data class BenchPlannerConfig(
    override val computeBudget: Long = 50L,
    override val maxPathLength: Int = 10_000,
    override val allowDiagonal: Boolean = true,
    override val allowVertical: Boolean = true,
    override val allowJump: Boolean = false,
    override val maxDropHeight: Int = 3,
    /** WP3.2 discovery rides with jumps in the bench. */
    override val allowManeuverDiscovery: Boolean = allowJump,
) : PlannerConfig

object TraversalScenarios {
    // All fixtures live in this region; it is wiped before every scenario.
    // Keep volumes below the /fill block limit (32768).
    private val RESET = listOf(
        "/fill -16 62 -14 24 78 14 minecraft:air",
        "/fill -16 63 -14 24 63 14 minecraft:stone",
    )

    /**
     * Reconstructs the exposed bottom-bedrock field from the field test: a
     * guaranteed bottom sheet plus four independently sampled bedrock layers
     * using vanilla's 80/60/40/20% layer profile. Removing every non-bedrock
     * block leaves the dense two-dimensional pillars, pockets, overhangs and
     * narrow landings visible in the reference screenshot — not a 1-D height
     * strip. Direct server writes avoid thousands of `/setblock` commands.
     */
    private val EXPOSED_BEDROCK_FIXTURE: (MinecraftServer) -> Unit = { server ->
        val world = server.overworld
        val random = Random(0x5EED_BEDL)
        for (x in 0..95) {
            for (z in -20..20) {
                for (layer in 1..4) {
                    val probability = (5 - layer) / 5.0
                    if (random.nextDouble() < probability) {
                        world.setBlockState(
                            BlockPos(x, 62 + layer, z),
                            Blocks.BEDROCK.defaultState,
                            Block.NOTIFY_ALL,
                        )
                    }
                }
            }
        }

        // Flat 3x3 launch and arrival islands make the measured traversal
        // about the chaotic middle, not spawn/goal placement luck.
        for (x in 0..2) for (z in -1..1) for (y in 63..66) {
            world.setBlockState(BlockPos(x, y, z), Blocks.AIR.defaultState, Block.NOTIFY_ALL)
        }
        for (x in 93..95) for (z in -1..1) for (y in 63..66) {
            world.setBlockState(BlockPos(x, y, z), Blocks.AIR.defaultState, Block.NOTIFY_ALL)
        }
    }

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
        gated: Boolean = true,
        serverFixture: ((MinecraftServer) -> Unit)? = null,
        maxFirstFollowingTick: Int? = null,
        maxPlanningPauseTicks: Int? = null,
        maxLongestPlanningPauseTicks: Int? = null,
        maxLongestMovementStallTicks: Int? = null,
        maxLongestExecutorLostBurstTicks: Int? = null,
        maxReplansRequested: Int? = null,
        minJumpLandingSuccessRate: Double? = null,
    ) = TraversalScenario(
        name = name,
        purpose = purpose,
        fixture = RESET + fixture,
        serverFixture = serverFixture,
        start = start,
        startYaw = startYaw,
        goal = goal,
        timeoutTicks = timeoutTicks,
        expectSuccess = expectSuccess,
        allowJump = allowJump,
        probeNode = probeNode,
        mutations = mutations,
        gated = gated,
        maxFirstFollowingTick = maxFirstFollowingTick,
        maxPlanningPauseTicks = maxPlanningPauseTicks,
        maxLongestPlanningPauseTicks = maxLongestPlanningPauseTicks,
        maxLongestMovementStallTicks = maxLongestMovementStallTicks,
        maxLongestExecutorLostBurstTicks = maxLongestExecutorLostBurstTicks,
        maxReplansRequested = maxReplansRequested,
        minJumpLandingSuccessRate = minJumpLandingSuccessRate,
    )

    // Scenario-set discipline: every case must probe a behavior no other
    // case covers — subsumed variants get deleted (flat-walk ⊂ diagonal,
    // single step-up ⊂ stairs, single gap jump ⊂ gauntlet-gap1-x4).
    val all: List<TraversalScenario> = listOf(
        scenario(
            "flat-diagonal-10",
            "Any-angle steering: goal off-axis, refined path should cut the corner. Also the " +
                "flat-walk executor sanity baseline.",
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
            "maze-corridors",
            "S1 maze class: three walls force an S-shaped route with four 90° turns — longer " +
                "multi-turn search, wall-memory refinement, and cornering at speed on one course.",
            "/fill 3 64 -8 3 66 14 minecraft:stone",
            "/fill 8 64 -14 8 66 8 minecraft:stone",
            "/fill 13 64 -8 13 66 14 minecraft:stone",
            goal = fastVectorOf(17, 64, 0),
            timeoutTicks = 900,
        ),

        scenario(
            "bedrock-exposed-100",
            "Field-test regression: traverse a 95-block span (over 100 planned blocks) through a seeded 96x41 exposed " +
                "vanilla-profile bedrock field with dense 2-D pillars, pockets, overhangs and " +
                "narrow landings. Measures maneuver discovery efficiency, jump reliability, " +
                "refined-path relocalization, unnecessary replans and total traversal ticks.",
            // Split the clear so every /fill stays below the command's 32768-block cap.
            "/fill -4 60 -22 50 70 22 minecraft:air",
            "/fill 51 60 -22 99 70 22 minecraft:air",
            "/fill -2 62 -21 97 62 21 minecraft:bedrock",
            start = Vec3d(0.5, 63.0, 0.5),
            goal = fastVectorOf(95, 63, 0),
            allowJump = true,
            timeoutTicks = 2_400,
            // Discovery now fans flat + ascending + descending candidates
            // per ledge; first movement on this 96-block jump course lands
            // around tick 68 (~3.4 s) with the per-landing sim budget. The
            // gate holds THAT line; T7 one-sim-per-pattern caching is the
            // planned lever to pull it back under 40.
            maxFirstFollowingTick = 80,
            maxPlanningPauseTicks = 10,
            maxLongestPlanningPauseTicks = 5,
            maxLongestMovementStallTicks = 10,
            maxLongestExecutorLostBurstTicks = 8,
            maxReplansRequested = 2,
            minJumpLandingSuccessRate = 1.0,
            serverFixture = EXPOSED_BEDROCK_FIXTURE,
        ),

        scenario(
            "stair-down-4",
            "Four consecutive 1-block step-downs: sustained descent execution — drop-2 covers one " +
                "walk-off, nothing covered a descending staircase (the asymmetric mirror of " +
                "stair-up-4-open).",
            "/fill -2 64 -2 1 67 2 minecraft:stone",
            "/fill 2 64 -2 3 66 2 minecraft:stone",
            "/fill 4 64 -2 5 65 2 minecraft:stone",
            "/fill 6 64 -2 7 64 2 minecraft:stone",
            start = Vec3d(0.5, 68.0, 0.5),
            goal = fastVectorOf(11, 64, 0),
            timeoutTicks = 400,
        ),

        scenario(
            "drop-3-boundary",
            "Walk-off drop of exactly maxDropHeight (3): the deepest allowed drop edge — probes " +
                "the config boundary from the allowed side (drop-5-too-deep covers the refusal).",
            "/fill -2 64 -2 2 66 2 minecraft:stone",
            start = Vec3d(0.5, 67.0, 0.5),
            goal = fastVectorOf(8, 64, 0),
            timeoutTicks = 300,
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

        // ------------------------------------------------------------------
        // S3 parkour gauntlet (research plan §5.2). Two scenario classes:
        //
        // H6 BASELINE (gated=false): jumps the graph already represents, run
        // on elevated runways so a missed jump strands the agent on the base
        // floor with no route back up — failure is a clean timeout, never a
        // recovery walk that muddies the timing numbers. These measure the
        // reactive executor's single-attempt reliability; WP3.3's
        // envelope-checked scripts must beat them (H6 predicts <50% vs ≥95%).
        //
        // F3 PROBES (gated, expectSuccess=false): connections the 45°-
        // quantized graph cannot represent at all. They pass today as clean
        // planner failures and each flips to expectSuccess=true when the
        // WP3.2 discovery / WP3.3 chain solver lands — the F3 coverage
        // metric of conjecture C1.
        // ------------------------------------------------------------------

        scenario(
            "gauntlet-gap1-x4",
            "H6 baseline: four consecutive 1-block gap jumps in a straight line — repeated takeoff " +
                "timing with no heading changes.",
            "/fill 0 65 -1 3 65 1 minecraft:stone",
            "/fill 5 65 -1 7 65 1 minecraft:stone",
            "/fill 9 65 -1 11 65 1 minecraft:stone",
            "/fill 13 65 -1 15 65 1 minecraft:stone",
            "/fill 17 65 -1 19 65 1 minecraft:stone",
            start = Vec3d(0.5, 66.0, 0.5),
            goal = fastVectorOf(18, 66, 0),
            allowJump = true,
            timeoutTicks = 500,
            gated = false,
        ),

        scenario(
            "gauntlet-gap1-turns",
            "H6 baseline: 1-block gap jumps with 90° heading changes between them — yaw settling " +
                "and re-takeoff on short pads.",
            "/fill 0 65 -1 2 65 1 minecraft:stone",
            "/fill 4 65 -1 6 65 2 minecraft:stone",
            "/fill 4 65 4 6 65 6 minecraft:stone",
            "/fill 8 65 4 10 65 6 minecraft:stone",
            start = Vec3d(0.5, 66.0, 0.5),
            goal = fastVectorOf(9, 66, 5),
            allowJump = true,
            timeoutTicks = 500,
            gated = false,
        ),

        scenario(
            "gauntlet-gap1-rise1",
            "H6 baseline: two rising gap jumps (1 gap, +1 landing) — the gapJump(rise=1) template's " +
                "first execution coverage; the arc must clear the far lip.",
            "/fill 0 65 -1 2 65 1 minecraft:stone",
            "/fill 4 66 -1 6 66 1 minecraft:stone",
            "/fill 8 67 -1 10 67 1 minecraft:stone",
            start = Vec3d(0.5, 66.0, 0.5),
            goal = fastVectorOf(9, 68, 0),
            allowJump = true,
            timeoutTicks = 500,
            gated = false,
        ),

        scenario(
            "gauntlet-mixed",
            "H6 headline course: step-up, elevated gap jump, 90° turn, walk-off drop-2, gap jump — " +
                "every maneuver class the graph has, chained. End-to-end ticks are the baseline " +
                "WP3 must beat. Every pad stands ≥3 above the base floor: a legal drop-off + " +
                "step-up bypass along the floor must not exist (observed cheat with 1-high pads).",
            "/fill 0 67 -1 3 67 1 minecraft:stone",
            "/fill 4 68 -1 6 68 1 minecraft:stone",
            "/fill 8 68 -1 10 68 1 minecraft:stone",
            "/fill 8 66 2 10 66 4 minecraft:stone",
            "/fill 8 66 6 10 66 8 minecraft:stone",
            start = Vec3d(0.5, 68.0, 0.5),
            goal = fastVectorOf(9, 67, 7),
            allowJump = true,
            timeoutTicks = 600,
            gated = false,
        ),

        scenario(
            "f3-gap2-sprint",
            "WP3.2 discovery: 2-block gap crossed by a discovered sprint-jump edge (3 forward) — " +
                "no template represents it. Ungated H8 baseline until discovery+execution prove " +
                "stable across runs.",
            "/fill 0 65 -1 3 65 1 minecraft:stone",
            "/fill 6 65 -1 9 65 1 minecraft:stone",
            start = Vec3d(0.5, 66.0, 0.5),
            goal = fastVectorOf(8, 66, 0),
            allowJump = true,
            timeoutTicks = 300,
            gated = false,
        ),

        scenario(
            "f3-gap3-sprint",
            "WP3.2 discovery: 3-block gap — near the sprint-jump distance limit; the discovered " +
                "edge is validated at full sprint entry. Ungated H8 baseline.",
            "/fill 0 65 -1 3 65 1 minecraft:stone",
            "/fill 7 65 -1 10 65 1 minecraft:stone",
            start = Vec3d(0.5, 66.0, 0.5),
            goal = fastVectorOf(9, 66, 0),
            allowJump = true,
            timeoutTicks = 300,
            gated = false,
        ),

        scenario(
            "f3-gap-angled",
            "WP3.2 discovery (T5 gadget class): the only crossing is an oblique ~18° sprint jump " +
                "(displacement 3,1) no 45°-quantized edge can represent — the arbitrary-angle case " +
                "discovery exists for. Ungated H8 baseline.",
            "/fill 0 65 -1 2 65 1 minecraft:stone",
            "/fill 5 65 1 6 65 2 minecraft:stone",
            start = Vec3d(0.5, 66.0, 0.5),
            goal = fastVectorOf(6, 66, 2),
            allowJump = true,
            timeoutTicks = 300,
            gated = false,
        ),

        scenario(
            "disc-gap-diag45",
            "WP3.2 discovery at pure 45°: 1-wide pads force the diagonal sprint jump " +
                "(displacement 3,3 ≈ 4.24, no shallower candidate reaches) — the maximal-angle " +
                "discovery case plus a narrow-pad landing; f3-gap-angled covers the shallow " +
                "angle. Ungated H8 baseline.",
            "/fill 0 65 0 2 65 0 minecraft:stone",
            "/fill 5 65 3 7 65 3 minecraft:stone",
            start = Vec3d(0.5, 66.0, 0.5),
            goal = fastVectorOf(6, 66, 3),
            allowJump = true,
            timeoutTicks = 300,
            gated = false,
        ),

        scenario(
            "disc-gap4-too-far",
            "Discovery band cap: a 4-wide gap (displacement 5) exceeds the flat sprint-jump " +
                "envelope — no candidate may be proposed, no edge hallucinated; clean no-path. " +
                "Regression guard against band inflation without envelope support.",
            "/fill 0 65 -1 3 65 1 minecraft:stone",
            "/fill 8 65 -1 11 65 1 minecraft:stone",
            start = Vec3d(0.5, 66.0, 0.5),
            goal = fastVectorOf(10, 66, 0),
            allowJump = true,
            timeoutTicks = 200,
            expectSuccess = false,
        ),

        scenario(
            "disc-gap-ceiling",
            "Discovery under a 2-high ceiling: the arc is physically impossible, the validation " +
                "sims must fail (head bonk → short arc → below start), no edge, clean no-path — " +
                "the sims respect world collision, not just the yaw line.",
            "/fill 0 65 -1 3 65 1 minecraft:stone",
            "/fill 6 65 -1 9 65 1 minecraft:stone",
            "/fill 0 68 -1 9 68 1 minecraft:stone",
            start = Vec3d(0.5, 66.0, 0.5),
            goal = fastVectorOf(8, 66, 0),
            allowJump = true,
            timeoutTicks = 200,
            expectSuccess = false,
        ),

        scenario(
            "mutation-jump-blocked",
            "S2 × WP3.2: the planned route uses a discovered jump; at tick 4 a wall fills the " +
                "gap's flight path. invalidateAround must drop the edge, re-discovery must fail " +
                "against the wall, and the repair must reroute over the walk bridge — nothing " +
                "else exercises discovery invalidation.",
            "/fill 0 65 -1 3 65 1 minecraft:stone",
            "/fill 6 65 -1 9 65 1 minecraft:stone",
            "/fill 0 65 2 9 65 3 minecraft:stone",
            start = Vec3d(0.5, 66.0, 0.5),
            goal = fastVectorOf(8, 66, 0),
            allowJump = true,
            timeoutTicks = 600,
            mutations = listOf(4 to "/fill 4 66 -1 5 67 1 minecraft:stone"),
        ),

        scenario(
            "f3-momentum-chain",
            "WP3.3 chain: two 2-block gaps bridged by a single 1-wide landing block — a chain " +
                "macro-edge validated and executed by the shared momentum policy (jump on every " +
                "grounded tick, mid-air braking into each landing). The single-jump envelope " +
                "correctly refuses this; only the chain solver connects it. Ungated baseline.",
            "/fill 0 65 -1 3 65 1 minecraft:stone",
            "/setblock 6 65 0 minecraft:stone",
            "/fill 9 65 -1 12 65 1 minecraft:stone",
            start = Vec3d(0.5, 66.0, 0.5),
            goal = fastVectorOf(11, 66, 0),
            allowJump = true,
            timeoutTicks = 300,
            gated = false,
        ),
    )
}
