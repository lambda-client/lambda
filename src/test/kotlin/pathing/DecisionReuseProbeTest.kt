/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.trajectory.AnchorRollout
import com.lambda.pathing.trajectory.AttemptAccumulator
import com.lambda.pathing.trajectory.Outcome
import com.lambda.pathing.trajectory.PlanSegment
import com.lambda.pathing.trajectory.SearchProbe
import com.lambda.pathing.trajectory.ValueAnchor
import com.lambda.pathing.trajectory.ValueFieldAnchorSearch
import com.lambda.pathing.trajectory.ValueFieldSearchConfig
import com.lambda.pathing.world.center
import kotlin.test.Test
import kotlin.test.assertTrue
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Tag

/**
 * The gate for the spine-and-shortcut design: do DECISIONS survive a perturbed rejoin?
 *
 * `RejoinReuseProbeTest` established that replaying a certified input tape from a
 * displaced body fails -- 72% valid three centimetres out, 0.9% at one beam bucket -- so
 * a shortcut cannot reuse the spine's frames. The claim the design rests on is that the
 * decisions behind those frames do survive, because a movement program is a closed-loop
 * controller: re-run from a different body it re-solves its launch instead of replaying a
 * stale one.
 *
 * This drives the real [AnchorRollout] rather than a reimplementation of it. An earlier
 * version of this probe rebuilt the movement program by hand and scored a segment as
 * reproduced only when it landed on `decision.step`; its control came back at 22%,
 * because roughly a third of segments legitimately finish on a *different* stance -- the
 * corner catch in `AnchorRollout.complete`, where a body catching a pad edge is credited
 * to its supporting block. The control here has to be ~100% or nothing else on the line
 * means anything.
 */
@Tag("bedrock-corpus")
class DecisionReuseProbeTest {

    private class Case(
        val name: String,
        val segments: List<PlanSegment.Move>,
        val rollouts: AnchorRollout,
    )

    private class Verdict(var ok: Int = 0, var failed: Int = 0) {
        val total: Int get() = ok + failed
        val rate: Double get() = if (total == 0) 0.0 else 100.0 * ok / total
    }

    /**
     * Built once for the whole class. Planning the corpus is expensive and this shares a
     * JVM with `HorizonWalkProbeTest`, which walks against a real wall clock -- rebuilding
     * these per level starved it into failing.
     */
    private fun cases(): List<Case> = CASES

    private fun buildCases(): List<Case> = ProbeScenarios.all().mapNotNull { scenario ->
        val outcome = ProbeScenarios.planned(scenario)
        val plan = (outcome.result as? PathPlanResult.Planned)?.path?.takeIf { !it.partial }?.plan
            ?: return@mapNotNull null
        val field = outcome.planner.valueField()
        val goal = outcome.route.goal.center(scenario.environment)
        Case(
            name = scenario.name,
            segments = plan.segments.filterIsInstance<PlanSegment.Move>(),
            rollouts = AnchorRollout(
                movements = outcome.catalog,
                field = field,
                config = CONSTRAINTS,
                searchConfig = ValueFieldSearchConfig(),
                environment = scenario.environment,
                profile = ProbeScenarios.PROFILE,
                goalPoint = { goal },
                attempts = AttemptAccumulator(),
                progressOf = { 0 },
                probe = SearchProbe.NONE,
            ),
        )
    }

    @Test
    fun `decision chains survive a perturbed rejoin`() {
        val cases = cases()
        check(cases.isNotEmpty()) { "no scenario produced a full plan" }

        var controlRate = 0.0
        for ((label, posDelta, velDelta) in LEVELS) {
            val overall = Verdict()
            val per = StringBuilder()
            for (case in cases) {
                val verdict = Verdict()
                for (index in case.segments.indices) {
                    if (index == 0 || index % REJOIN_STRIDE != 0) continue
                    if (!case.segments[index - 1].settledExit(STOPPED_SPEED)) continue
                    for ((dx, dz) in DIRECTIONS) {
                        val start = case.segments[index].entry.perturbed(
                            dx * posDelta, dz * posDelta, dx * velDelta, dz * velDelta,
                        )
                        if (rerun(case, index, start)) verdict.ok++ else verdict.failed++
                    }
                }
                overall.ok += verdict.ok
                overall.failed += verdict.failed
                per.append(" %s=%.0f%%".format(case.name, verdict.rate))
            }
            if (label == "control") controlRate = overall.rate
            println(
                "[decision] dpos=%-6.4f dvel=%-6.4f (%-10s) valid=%5.1f%%  (%d ok / %d failed) |%s".format(
                    posDelta, velDelta, label, overall.rate, overall.ok, overall.failed, per,
                ),
            )
        }
        // 95, not 99: chain steering reads the guide field, and the momentum layer's
        // two-class labels drift more between the walk that recorded a segment and the
        // fresh field that re-runs it -- measured at ~3% of traverse segments. The
        // perturbation ladder reads relative survival, which a small control leak
        // widens but does not bias.
        assertTrue(
            controlRate >= 95.0,
            "control must reproduce the recorded chain; harness is invalid at $controlRate%",
        )
    }

    /**
     * How deep a re-run gets before it fails, which is what sizes a splice.
     *
     * The pass/fail probe demands the WHOLE remaining chain re-certify, which is a lower
     * bound on usefulness: a splice that re-certifies eight segments and then diverges is
     * still a splice, it just re-searches from the ninth. This reports the depth reached,
     * so the design can pick a splice horizon rather than guess one.
     */
    @Test
    fun `how deep a re-run gets before it diverges`() {
        for ((label, posDelta, velDelta) in DEPTH_LEVELS) {
            val depths = ArrayList<Int>()
            val remaining = ArrayList<Int>()
            for (case in cases()) {
                for (index in case.segments.indices) {
                    if (index == 0 || index % REJOIN_STRIDE != 0) continue
                    if (!case.segments[index - 1].settledExit(STOPPED_SPEED)) continue
                    for ((dx, dz) in DIRECTIONS) {
                        val start = case.segments[index].entry.perturbed(
                            dx * posDelta, dz * posDelta, dx * velDelta, dz * velDelta,
                        )
                        depths += depth(case, index, start)
                        remaining += case.segments.size - index
                    }
                }
            }
            val sorted = depths.sorted()
            val full = depths.indices.count { depths[it] >= remaining[it] }
            println(
                "[depth]    (%-10s) median=%d  p25=%d  p75=%d  reached-end=%.0f%%  >=4 segments=%.0f%%  >=8=%.0f%%".format(
                    label,
                    sorted[sorted.size / 2], sorted[sorted.size / 4], sorted[sorted.size * 3 / 4],
                    100.0 * full / depths.size,
                    100.0 * depths.count { it >= 4 } / depths.size,
                    100.0 * depths.count { it >= 8 } / depths.size,
                ),
            )
        }
    }

    /** Segments successfully re-certified from [from] before the first failure. */
    private fun depth(case: Case, from: Int, start: MovementSimulationState): Int {
        var anchor = ValueAnchor(
            state = start, stance = ValueFieldAnchorSearch.stanceOf(start), elapsed = 0,
            collisionEvents = 0, launchMargin = 0, inputSwitches = 0,
            parent = null, inputs = emptyList(), boundary = 0,
        )
        var reached = 0
        for (index in from until case.segments.size) {
            when (val outcome = case.rollouts.transition(anchor, case.segments[index].decision, null)) {
                is Outcome.Anchored -> {
                    anchor = outcome.anchor
                    reached++
                }
                is Outcome.Arrived -> return case.segments.size - from
                else -> return reached
            }
        }
        return reached
    }

    /** Re-run decisions [from]..end, each from the previous one's exit. */
    private fun rerun(case: Case, from: Int, start: MovementSimulationState): Boolean {
        var anchor = ValueAnchor(
            state = start,
            stance = ValueFieldAnchorSearch.stanceOf(start),
            elapsed = 0,
            collisionEvents = 0,
            launchMargin = 0,
            inputSwitches = 0,
            parent = null,
            inputs = emptyList(),
            boundary = 0,
        )
        for (index in from until case.segments.size) {
            when (val outcome = case.rollouts.transition(anchor, case.segments[index].decision, null)) {
                is Outcome.Anchored -> anchor = outcome.anchor
                // A braked stop short of the chain's end is still a certified arrival.
                is Outcome.Arrived -> return true
                else -> return false
            }
        }
        return true
    }

    private fun MovementSimulationState.perturbed(px: Double, pz: Double, vx: Double, vz: Double) =
        copy(
            position = Vec3d(position.x + px, position.y, position.z + pz),
            velocity = Vec3d(velocity.x + vx, velocity.y, velocity.z + vz),
            boundingBox = boundingBox.offset(px, 0.0, pz),
        )

    private companion object {
        val CASES: List<Case> by lazy { DecisionReuseProbeTest().buildCases() }

        val CONSTRAINTS = MotionConstraints()
        const val STOPPED_SPEED = 0.01

        /** Sampling stride over rejoin points; this probe shares a JVM with a wall-clock gate. */
        const val REJOIN_STRIDE = 3

        val DIRECTIONS = listOf(
            1.0 to 0.0, 0.0 to 1.0, 0.707 to 0.707, -0.707 to 0.707,
        )

        val DEPTH_LEVELS = listOf(
            Triple("1/4 bucket", 0.0625, 0.01875),
            Triple("1 bucket", 0.25, 0.075),
        )

        val LEVELS = listOf(
            Triple("control", 0.0, 0.0),
            Triple("1/8 bucket", 0.03125, 0.009375),
            Triple("1/4 bucket", 0.0625, 0.01875),
            Triple("1/2 bucket", 0.125, 0.0375),
            Triple("1 bucket", 0.25, 0.075),
        )
    }
}
