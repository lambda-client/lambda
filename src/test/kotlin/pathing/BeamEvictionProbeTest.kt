/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.search.FrontierDomination
import kotlin.test.Test
import org.junit.jupiter.api.Tag

/**
 * Is the beam key discarding the anchors that matter?
 *
 * 82% of anchors created are merged or evicted on arrival at `frontierPerKey = 3`, while
 * the rejoin experiments measured bodies one bucket apart sharing only 45% of their
 * decision futures -- so the beam may be throwing away the anchor that makes the jump and
 * paying to rediscover it. One read per rung: the cap and domination policy against tape
 * quality and search cost. Non-gating report.
 */
@Tag("bedrock-corpus")
class BeamEvictionProbeTest {

    @Test
    fun `per-key cap and domination against tape quality`() {
        val scenarios = ProbeScenarios.sample()
        println("[beam] rungs: perKey x domination")
        for (rung in RUNGS) {
            var arrived = 0
            var frames = 0
            var expansions = 0L
            var admitted = 0L
            var refused = 0L
            val per = StringBuilder()
            for (scenario in scenarios) {
                val outcome = ProbeScenarios.plan(
                    scenario,
                    frontierPerKey = rung.perKey,
                    frontierDomination = rung.domination,
                )
                for (exhaustion in outcome.exhaustions) {
                    expansions += exhaustion.expansions.toLong()
                    admitted += exhaustion.anchorsAdmitted.toLong()
                    refused += (exhaustion.beamDominated + exhaustion.beamCapped).toLong()
                }
                val path = (outcome.result as? PathPlanResult.Planned)?.path
                if (path != null && !path.partial) {
                    arrived++
                    frames += path.plan.frames.size
                    per.append(" ").append(path.plan.frames.size)
                } else per.append(" FAIL")
            }
            println(
                "[beam] perKey=%-2d dom=%-14s arrived=%d/%d  frames=%-5d expansions=%-8d admitted=%-7d refused=%-7d |%s"
                    .format(rung.perKey, rung.domination, arrived, scenarios.size, frames, expansions, admitted, refused, per),
            )
        }
    }

    private class Rung(val perKey: Int, val domination: FrontierDomination)

    /**
     * The corpus at production speed: ~600 expansions per body frame instead of the
     * default 78. This is the regime where the search saturates, the open list drains
     * and the starved reserve gets exercised -- the wholesale-revival churn only ever
     * showed up here, never at harness speed.
     */
    @Test
    fun `production-speed pressure rung`() {
        val scenarios = ProbeScenarios.sample()
        for (headroom in listOf(0, 1560)) {
            var arrived = 0
            var frames = 0
            var expansions = 0L
            var starved = 0L
            var drops = 0L
            val per = StringBuilder()
            for (scenario in scenarios) {
                val outcome = ProbeScenarios.plan(
                    scenario, microsPerExpansion = 83L, branchExpansionHeadroomExpansions = headroom,
                )
                for (exhaustion in outcome.exhaustions) {
                    expansions += exhaustion.expansions.toLong()
                    starved += exhaustion.forkStarvedDrops.toLong()
                    drops += exhaustion.adoptableDrops.toLong()
                }
                val path = (outcome.result as? com.lambda.pathing.PathPlanResult.Planned)?.path
                if (path != null && !path.partial) {
                    arrived++
                    frames += path.plan.frames.size
                    per.append(" ").append(path.plan.frames.size)
                } else per.append(" FAIL")
            }
            println(
                "[beam] production-speed head=%-3d arrived=%d/%d frames=%-5d expansions=%-8d drops=%-6d starved=%-7d |%s"
                    .format(headroom, arrived, scenarios.size, frames, expansions, drops, starved, per),
            )
        }
    }

    private companion object {
        /**
         * Three rungs, one lesson each: the shipping beam, the position-aware refinement
         * that beats it on the corpus (held back by the refusal grind on the baseline
         * walks), and the free search that bounds what the beam costs at all. The full
         * ladder that established the curve -- widening the cap alone changes nothing,
         * because nearly every refusal is domination -- lives in the session notes; this
         * task shares a JVM with a wall-clock test that starves under load.
         */
        val RUNGS = listOf(
            Rung(3, domination = FrontierDomination.FULL),
            Rung(3, domination = FrontierDomination.POSITION_AWARE),
            Rung(64, domination = FrontierDomination.OFF),
        )
    }
}
