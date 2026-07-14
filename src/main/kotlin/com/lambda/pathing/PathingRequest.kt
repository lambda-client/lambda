/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing

import com.lambda.context.Automated
import com.lambda.interaction.managers.Request
import com.lambda.pathing.coarse.Stance

/**
 * A request to walk to [goal].
 *
 * The requester supplies the automation config, so its own rotation mode and turn
 * speed decide how the walk is steered -- the manager never reaches around it to
 * write rotations directly.
 */
class PathingRequest(
    automated: Automated,
    val goal: Stance,
) : Request(), Automated by automated {
    override val requestId = requestCount++
    override val tickStageMask = PathingManager.openStages.toSet()

    /**
     * A walk is a task, not a per-tick nudge like a rotation. Commands and scripts
     * submit from outside any open tick stage, so the request must queue until one
     * opens rather than being dropped on the floor.
     */
    override val nowOrNothing = false

    override val done: Boolean get() = PathingManager.isFinished(this)

    override fun submit(queueIfMismatchedStage: Boolean): PathingRequest =
        PathingManager.request(this, queueIfMismatchedStage)

    companion object {
        private var requestCount = 0
    }
}
