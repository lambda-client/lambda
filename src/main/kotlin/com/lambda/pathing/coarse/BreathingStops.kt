package com.lambda.pathing.coarse

import com.lambda.pathing.actions.families.DiveMovement
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.world.CoarseVoxelView
import java.util.PriorityQueue
import kotlin.math.ceil
import kotlin.math.sqrt

internal object BreathingStops {
	fun plan(
		view: CoarseVoxelView,
		moves: SimpleMoveLibrary,
		route: List<Stance>,
		startAir: Int,
		hopeless: MutableSet<Pair<Stance, Stance>> = HashSet(),
	): List<Stance> {
		if (route.size < 2) return emptyList()
		var last = 0
		val reserve = moves.catalog.options.airReserve
		val usableAir = moves.usableAir
		var budget = if (!underwater(view, route[0])) usableAir else if (startAir >= reserve) startAir - reserve else startAir
		var spent = 0
		for (index in 1 until route.size) {
			val node = route[index]
			if (!underwater(view, node)) {
				last = index
				budget = usableAir
				spent = 0
				continue
			}
			spent += airTicks(route[index - 1], node)
			if (spent <= budget) continue
			val exit = (index + 1 until route.size).firstOrNull { !underwater(view, route[it]) } ?: route.lastIndex
			val stretch = route[last] to route[exit]
			if (stretch in hopeless) return emptyList()
			return search(view, moves, route.subList(last, exit + 1), budget).ifEmpty { hopeless += stretch; emptyList() }
		}
		return emptyList()
	}

	fun underwater(view: CoarseVoxelView, stance: Stance): Boolean =
		DiveMovement.submerged(view, stance.x, stance.y, stance.z) && !DiveMovement.breathable(view, stance.x, stance.y, stance.z)

	private class Label(val stance: Stance, val cost: Double, val air: Int, val parent: Label?) {
		var dominated = false
	}

	private fun search(view: CoarseVoxelView, moves: SimpleMoveLibrary, stretch: List<Stance>, firstBudget: Int): List<Stance> {
		val usableAir = moves.usableAir
		val start = stretch.first()
		val goal = stretch.last()
		val minX = stretch.minOf { it.x } - REACH
		val maxX = stretch.maxOf { it.x } + REACH
		val minY = stretch.minOf { it.y } - REACH
		val maxY = stretch.maxOf { it.y } + REACH
		val minZ = stretch.minOf { it.z } - REACH
		val maxZ = stretch.maxOf { it.z } + REACH
		val labels = HashMap<Stance, ArrayList<Label>>()
		val cache = CoarseEdgeCache(view.cursor(), moves)
		val queue = PriorityQueue<Label>(compareBy { it.cost })
		fun offer(label: Label) {
			val existing = labels.getOrPut(label.stance) { ArrayList(2) }
			if (existing.any { it.cost <= label.cost && it.air >= label.air }) return
			existing.removeAll { (it.cost >= label.cost && it.air <= label.air).also { dominated -> if (dominated) it.dominated = true } }
			existing += label
			queue += label
		}
		offer(Label(start, 0.0, firstBudget, null))
		var expansions = 0
		var reached: Label? = null
		while (queue.isNotEmpty() && expansions++ < MAX_EXPANSIONS) {
			val label = queue.poll()
			if (label.dominated) continue
			if (label.stance == goal) {
				reached = label
				break
			}
			val wet = underwater(view, label.stance)
			for (edge in cache.edgesFrom(label.stance)) {
				if (edge.movement !in MovementId.AQUATIC) continue
				val next = edge.to
				if (next != goal && (next.x !in minX..maxX || next.y !in minY..maxY || next.z !in minZ..maxZ)) continue
				val air = if (underwater(view, next)) label.air - airTicks(label.stance, next) else usableAir
				if (air < 0) continue
				val refill = if (wet && air == usableAir) (usableAir - label.air).toDouble() / Vanilla.AIR_REFILL_PER_TICK else 0.0
				offer(Label(next, label.cost + edge.lowerBoundTicks + refill, air, label))
			}
		}
		var label = reached ?: return emptyList()
		val path = ArrayList<Label>()
		while (true) {
			path += label
			label = label.parent ?: break
		}
		path.reverse()
		val targets = ArrayList<Stance>()
		for (index in 1 until path.lastIndex) {
			if (underwater(view, path[index - 1].stance) && !underwater(view, path[index].stance)) targets += path[index].stance
		}
		if (targets.isEmpty()) return emptyList()
		targets += goal
		return targets
	}

	private fun airTicks(from: Stance, to: Stance): Int {
		val dx = (to.x - from.x).toDouble()
		val dy = (to.y - from.y).toDouble()
		val dz = (to.z - from.z).toDouble()
		return ceil(sqrt(dx * dx + dy * dy + dz * dz) * AIR_TICKS_PER_BLOCK).toInt()
	}

	private val SimpleMoveLibrary.usableAir: Int get() = Vanilla.MAX_AIR - catalog.options.airReserve

	private const val AIR_TICKS_PER_BLOCK = 7.0

	private const val REACH = 24

	private const val MAX_EXPANSIONS = 200_000
}
