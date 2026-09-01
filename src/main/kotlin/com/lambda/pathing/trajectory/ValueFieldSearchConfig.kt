package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseRoutePlan

/**
 * How the beam decides one same-bucket anchor makes another redundant.
 *
 * [FULL] is the shipping rule: earlier, at least as fast, no more collisions or input
 * switches. It is blind to position inside the quarter-block bucket, so an anchor a
 * fraction closer to a jump lip is killed by a twin that arrived a frame earlier --
 * against the probe corpus that blindness costs about 1% tape length and 7% of all
 * expansions at either parallelism, and [POSITION_AWARE] (the dominator must also
 * stand at least as close to the stance's best next coarse cell) buys both back. It is
 * still not the default: on the baseline walks the extra frontier diversity feeds the
 * publication-refusal grind, and two of twelve walks pay for the others' gains with
 * tape restarts or collisions. Flip it after the grind is fixed, not before. [OFF]
 * keeps everything up to the per-key cap -- the full quality of a free search on the
 * corpus, a fifth more expansions, worse variance.
 */
enum class FrontierDomination { FULL, POSITION_AWARE, OFF }

data class ValueFieldSearchConfig(
    val maxExpansions: Int = 8000,
    val stallExpansions: Int = 3000,
    val maxTransitionFrames: Int = 40,
    val branchMarginTicks: Double = 4.0,
    val headingFanDegrees: List<Double> = listOf(0.0, -12.0, 12.0),
    val headingCommitFrames: Int = 12,
    val safePrefixFrames: Int = 20,
    val safePrefixDelayMillis: Long = 250,
    val horizonCommitFrames: Int = 20,
    val horizonRunwayFrames: Int = 30,
    val localHorizonFrames: Int = 0,
    val minCommitExpansions: Int = 0,
    val maxFinalCommitFrames: Int = 0,
    /**
     * How close to the solution's end the body must be before the session finalizes
     * and stops refining. This used to share [maxFinalCommitFrames] (40), which froze
     * the last two seconds of every walk at whatever quality the finalize moment had;
     * the fork window makes staying alive cheap, so the freeze shrinks to the frames
     * an adoption round-trip genuinely needs.
     */
    val finalizeArrivalFrames: Int = 10,
    val chainLength: Int = 6,
    val finishValueTicks: Double = 11.0,
    val maxFinishSweeps: Int = 64,
    val frontierPerKey: Int = 3,
    /** When > 0, mirror the shipping beam at this per-key cap in shadow and mark what it would refuse. */
    val beamShadowPerKey: Int = 0,
    /**
     * Minimum fork life, in EXPANSIONS of remaining search time, an off-tape branch
     * needs to be worth deepening. A branch's whole subtree dies with its fork, so
     * rollouts spent on a nearly-foreclosed branch are wasted unless the line is
     * committed first -- and the commit gates refuse nearly all of them.
     *
     * Denominated in the search's own currency on purpose. Frames were tried first and
     * were wrong at production speed: 20 frames of fork life is 1,560 expansions at the
     * harness's 78 per frame -- the sweet spot that took the baseline down 2.3% and
     * killed the restart tails -- but 12,000 at the field's ~600 per frame, where the
     * same setting starved live alternatives on parkour and cost 7 frames a run. The
     * session measures its own expansions-per-body-frame and converts. 0 disables.
     */
    val branchExpansionHeadroomExpansions: Int = 1560,
    /**
     * Offer solver-derived momentum jumps across cells the route walks in the SEARCH
     * vocabulary; see JumpMovement.proposals. Off by default after a five-variant gate
     * sweep: every clause set that admits the parkour wins (courses -7 to -15) also
     * re-admits open-terrain damage (bedrock-05 +20 with stalls, or -08 short). The
     * improver gets these proposals regardless -- it validates every candidate through
     * recertify and score, so there the noise can only spend budget, never misroute
     * the walk.
     */
    val momentumSkips: Boolean = false,
    /**
     * Offer the chained sprint-jump gait in the SEARCH vocabulary; see
     * JumpMovement.gaitHop. The gait itself is real -- the simulator sustains 0.3506
     * b/t jumping every landing against sprint's 0.2797 -- but where it beats ground
     * turned out untestable by static chain predicates: flat-or-down targets win pads
     * and lose the traverse, rise-tolerant targets the reverse, and the field-tempo
     * probe pays either way. Off until the guide field itself knows about velocity;
     * the improver gets it regardless, where recertify validates every candidate.
     */
    val momentumGait: Boolean = false,
    /**
     * Weight on the guide term of the frontier order (weighted A*). 1.0 is the
     * neutral best-first order, under which two thirds of all expansions were
     * measured within twenty frames of the published tip -- breadth churn that
     * starves the depth the endgame needs. Above 1, deeper anchors win the ties.
     * Ordering only; pruning bounds never read it.
     */
    val guideWeight: Double = 1.0,
    /**
     * Queue-order credit, in ticks, for anchors descending from the published tip.
     * Uniform depth-greed (guideWeight) was measured useless because near-duplicates
     * exist at every depth; this is the selective form -- the tape line's own
     * continuation is the depth the endgame starves for (67% of expansions were
     * measured within twenty frames of the tip while finishes arrived too late).
     * Ordering only. 0 disables.
     */
    val tipLineCreditTicks: Double = 0.0,
    /**
     * Every this-many expansions, one poll takes the DEEPEST open entry instead of
     * the best-ordered one: a depth lane scheduled outside the order, because six
     * measured attempts prove re-ranking the order always trades tails. The lane
     * serves the late-discovery stall (finishes sealing only when the body has
     * already consumed the tape) without biasing what gets committed. 0 disables.
     */
    val depthLaneInterval: Int = 0,
    /**
     * Ticks of queue-side price added to an action each time a same-bucket peer's
     * rollout of it produced a beam-merged child. Outcome-informed discovery: the
     * guides cannot know which actions keep re-deriving held states, but the beam
     * sees every merge -- and unlike the measured hard-skip memo (which lost the
     * actions near-duplicate states genuinely needed), a surcharge only delays.
     * 0 disables.
     */
    val mergeSurchargeTicks: Double = 0.0,
    val frontierDomination: FrontierDomination = FrontierDomination.FULL,
    val speedBucketBlocks: Double = 0.075,
    val yawBucketDegrees: Double = 20.0,
    val maxTemperature: Double = 1.0,
    /** Rollouts a solved plan may spend having its worst spans shortcut. 0 disables it. */
    val improvementBudget: Int = 0,
) {
    init {
        require(maxExpansions > 0)
        require(stallExpansions > 0)
        require(maxTransitionFrames > 0)
        require(chainLength > 0)
        require(safePrefixFrames > 0)
        require(safePrefixDelayMillis >= 0)
        require(horizonCommitFrames > 0)
        require(horizonRunwayFrames >= 0)
        require(headingFanDegrees.all { it.isFinite() })
        require(headingCommitFrames > 0)
        require(finishValueTicks >= 0.0)
        require(maxFinishSweeps >= 0)
        require(frontierPerKey > 0)
        require(beamShadowPerKey >= 0)
        require(branchExpansionHeadroomExpansions >= 0)
        require(speedBucketBlocks > 0.0)
        require(yawBucketDegrees > 0.0)
        require(maxTemperature > 0.0 && maxTemperature <= 1.0)
        require(improvementBudget >= 0)
    }
}

sealed interface WorldSyncResult {

    data object Quiet : WorldSyncResult

    data object Woken : WorldSyncResult

    data class Changed(val route: CoarseRoutePlan?) : WorldSyncResult
}
