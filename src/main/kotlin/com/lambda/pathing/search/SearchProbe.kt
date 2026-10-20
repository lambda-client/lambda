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
		loadable: Boolean,
		stance: Stance,
		movement: MovementId,
	) {
	}

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
			if (role == SearchNodeRole.INTERIOR && node in roles) return
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
	val shown = ArrayList<Frontier.OpenEntry>(MAX_SHOWN_CANDIDATES + 1)
	for (entry in pool) {
		var at = shown.size
		while (at > 0 && entry.order.compareTo(shown[at - 1].order) < 0) at--
		if (at == MAX_SHOWN_CANDIDATES) continue
		shown.add(at, entry)
		if (shown.size > MAX_SHOWN_CANDIDATES) shown.removeAt(MAX_SHOWN_CANDIDATES)
	}
	val spine = Collections.newSetFromMap(IdentityHashMap<ValueAnchor, Boolean>())
	var unwalked = tip
	return shown.map { entry ->
		val branch = ArrayList<ValueAnchor>()
		var node: ValueAnchor? = entry.anchor
		while (node != null) {
			while (unwalked != null && unwalked.elapsed >= node.elapsed) {
				spine += unwalked
				unwalked = unwalked.parent
			}
			if (node in spine) break
			branch += node
			node = node.parent
		}
		val points = ArrayList<Vec3d>()
		node?.let { points += it.state.position }
		for (index in branch.indices.reversed()) {
			points += branch[index].trace
			points += branch[index].state.position
		}
		CandidatePath(points, entry === shown.first())
	}
}

private const val MAX_SHOWN_CANDIDATES = 12
