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
    val anchor: ValueAnchor? = null,
) {
    val score: Int get() = frames + ValueFieldAnchorSearch.COLLISION_FRAME_PENALTY * collisionEvents
}
