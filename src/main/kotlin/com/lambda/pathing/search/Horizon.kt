package com.lambda.pathing.search

internal class Horizon(
	private val window: Int,
	private val frontier: Frontier,
) {
	var root: ValueAnchor? = null
		private set

	var end = Int.MAX_VALUE
		private set

	fun begin() {
		if (window > 0) end = window
	}

	fun canReach(anchor: ValueAnchor): Boolean = root?.let { anchor.descendsFrom(it) } ?: true

	fun follow(publications: Publications) {
		val cursor = publications.cursor() ?: return
		publications.expireUnadopted()
		val running = publications.running ?: return
		val executing = cursor.coerceAtMost(running.brake.elapsed)
		if (executing > running.anchor.elapsed) {
			if (root !== running.brake) reRoot(running.brake)
			return
		}
		val reached = running.anchor.ancestorAtOrBefore(executing) ?: return
		val current = root
		if (current == null || (reached !== current && reached.descendsFrom(current))) reRoot(reached)
	}

	fun reRoot(anchor: ValueAnchor) {
		root = anchor
		frontier.retainDescendants(anchor)
		frontier.reopen(anchor)
		if (window > 0) end = anchor.elapsed + window
		frontier.repartition(anchor, end)
	}

	fun extend(): Boolean {
		if (window <= 0 || !frontier.hasParked) return false
		val root = root ?: return false
		val before = frontier.openSize
		end += window
		frontier.repartition(root, end)
		return frontier.openSize > before
	}
}
