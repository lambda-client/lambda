package com.lambda.pathing.search

/**
 * Counters the session keeps only to report: they feed [SearchExhaustion] and the probes
 * and gate nothing. Anything a loop decision reads stays on the session.
 */
internal class SearchStats {
    /** Which exit the loop took. */
    var exit = "budget"

    /** Polled branches whose fork the cursor had already passed. */
    var adoptableDrops = 0

    /** Walk-through waypoints passed by re-targeting the running search. */
    var legSwitches = 0

    /** Polled branches moved to the starved reserve for lack of expansion headroom. */
    var forkStarvedDrops = 0

    var improvementSplices = 0
    var improvementRollouts = 0
    var improvementSaved = 0
    var improvementDiagnosis = ""
}
