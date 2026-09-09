package com.lambda.pathing

import com.lambda.context.Automated
import com.lambda.interaction.managers.Request
import com.lambda.pathing.core.Stance

class PathingRequest(
	automated: Automated,

	val goal: Stance,

	val waypoints: List<Stance> = emptyList(),
) : Request(), Automated by automated {
	override val requestId = requestCount++
	override val tickStageMask = PathingManager.openStages.toSet()

	override val nowOrNothing = false

	override val done: Boolean get() = PathingManager.isFinished(this)

	override fun submit(queueIfMismatchedStage: Boolean): PathingRequest =
		PathingManager.request(this, queueIfMismatchedStage)

	companion object {
		private var requestCount = 0
	}
}
