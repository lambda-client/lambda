package com.lambda.pathing.api

import com.lambda.config.blocks.PathingRenderConfig
import com.lambda.context.Automated
import com.lambda.pathing.find.BlockFinder
import com.lambda.pathing.session.PathingManager
import com.lambda.pathing.session.PathingRequest
import com.lambda.pathing.core.Stance
import com.lambda.pathing.session.PathingSession.State
import com.lambda.pathing.session.Telemetry
import com.lambda.pathing.world.streaming.PathingWorld
import net.minecraft.block.pattern.CachedBlockPosition
import java.util.function.Predicate

object PathingService {

	fun route(automated: Automated, waypoints: List<Stance>): PathingRequest? {
		BlockFinder.cancel()
		return PathingManager.route(automated, waypoints)
	}

	fun rehearse(automated: Automated, waypoints: List<Stance>): PathingRequest? {
		BlockFinder.cancel()
		return PathingManager.rehearse(automated, waypoints)
	}

	fun find(description: String, predicate: Predicate<CachedBlockPosition>) = BlockFinder.find(description, predicate)

	val findStatus: String get() = BlockFinder.status()

	fun cancel() {
		BlockFinder.cancel()
		PathingManager.cancel()
	}

	fun clear() {
		BlockFinder.cancel()
		PathingManager.clear()
	}

	val status: State get() = PathingManager.status

	val telemetry: Telemetry get() = PathingManager.telemetry

	val isRouting: Boolean get() = PathingManager.isRouting

	val renderConfig: PathingRenderConfig get() = PathingManager.renderConfig

	val world: PathingWorld? get() = PathingManager.world
}
