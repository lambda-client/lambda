package com.lambda.pathing.search

import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.Footing
import com.lambda.pathing.world.center
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment

internal class RoutePoints(private val environment: SnapshotSimulationEnvironment) {
	private val cache = object : LinkedHashMap<List<Stance>, List<HorizontalPoint>>(256, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<List<Stance>, List<HorizontalPoint>>) = size > CAPACITY
	}
	private var revision: Any? = environment.revision

	fun of(chain: List<Stance>): List<HorizontalPoint> {
		if (chain.none { partial(it) }) return chain.map { it.center(environment) }
		synchronized(cache) {
			val current = environment.revision
			if (current !== revision) {
				cache.clear()
				revision = current
			}
			cache[chain]?.let { return it }
		}
		val points = route(chain)
		synchronized(cache) { cache[chain] = points }
		return points
	}

	private fun route(chain: List<Stance>): List<HorizontalPoint> {
		val points = ArrayList<HorizontalPoint>(chain.size)
		var previous = chain.first().center(environment)
		points += previous
		for (i in 1 until chain.size) {
			val stance = chain[i]
			val path = if (partial(chain[i - 1]) || partial(stance)) {
				Footing.route(environment, previous, stance, if (stance.y > chain[i - 1].y) Footing.JUMP_RISE else Footing.WALK_RISE)
			} else null
			if (path != null && path.size >= 2) {
				points += path.subList(1, path.size)
				previous = path.last()
			} else {
				previous = stance.center(environment)
				points += previous
			}
		}
		return points
	}

	private fun partial(stance: Stance): Boolean = Footing.involvesPartial(environment, stance.x, stance.y, stance.z)

	private companion object {
		const val CAPACITY = 4096
	}
}
