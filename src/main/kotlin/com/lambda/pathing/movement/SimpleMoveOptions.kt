package com.lambda.pathing.movement

data class SimpleMoveOptions(
    val allowDiagonal: Boolean = true,
    val allowStepUp: Boolean = true,
    val maxWalkOffDepth: Int = 3,

    val maxDropSpan: Int = 2,

    val allowClimbing: Boolean = false,
    val allowJumpCandidates: Boolean = true,
    val maxJumpSpan: Int = 5,
    val maxJumpDrop: Int = 1,

    val allowOffAxisJumps: Boolean = true,

    /**
     * Offer descending jump templates beyond the flat standing reach: air gap 4.0
     * with at least half a block of real drop, 4.24 with a full block (measured,
     * FenceJumpTest's drop-reach matrix). Off by default: on the corpus the wider
     * fan trades a ~1% frame gain for stalls on three routes and two wall brushes,
     * and chains through the wide drops re-certify worse.
     */
    val allowDeepDropJumps: Boolean = false,

    val allowSlimeBounces: Boolean = false,
    val maxBounceDrop: Int = 8,
) {
    init {
        require(maxWalkOffDepth >= 0) { "maxWalkOffDepth must be non-negative" }
        require(maxDropSpan >= 1) { "maxDropSpan must reach at least the adjacent stance" }
        require(maxJumpSpan >= 2) { "maxJumpSpan must reach past the adjacent stance" }
        require(maxBounceDrop >= 3) { "a bounce needs a fall deep enough to rebound from" }
        require(maxJumpDrop >= 0) { "maxJumpDrop must be non-negative" }
    }
}
