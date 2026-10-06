package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.core.headingAlignment
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import kotlin.math.floor
import kotlin.math.hypot
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap

class CoarseValueField(
	override val view: CoarseVoxelView,
	private val moves: SimpleMoveLibrary,
	val goal: Stance,
	private val labelAt: (Stance, SpeedClass) -> Double,
	private val edgeProvider: (Stance) -> List<CoarseEdge>,
) : ValueField {
	private val guides = Object2DoubleOpenHashMap<Stance>()
		.apply { defaultReturnValue(Double.NaN) }
	private val movingGuides = Object2DoubleOpenHashMap<Stance>()
		.apply { defaultReturnValue(Double.NaN) }
	private val stoppedGuides = Object2DoubleOpenHashMap<Stance>()
		.apply { defaultReturnValue(Double.NaN) }
	private val edges = HashMap<Stance, List<CoarseEdge>>()
	private val chains = HashMap<ChainKey, List<Stance>>()

	override fun invalidate(sections: Set<PathingSection>) {
		if (sections.isEmpty()) return
		chains.clear()
		val halo = HashSet<PathingSection>(sections.size * 27)
		for ((x, y, z) in sections) {
			for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
				halo += PathingSection(
					x + dx, y + dy, z + dz,
				)
			}
		}

		val inHalo = { stance: Stance -> PathingSection(stance.x shr 4, stance.y shr 4, stance.z shr 4) in halo }
		guides.keys.removeAll(inHalo)
		movingGuides.keys.removeAll(inHalo)
		stoppedGuides.keys.removeAll(inHalo)
		edges.keys.removeAll(inHalo)
	}

	override fun lowerBound(stance: Stance): Double = moves.heuristic(stance, goal)

	override fun guide(stance: Stance): Double =
		guideVia(guides, stance) { minOf(labelAt(it, SpeedClass.MOVING), labelAt(it, SpeedClass.STOPPED)) }

	override fun guide(stance: Stance, speed: SpeedClass): Double =
		guideVia(if (speed == SpeedClass.MOVING) movingGuides else stoppedGuides, stance) { labelAt(it, speed) }

	private inline fun guideVia(
		cache: Object2DoubleOpenHashMap<Stance>,
		stance: Stance,
		labelOf: (Stance) -> Double,
	): Double {
		val cached = cache.getDouble(stance)
		if (!cached.isNaN()) return cached

		val direct = labelOf(stance)
		val computed = if (direct.isFinite()) direct else {
			var best = Double.POSITIVE_INFINITY
			for ((_, _, to, _, lowerBoundTicks) in edgesFrom(stance)) {
				val neighbour = labelOf(to)
				if (neighbour.isFinite()) best = minOf(best, lowerBoundTicks + neighbour)
			}
			best
		}
		cache.put(stance, computed)
		return computed
	}

	override fun isMapped(stance: Stance): Boolean = guide(stance).isFinite()

	fun edgesFrom(stance: Stance): List<CoarseEdge> =
		edges.getOrPut(stance) { edgeProvider(stance) }

	override fun isStance(stance: Stance): Boolean = moves.isStance(view, stance)

	override fun steps(
		stance: Stance,
		count: Int,
		marginTicks: Double,
		heading: Pair<Double, Double>?,
	): List<CoarseEdge> {
		val byDestination = HashMap<Stance, CoarseEdge>()
		for (edge in edgesFrom(stance)) {
			val incumbent = byDestination[edge.to]
			if (incumbent == null || edge.lowerBoundTicks < incumbent.lowerBoundTicks) {
				byDestination[edge.to] = edge
			}
		}

		val reference = reference(stance, heading)
		val ranked = ArrayList<RankedStep>(byDestination.size)
		for (edge in byDestination.values) {
			val guide = guide(edge.to)
			if (!guide.isFinite()) continue
			val cost = edge.lowerBoundTicks + guide
			ranked += RankedStep(edge, cost, floor(cost / TIE_TICKS), alignment(stance, edge.to, reference))
		}
		if (ranked.isEmpty()) return emptyList()
		val best = ranked.minOf { it.cost }
		val ordered = if (count == 1) listOfNotNull(ranked.minOrNull()) else ranked.sorted()
		return ordered.asSequence().takeWhile { it.cost <= best + marginTicks }.take(count).map { it.edge }.toList()
	}

	private class RankedStep(val edge: CoarseEdge, val cost: Double, val tier: Double, val alignment: Double) :
		Comparable<RankedStep> {
		override fun compareTo(other: RankedStep): Int {
			val byTier = tier.compareTo(other.tier)
			if (byTier != 0) return byTier
			return compareDirections(alignment, edge.to, other.alignment, other.edge.to)
		}
	}

	override fun chain(
		stance: Stance,
		firstStep: Stance?,
		length: Int,
		heading: Pair<Double, Double>?,
	): List<Stance> {
		require(length > 0) { "A steering chain needs at least one node" }
		val key = ChainKey(stance, firstStep, length, heading)
		chains[key]?.let { return it }
		val chain = ArrayList<Stance>(length + 1)
		chain += stance
		val visited = HashSet<Stance>()
		visited += stance

		var direction = heading
		firstStep?.let {
			direction = (it.x - stance.x).toDouble() to (it.z - stance.z).toDouble()
			chain += it
			visited += it
		}

		while (chain.size <= length && chain.last() != goal) {
			val from = chain.last()
			val next = descend(from, visited, direction) ?: break
			direction = (next.x - from.x).toDouble() to (next.z - from.z).toDouble()
			chain += next
			visited += next
		}
		if (chains.size >= MAX_CHAINS) chains.clear()
		chains[key] = chain
		return chain
	}

	private data class ChainKey(
		val stance: Stance,
		val firstStep: Stance?,
		val length: Int,
		val heading: Pair<Double, Double>?,
	)

	private fun descend(
		from: Stance,
		visited: Set<Stance>,
		heading: Pair<Double, Double>?,
	): Stance? {
		val edges = edgesFrom(from)
		val costs = DoubleArray(edges.size) { index ->
			val to = edges[index].to
			if (to in visited) Double.NaN
			else guide(to).let { if (it.isFinite()) edges[index].lowerBoundTicks + it else Double.NaN }
		}
		var best = Double.NaN
		for (cost in costs) if (!cost.isNaN()) best = if (best.isNaN()) cost else minOf(best, cost)
		if (best.isNaN()) return null

		val reference = reference(from, heading)
		var chosen: Stance? = null
		var chosenAlignment = 0.0
		for (index in edges.indices) {
			val cost = costs[index]
			if (cost.isNaN() || !(cost <= best + TIE_TICKS)) continue
			val to = edges[index].to
			val alignment = alignment(from, to, reference)
			if (chosen == null || compareDirections(alignment, to, chosenAlignment, chosen) < 0) {
				chosen = to
				chosenAlignment = alignment
			}
		}
		return chosen
	}

	private fun reference(from: Stance, heading: Pair<Double, Double>?): Pair<Double, Double> {
		if (heading != null && hypot(heading.first, heading.second) > MIN_HEADING_LENGTH) return heading
		return Pair((goal.x - from.x).toDouble(), (goal.z - from.z).toDouble())
	}

	private fun alignment(from: Stance, to: Stance, heading: Pair<Double, Double>): Double =
		headingAlignment(heading.first, heading.second, (to.x - from.x).toDouble(), (to.z - from.z).toDouble())

	override fun reachesGoal(chain: List<Stance>): Boolean = chain.lastOrNull() == goal

	private companion object {
		fun compareDirections(alignment: Double, to: Stance, otherAlignment: Double, other: Stance): Int {
			val byAlignment = otherAlignment.compareTo(alignment)
			if (byAlignment != 0) return byAlignment
			if (to.y != other.y) return to.y.compareTo(other.y)
			if (to.x != other.x) return to.x.compareTo(other.x)
			return to.z.compareTo(other.z)
		}

		const val TIE_TICKS = 0.5
		const val MIN_HEADING_LENGTH = 1e-6
		const val MAX_CHAINS = 4096
	}
}
