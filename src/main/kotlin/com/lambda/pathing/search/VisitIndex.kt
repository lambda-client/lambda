package com.lambda.pathing.search

import com.lambda.pathing.core.Stance

internal class VisitIndex(private val root: () -> ValueAnchor?) {
	private var indexed: ValueAnchor? = null
	private val lineage = HashSet<Stance>()

	fun invalidate() {
		indexed = null
		lineage.clear()
	}

	fun visited(anchor: ValueAnchor, stance: Stance): Boolean {
		val current = root()
		if (current == null) invalidate() else if (current !== indexed) index(current)
		var node: ValueAnchor? = anchor
		while (node != null) {
			if (node === indexed) return stance in lineage
			if (node.stance == stance) return true
			if (node.legRoot) return false
			node = node.parent
		}
		return false
	}

	private fun index(root: ValueAnchor) {
		val previous = indexed
		val added = ArrayList<Stance>()
		var node: ValueAnchor? = root
		var joined = false
		while (node != null) {
			if (node === previous) {
				joined = true
				break
			}
			added += node.stance
			if (node.legRoot) break
			node = node.parent
		}
		if (!joined) lineage.clear()
		lineage.addAll(added)
		indexed = root
	}
}
