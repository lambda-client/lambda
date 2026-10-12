package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.core.headingAlignment
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import kotlin.math.floor

class CoarseValueField(
	override val view: CoarseVoxelView,
	private val moves: SimpleMoveLibrary,
	val goal: Stance,
	private val labelAt: (Stance, SpeedClass) -> Double,
	private val edgeProvider: (Stance) -> List<CoarseEdge>,
	private val labelRevision: () -> Long,
) : ValueField {
	private val chains = HashMap<ChainKey, List<Stance>>()
	private var chainsRevision = labelRevision()

	private class Options(val edges: List<CoarseEdge>, val costs: DoubleArray)

	private val options = HashMap<Stance, Options>()
	private var optionsRevision = labelRevision()

	override fun invalidate() {
		chains.clear()
		options.clear()
	}

	override fun lowerBound(stance: Stance): Double = moves.heuristic(stance, goal)

	override fun guide(stance: Stance): Double =
		guideVia(stance) { minOf(labelAt(it, SpeedClass.MOVING), labelAt(it, SpeedClass.STOPPED)) }

	override fun guide(stance: Stance, speed: SpeedClass): Double = guideVia(stance) { labelAt(it, speed) }

	private inline fun guideVia(stance: Stance, labelOf: (Stance) -> Double): Double {
		val direct = labelOf(stance)
		if (direct.isFinite()) return direct
		var best = Double.POSITIVE_INFINITY
		for ((_, _, to, _, lowerBoundTicks) in edgesFrom(stance)) {
			val neighbour = labelOf(to)
			if (neighbour.isFinite()) best = minOf(best, lowerBoundTicks + neighbour)
		}
		return best
	}

	override fun isMapped(stance: Stance): Boolean = guide(stance).isFinite()

	fun edgesFrom(stance: Stance): List<CoarseEdge> = edgeProvider(stance)

	override fun isStance(stance: Stance): Boolean = moves.isStance(view, stance)

	override fun steps(stance: Stance, count: Int, marginTicks: Double): List<CoarseEdge> {
		val options = optionsFrom(stance)
		if (options.edges.isEmpty()) return emptyList()
		val reference = towardGoal(stance)
		val ranked = ArrayList<RankedStep>(options.edges.size)
		for (index in options.edges.indices) {
			val edge = options.edges[index]
			val cost = options.costs[index]
			ranked += RankedStep(edge, cost, floor(cost / TIE_TICKS), alignment(stance, edge.to, reference))
		}
		val best = ranked.minOf { it.cost }
		val ordered = if (count == 1) listOfNotNull(ranked.minOrNull()) else ranked.sorted()
		return ordered.asSequence().takeWhile { it.cost <= best + marginTicks }.take(count).map { it.edge }.toList()
	}

	private fun optionsFrom(stance: Stance): Options {
		val revision = labelRevision()
		if (revision != optionsRevision) {
			options.clear()
			optionsRevision = revision
		}
		options[stance]?.let { return it }
		val byDestination = HashMap<Stance, CoarseEdge>()
		for (edge in edgesFrom(stance)) {
			val incumbent = byDestination[edge.to]
			if (incumbent == null || edge.lowerBoundTicks < incumbent.lowerBoundTicks) {
				byDestination[edge.to] = edge
			}
		}
		val edges = ArrayList<CoarseEdge>(byDestination.size)
		val costs = DoubleArray(byDestination.size)
		for (edge in byDestination.values) {
			val guide = guide(edge.to)
			if (!guide.isFinite()) continue
			costs[edges.size] = edge.lowerBoundTicks + guide
			edges += edge
		}
		if (options.size >= MAX_OPTIONS) options.clear()
		return Options(edges, costs).also { options[stance] = it }
	}

	private class RankedStep(val edge: CoarseEdge, val cost: Double, val tier: Double, val alignment: Double) :
		Comparable<RankedStep> {
		override fun compareTo(other: RankedStep): Int {
			val byTier = tier.compareTo(other.tier)
			if (byTier != 0) return byTier
			return compareDirections(alignment, edge.to, other.alignment, other.edge.to)
		}
	}

	override fun chain(stance: Stance, firstStep: Stance?, length: Int): List<Stance> {
		require(length > 0) { "A steering chain needs at least one node" }
		val revision = labelRevision()
		if (revision != chainsRevision) {
			chains.clear()
			chainsRevision = revision
		}
		val key = ChainKey(stance, firstStep, length)
		chains[key]?.let { return it }
		val chain = ArrayList<Stance>(length + 1)
		chain += stance
		val visited = HashSet<Stance>()
		visited += stance

		firstStep?.let {
			chain += it
			visited += it
		}

		while (chain.size <= length && chain.last() != goal) {
			val next = descend(chain.last(), visited) ?: break
			chain += next
			visited += next
		}
		if (chains.size >= MAX_CHAINS) chains.clear()
		chains[key] = chain
		return chain
	}

	private data class ChainKey(val stance: Stance, val firstStep: Stance?, val length: Int)

	private fun descend(from: Stance, visited: Set<Stance>): Stance? {
		val options = optionsFrom(from)
		val edges = options.edges
		var best = Double.NaN
		for (index in edges.indices) {
			if (edges[index].to in visited) continue
			val cost = options.costs[index]
			best = if (best.isNaN()) cost else minOf(best, cost)
		}
		if (best.isNaN()) return null

		val reference = towardGoal(from)
		var chosen: Stance? = null
		var chosenAlignment = 0.0
		for (index in edges.indices) {
			val to = edges[index].to
			if (to in visited || !(options.costs[index] <= best + TIE_TICKS)) continue
			val alignment = alignment(from, to, reference)
			if (chosen == null || compareDirections(alignment, to, chosenAlignment, chosen) < 0) {
				chosen = to
				chosenAlignment = alignment
			}
		}
		return chosen
	}

	private fun towardGoal(from: Stance): Pair<Double, Double> = Pair((goal.x - from.x).toDouble(), (goal.z - from.z).toDouble())

	private fun alignment(from: Stance, to: Stance, reference: Pair<Double, Double>): Double =
		headingAlignment(reference.first, reference.second, (to.x - from.x).toDouble(), (to.z - from.z).toDouble())

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
		const val MAX_CHAINS = 4096
		const val MAX_OPTIONS = 16384
	}
}
