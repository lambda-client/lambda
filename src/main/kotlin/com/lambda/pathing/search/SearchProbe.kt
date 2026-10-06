package com.lambda.pathing.search

import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.rollout.TrajectoryRollout
import com.lambda.pathing.rollout.TrajectoryDiagnostic

import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import java.util.Collections
import java.util.IdentityHashMap
import net.minecraft.util.math.Vec3d

class CandidatePath(val points: List<Vec3d>, val best: Boolean)

enum class SearchNodeRole { SPINE, BEST, OPEN, PARKED, SPENT, INTERIOR }

class SearchTreeNode(val position: Vec3d, val role: SearchNodeRole)

class SearchTreeEdge(
	val from: Vec3d,
	val to: Vec3d,
	val role: SearchNodeRole,
	val trace: List<Vec3d>,
)

class SearchTreeView(
	val nodes: List<SearchTreeNode>,
	val edges: List<SearchTreeEdge>,
)

interface SearchProbe {
	val candidatesEnabled: Boolean get() = false

	val treeEnabled: Boolean get() = false

	fun tree(view: SearchTreeView) {}

	fun stats(view: SearchStats) {}

	fun expansion(from: Stance, action: TrajectoryDecision, diagnostic: TrajectoryDiagnostic?) {}

	fun attempt(rollout: TrajectoryRollout, certified: Boolean, diagnostic: TrajectoryDiagnostic?) {}

	fun blocked(
		frame: Int,
		section: PathingSection,
		capturable: Boolean,
		stance: Stance,
		movement: MovementId,
	) {
	}

	fun sync(sections: Int, mutations: Int, chunks: Int, routeAffected: Boolean, extending: Boolean) {}

	fun candidates(lines: List<CandidatePath>) {}

	companion object {
		val NONE: SearchProbe = object : SearchProbe {}
	}
}

internal fun searchTree(
	spent: List<ValueAnchor>,
	parked: List<Frontier.OpenEntry>,
	open: List<Frontier.OpenEntry>,
	best: ValueAnchor?,
	published: ValueAnchor?,
): SearchTreeView {
	val roles = HashMap<ValueAnchor, SearchNodeRole>()
	fun claim(anchor: ValueAnchor, role: SearchNodeRole) {
		val existing = roles[anchor]
		if (existing == null || role < existing) roles[anchor] = role
	}

	fun claimChain(leaf: ValueAnchor?, role: SearchNodeRole) {
		var node = leaf
		while (node != null && roles.size < MAX_TREE_NODES) {
			claim(node, role)
			node = node.parent
		}
	}

	spent.forEach { claim(it, SearchNodeRole.SPENT) }
	parked.forEach { claim(it.anchor, SearchNodeRole.PARKED) }
	open.forEach { claim(it.anchor, SearchNodeRole.OPEN) }
	roles.keys.toList().forEach { claimChain(it.parent, SearchNodeRole.INTERIOR) }
	claimChain(best, SearchNodeRole.BEST)
	claimChain(published, SearchNodeRole.SPINE)

	val nodes = ArrayList<SearchTreeNode>(roles.size)
	val edges = ArrayList<SearchTreeEdge>(roles.size)
	roles.forEach { (anchor, role) ->
		nodes += SearchTreeNode(anchor.state.position, role)
		val parent = anchor.parent ?: return@forEach
		if (parent !in roles) return@forEach
		edges += SearchTreeEdge(parent.state.position, anchor.state.position, role, anchor.trace)
	}
	return SearchTreeView(nodes, edges)
}

private const val MAX_TREE_NODES = 4096

internal fun candidatePaths(pool: List<Frontier.OpenEntry>, tip: ValueAnchor?): List<CandidatePath> {
	val best = pool.minByOrNull { it.order }
	val spine = Collections.newSetFromMap(IdentityHashMap<ValueAnchor, Boolean>())
	generateSequence(tip) { it.parent }.forEach { spine += it }
	return pool.sortedBy { it.order }.take(MAX_SHOWN_CANDIDATES).map { entry ->
		val lineage = generateSequence(entry.anchor) { it.parent }
		val branch = lineage.takeWhile { it !in spine }.toList().asReversed()
		val points = ArrayList<Vec3d>()
		lineage.firstOrNull { it in spine }?.let { points += it.state.position }
		branch.forEach { anchor ->
			points += anchor.trace
			points += anchor.state.position
		}
		CandidatePath(points, entry === best)
	}
}

private const val MAX_SHOWN_CANDIDATES = 12
