package com.lambda.pathing.session

import com.lambda.pathing.search.PublishedPath

sealed interface PlanningFailure {
	val message: String

	data class InvalidRequest(override val message: String) : PlanningFailure
	data class ResourceLimit(override val message: String) : PlanningFailure
	data class NoRoute(override val message: String) : PlanningFailure

	data class NoCertifiedMotion(override val message: String) : PlanningFailure
}

sealed interface PathPlanResult {
	data class Planned(val path: PublishedPath) : PathPlanResult
	data class Failed(val failure: PlanningFailure) : PathPlanResult
	data object Cancelled : PathPlanResult
}
