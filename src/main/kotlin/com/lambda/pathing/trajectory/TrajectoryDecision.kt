/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.Stance

sealed interface TrajectoryDecision {
    val sprint: Boolean

    val step: Stance?

    data class Walk(
        override val sprint: Boolean,
        override val step: Stance?,
        val lookAheadNodes: Int,
        val easeTurns: Boolean,
    ) : TrajectoryDecision

    data class Launch(
        override val sprint: Boolean,
        override val step: Stance?,
        val delayFrames: Int,
    ) : TrajectoryDecision

    data class Heading(
        override val sprint: Boolean,
        override val step: Stance?,
        val yaw: Double,
        val delayFrames: Int?,
        val keys: MovementKeys = MovementKeys.FORWARD,
        val airborneKeys: MovementKeys = keys,
    ) : TrajectoryDecision
}
