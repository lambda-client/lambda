/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.util.player.prediction.MovementSimulationInput

internal class Solution(
    val inputs: List<MovementSimulationInput>,
    val boundaries: List<Int>,
    val segments: Int,
    val launchMargin: Int,
    val parameters: TerminalApproach,
    val frames: Int,
    val collisionEvents: Int,
    /** The anchor this grew from; how the endgame knows which line to commit along. */
    val anchor: ValueAnchor? = null,
) {
    /**
     * Winner selection currency: arrival time plus a toll per contact.
     *
     * Ranking on frames alone lets a tape that scrapes a wall and saves one tick beat
     * a clean one, which is the "it takes a collision instead of the better jump"
     * report. The toll was established at four frames per bump by the staircase work
     * (it cut a five-riser climb from ten bumps to four) and was lost when the search
     * was extracted out of the legacy sweep. It is a preference among *certified*
     * tapes only, never a safety gate: a necessary scrape — a rising jump grazing the
     * lip it clears — still wins when nothing cleaner certifies.
     */
    val score: Int get() = frames + ValueFieldAnchorSearch.COLLISION_FRAME_PENALTY * collisionEvents
}
