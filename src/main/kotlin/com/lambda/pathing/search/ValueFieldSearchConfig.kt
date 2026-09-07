package com.lambda.pathing.search

import com.lambda.pathing.coarse.CoarseRoutePlan

/**
 * How the beam decides one same-bucket anchor makes another redundant. [FULL]: earlier,
 * at least as fast, no more collisions or input switches. [POSITION_AWARE] additionally
 * requires the dominator to stand at least as close to the next coarse cell. [OFF] keeps
 * everything up to the per-key cap. Default and measurements: docs/decisions/beam.md.
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
     * Frames from the solution's end within which the session finalizes and stops
     * refining: the length of an adoption round-trip. See docs/decisions/publication-protocol.md.
     */
    val finalizeArrivalFrames: Int = 10,
    val chainLength: Int = 6,
    val finishValueTicks: Double = 11.0,
    val maxFinishSweeps: Int = 64,
    val frontierPerKey: Int = 3,
    /**
     * Minimum fork life, in expansions of remaining search time, an off-tape branch needs
     * to be worth deepening; the session converts via its measured expansions per body
     * frame. 0 disables. Denominated in expansions, never frames: docs/decisions/tempo-law.md.
     */
    val branchExpansionHeadroomExpansions: Int = 1560,
    /**
     * Offer solver-derived momentum jumps across route cells in the search vocabulary
     * (see MomentumProposals). The improver always gets them.
     * Default evidence: docs/decisions/movement-tuning.md.
     */
    val momentumSkips: Boolean = false,
    /**
     * Offer the chained sprint-jump gait in the search vocabulary (see
     * MomentumProposals.gaitHop). The improver always gets it. Default evidence:
     * docs/decisions/movement-tuning.md.
     */
    val momentumGait: Boolean = false,
    val frontierDomination: FrontierDomination = FrontierDomination.FULL,
    val speedBucketBlocks: Double = 0.075,
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
        require(branchExpansionHeadroomExpansions >= 0)
        require(speedBucketBlocks > 0.0)
        require(maxTemperature > 0.0 && maxTemperature <= 1.0)
        require(improvementBudget >= 0)
    }
}

sealed interface WorldSyncResult {

    /** Sections whose known content changed (re-captured or reloaded), for tape repair. */
    val mutations: Set<com.lambda.pathing.core.PathingSection> get() = emptySet()

    data object Quiet : WorldSyncResult

    data class Woken(
        override val mutations: Set<com.lambda.pathing.core.PathingSection> = emptySet(),
    ) : WorldSyncResult

    data class Changed(
        val route: CoarseRoutePlan?,
        override val mutations: Set<com.lambda.pathing.core.PathingSection> = emptySet(),
    ) : WorldSyncResult
}
