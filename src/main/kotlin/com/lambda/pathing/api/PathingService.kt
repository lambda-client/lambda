package com.lambda.pathing.api

import com.lambda.config.automation.AutomationConfig
import com.lambda.config.blocks.PathingRenderConfig
import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.pathing.find.AccessStances
import com.lambda.pathing.find.AccessStances.liveView
import com.lambda.pathing.find.BlockFinder
import com.lambda.pathing.find.EntityFollower
import net.minecraft.entity.Entity
import com.lambda.pathing.session.PathingManager
import com.lambda.pathing.session.PathingRequest
import com.lambda.pathing.core.Stance
import com.lambda.pathing.session.PathingSession.State
import com.lambda.pathing.session.Telemetry
import com.lambda.pathing.world.streaming.PathingWorld
import net.minecraft.block.pattern.CachedBlockPosition
import net.minecraft.util.math.BlockPos
import java.util.function.Predicate

object PathingService {

	fun route(automated: Automated, waypoints: List<Stance>): PathingRequest? {
		BlockFinder.cancel()
		EntityFollower.cancel()
		return PathingManager.route(automated, waypoints)
	}

	fun SafeContext.walkTo(block: BlockPos): Stance? {
		val stance = AccessStances.clicked(liveView(block), block, player.pos) ?: return null
		route(AutomationConfig.DEFAULT, listOf(stance))
		return stance
	}

	fun rehearse(automated: Automated, waypoints: List<Stance>): PathingRequest? {
		BlockFinder.cancel()
		EntityFollower.cancel()
		return PathingManager.rehearse(automated, waypoints)
	}

	fun find(description: String, predicate: Predicate<CachedBlockPosition>, follow: Boolean = false) {
		EntityFollower.cancel()
		BlockFinder.find(description, predicate, follow)
	}

	fun follow(description: String, select: SafeContext.() -> Entity?) {
		BlockFinder.cancel()
		EntityFollower.follow(description, select)
	}

	val findStatus: String get() = EntityFollower.status() ?: BlockFinder.status()

	fun cancel() {
		EntityFollower.cancel()
		BlockFinder.cancel()
		PathingManager.cancel()
	}

	fun clear() {
		EntityFollower.cancel()
		BlockFinder.cancel()
		PathingManager.clear()
	}

	val status: State get() = PathingManager.status

	val telemetry: Telemetry get() = PathingManager.telemetry

	val isRouting: Boolean get() = PathingManager.isRouting

	val renderConfig: PathingRenderConfig get() = PathingManager.renderConfig

	val world: PathingWorld? get() = PathingManager.world
}
