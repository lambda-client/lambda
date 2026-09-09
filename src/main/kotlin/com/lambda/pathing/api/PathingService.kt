package com.lambda.pathing.api

import com.lambda.config.blocks.PathingRenderConfig
import com.lambda.context.Automated
import com.lambda.pathing.PathingManager
import com.lambda.pathing.PathingRequest
import com.lambda.pathing.core.Stance
import com.lambda.pathing.session.PathingSession.State
import com.lambda.pathing.session.Telemetry

object PathingService {

	fun route(automated: Automated, waypoints: List<Stance>): PathingRequest? =
		PathingManager.route(automated, waypoints)

	fun cancel() = PathingManager.cancel()

	fun clear() = PathingManager.clear()

	val status: State get() = PathingManager.status

	val telemetry: Telemetry get() = PathingManager.telemetry

	val isRouting: Boolean get() = PathingManager.isRouting

	val renderConfig: PathingRenderConfig get() = PathingManager.renderConfig

	fun diagnostics(): String = PathingManager.diagnostics()
}
