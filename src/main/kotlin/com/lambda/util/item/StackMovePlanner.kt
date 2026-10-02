/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.util.item

import java.util.concurrent.ConcurrentHashMap

/**
 * Plans the shortest sequence of vanilla `PICKUP` slot clicks that moves an exact number of items
 * of a single item type between two slots, using the cursor as the only intermediary.
 *
 * The model follows vanilla click semantics for a source slot, a destination slot and the cursor,
 * all holding the same item type (or being empty):
 *  - Left click with an empty cursor picks up the whole slot.
 *  - Right click with an empty cursor picks up half the slot, rounded up.
 *  - Left click with a held stack places as much as fits into the slot.
 *  - Right click with a held stack places exactly one item.
 *
 * A breadth-first search over `(cursor, source)` (the destination count follows from the invariant
 * `cursor + source + destination = total`) finds the minimal click count. Results are cached per
 * distinct `(cursor, source, destination, maxCount, destinationGoal)` since the state space is tiny
 * and transfers tend to repeat the same shapes.
 */
object StackMovePlanner {
	enum class Target {
		Source,
		Destination
	}

	/** A single slot click: [button] `0` is a left click, `1` a right click. */
	data class Click(val target: Target, val button: Int)

	/** Item counts held by the cursor and both slots. */
	data class State(val cursor: Int, val source: Int, val destination: Int) {
		val total get() = cursor + source + destination
	}

	private data class Key(val cursor: Int, val source: Int, val destination: Int, val maxCount: Int, val goal: Int)

	private val cache = ConcurrentHashMap<Key, List<Click>>()

	private val actions =
		listOf(
			Click(Target.Source, 0),
			Click(Target.Source, 1),
			Click(Target.Destination, 0),
			Click(Target.Destination, 1)
		)

	/**
	 * Applies one [click] to [state] following vanilla `PICKUP` semantics.
	 * Returns the same instance when the click would have no effect.
	 */
	fun apply(state: State, click: Click, maxCount: Int): State {
		val slot = if (click.target == Target.Source) state.source else state.destination
		val cursor = state.cursor
		val (newCursor, newSlot) =
			if (cursor == 0) {
				if (slot == 0) return state
				val taken = if (click.button == 0) slot else (slot + 1) / 2
				taken to slot - taken
			} else {
				val room = maxCount - slot
				if (room <= 0) return state
				val placed = if (click.button == 0) minOf(cursor, room) else 1
				cursor - placed to slot + placed
			}
		return if (click.target == Target.Source) State(newCursor, newSlot, state.destination)
		else State(newCursor, state.source, newSlot)
	}

	/**
	 * Finds the shortest click sequence that ends with the destination holding exactly [destinationGoal]
	 * items and an empty cursor. Returns `null` when the goal is unreachable (for example when the goal
	 * exceeds [maxCount] or the items available).
	 */
	fun plan(state: State, maxCount: Int, destinationGoal: Int): List<Click>? {
		if (maxCount <= 0 || destinationGoal < 0 || destinationGoal > maxCount) return null
		if (destinationGoal > state.total) return null
		if (state.cursor == 0 && state.destination == destinationGoal) return emptyList()
		if (state.total - destinationGoal > maxCount) return null

		val key = Key(state.cursor, state.source, state.destination, maxCount, destinationGoal)
		return cache.getOrPut(key) { search(state, maxCount, destinationGoal) }
	}

	/**
	 * Number of clicks needed for [plan], or `null` when unreachable.
	 */
	fun cost(state: State, maxCount: Int, destinationGoal: Int) = plan(state, maxCount, destinationGoal)?.size

	private fun search(start: State, maxCount: Int, goal: Int): List<Click>? {
		val width = maxCount + 1
		fun index(state: State) = state.cursor * width + state.source

		// parent[index] holds (parentIndex shl 2) or actionIndex; -1 marks unvisited.
		val parent = IntArray(width * width) { -1 }
		val queue = ArrayDeque<State>()
		val startIndex = index(start)
		parent[startIndex] = startIndex shl 2
		queue.addLast(start)

		while (queue.isNotEmpty()) {
			val state = queue.removeFirst()
			if (state.cursor == 0 && state.destination == goal) return reconstruct(parent, width, startIndex, index(state))

			actions.forEachIndexed { actionIndex, click ->
				val next = apply(state, click, maxCount)
				if (next === state) return@forEachIndexed
				if (next.cursor > maxCount || next.source > maxCount || next.destination > maxCount) return@forEachIndexed
				val nextIndex = index(next)
				if (parent[nextIndex] != -1) return@forEachIndexed
				parent[nextIndex] = (index(state) shl 2) or actionIndex
				queue.addLast(next)
			}
		}
		return null
	}

	private fun reconstruct(parent: IntArray, width: Int, startIndex: Int, endIndex: Int): List<Click> {
		val clicks = ArrayDeque<Click>()
		var current = endIndex
		while (current != startIndex) {
			val entry = parent[current]
			clicks.addFirst(actions[entry and 3])
			current = entry ushr 2
		}
		return clicks.toList()
	}
}
