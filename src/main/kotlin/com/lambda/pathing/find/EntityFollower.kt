package com.lambda.pathing.find

import com.lambda.config.automation.AutomationConfig
import com.lambda.context.SafeContext
import com.lambda.core.Loadable
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.pathing.core.Stance
import com.lambda.pathing.session.PathingManager
import com.lambda.pathing.session.PathingRequest
import com.lambda.pathing.session.PathingSession.Companion.PATHING_SOURCE
import com.lambda.pathing.session.PathingSession.State
import com.lambda.util.CommunicationUtils.info
import net.minecraft.entity.Entity

object EntityFollower : Loadable {
	private class Follow(val description: String, val select: SafeContext.() -> Entity?) {
		var request: PathingRequest? = null
		var goal: Stance? = null
		var routedAt = 0
		var missingTicks = 0
	}

	private var follow: Follow? = null

	fun follow(description: String, select: SafeContext.() -> Entity?) {
		cancel()
		follow = Follow(description, select)
	}

	fun cancel() {
		val current = follow ?: return
		follow = null
		if (current.request != null) PathingManager.cancel()
	}

	fun status(): String? = follow?.let { "Following ${it.description}." }

	init {
		listen<TickEvent.Pre> {
			val current = follow ?: return@listen
			val target = current.select(this)
			if (target == null) {
				if (++current.missingTicks >= MISSING_TICKS) {
					info("Lost sight of ${current.description}; stopped following.", PATHING_SOURCE)
					cancel()
				}
				return@listen
			}
			current.missingTicks = 0
			if (player.squaredDistanceTo(target.pos) <= CLOSE_ENOUGH * CLOSE_ENOUGH) return@listen
			val goal = Stance.of(target.pos, target.isOnGround)
			val state = current.request?.let(PathingManager::stateOf)
			val walking = state != null && state !is State.Complete && state !is State.Failed && state != State.Idle
			val moved = current.goal?.let { distanceSquared(it, goal) > REROUTE_DISTANCE * REROUTE_DISTANCE } ?: true
			if (walking && !moved) return@listen
			val request = current.request
			if (walking && request != null) {
				if (player.age - current.routedAt < RETARGET_TICKS) return@listen
				current.routedAt = player.age
				if (with(PathingManager) { retarget(request, goal) }) {
					current.goal = goal
					return@listen
				}
			}
			if (player.age - current.routedAt < REROUTE_TICKS && request != null) return@listen
			current.goal = goal
			current.routedAt = player.age
			current.request = PathingManager.route(AutomationConfig.DEFAULT, listOf(goal))
		}
		listenUnsafe<ConnectionEvent.Disconnect> { follow = null }
	}

	private fun distanceSquared(a: Stance, b: Stance): Int {
		val dx = a.x - b.x
		val dy = a.y - b.y
		val dz = a.z - b.z
		return dx * dx + dy * dy + dz * dz
	}

	private const val CLOSE_ENOUGH = 2.5
	private const val REROUTE_DISTANCE = 3
	private const val REROUTE_TICKS = 20
	private const val RETARGET_TICKS = 5
	private const val MISSING_TICKS = 100
}
