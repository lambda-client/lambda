package com.lambda.pathing.session

import com.lambda.pathing.search.PublishedPath

sealed interface PathPlanResult {
	data class Planned(val path: PublishedPath) : PathPlanResult
	data class Failed(val reason: String, val mobs: Boolean = false) : PathPlanResult
	data object Cancelled : PathPlanResult
}
