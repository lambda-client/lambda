package com.lambda.pathing.search

import com.lambda.pathing.core.Stance

internal class VisitIndex(private val root: () -> ValueAnchor?) {
	private var indexed: ValueAnchor? = null
	private val lineage = HashMap<Stance, Int>()

	fun invalidate() {
		indexed = null
		lineage.clear()
	}

	fun visited(anchor: ValueAnchor, stance: Stance): Boolean {
		val current = root()
		if (current == null) invalidate() else if (current !== indexed) index(current)
		var node: ValueAnchor? = anchor
		while (node != null) {
			if (node === indexed) return (lineage[stance] ?: return false) >= node.elapsed - HISTORY_FRAMES
			if (node.stance == stance) return true
			if (node.legRoot) return false
			node = node.parent
		}
		return false
	}

	private fun index(root: ValueAnchor) {
		val previous = indexed
		val added = ArrayList<ValueAnchor>()
		var node: ValueAnchor? = root
		var joined = false
		while (node != null) {
			if (node === previous) {
				joined = true
				break
			}
			added += node
			if (node.legRoot) break
			node = node.parent
		}
		if (!joined) lineage.clear()
		for (visit in added) lineage.merge(visit.stance, visit.elapsed, ::maxOf)
		indexed = root
	}

	private companion object {
		const val HISTORY_FRAMES = 300
	}
}
