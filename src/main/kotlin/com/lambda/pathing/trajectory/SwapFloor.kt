package com.lambda.pathing.trajectory

/**
 * The swap floor: a candidate tape displaces the running one only when it arrives at
 * least this many ticks earlier. One value for the publication gate
 * (HorizonController), the session's off-tape retention bar (AnchorSearchSession) and
 * the executor's adoption check (ImprovementArbiter), so the three can never disagree.
 * Decision record: docs/decisions/swap-floor.md.
 */
internal const val SWAP_FLOOR_GAIN_TICKS = 3.0
