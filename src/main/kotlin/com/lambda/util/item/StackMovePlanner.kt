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

/**
 * Plans the shortest sequence of vanilla `PICKUP` slot clicks that moves items of one item type from a source
 * slot into a destination slot, with the cursor as the only intermediary.
 *
 * The model follows vanilla click semantics for two slots and a cursor that hold the same item type or nothing:
 *  - Left click with an empty cursor picks up the whole slot.
 *  - Right click with an empty cursor picks up half the slot, rounded up.
 *  - Left click with a held stack places as much as fits into the slot.
 *  - Right click with a held stack places a single item.
 *
 * Every count is bounded by the stack size the destination slot allows (`maxCount`), which keeps the state space
 * at a few thousand states at most. A breadth-first search over it yields the minimal click count cheaply enough
 * to plan every move afresh.
 */
object StackMovePlanner {
	enum class Target {
		Source,
		Destination
	}

	/** A mouse button as vanilla numbers it in `PICKUP` clicks. */
	enum class Button(val id: Int) {
		Left(0),
		Right(1)
	}

	data class Click(val target: Target, val button: Button)

	/** Item counts held by the cursor and both slots. */
	data class State(val cursor: Int, val source: Int, val destination: Int) {
		val total get() = cursor + source + destination

		operator fun get(target: Target) =
			when (target) {
				Target.Source -> source
				Target.Destination -> destination
			}

		fun withSlot(target: Target, count: Int) =
			when (target) {
				Target.Source -> copy(source = count)
				Target.Destination -> copy(destination = count)
			}

		/** Whether every count lies within what slots holding at most [maxCount] items can represent. */
		fun fits(maxCount: Int) =
			cursor in 0..maxCount && source in 0..maxCount && destination in 0..maxCount

		/** The only state a finished move can end in: an empty cursor and [count] items in the destination. */
		fun settled(count: Int) = State(cursor = 0, source = total - count, destination = count)
	}

	/** The [clicks] that leave [destination] items in the destination slot and nothing on the cursor. */
	data class Plan(val clicks: List<Click>, val destination: Int)

	/** How a state was first reached during the search. */
	private data class Step(val previous: State, val click: Click)

	private val CLICKS = Target.entries.flatMap { target -> Button.entries.map { Click(target, it) } }

	/** Applies one [click] to [state] following vanilla `PICKUP` semantics. */
	fun apply(state: State, click: Click, maxCount: Int): State =
		if (state.cursor == 0) state.pickUp(click) else state.place(click, maxCount)

	/**
	 * The shortest click sequence that ends with exactly [goal] items in the destination and an empty cursor,
	 * or `null` when there is none: the goal exceeds [maxCount] or the items available, the source could not
	 * hold the surplus, or [state] does not [fit][State.fits] the model.
	 */
	fun plan(state: State, maxCount: Int, goal: Int): List<Click>? {
		val end = state.settled(goal)
		if (!state.fits(maxCount) || !end.fits(maxCount)) return null
		return explore(state, maxCount, maxClicks = Int.MAX_VALUE).pathTo(end)
	}

	/**
	 * The plan that brings the destination as close to [goal] as [clickBudget] clicks allow without exceeding
	 * it: the largest count in `(state.destination, goal]` whose shortest plan fits the budget. `null` when not
	 * even a single item can be moved within the budget.
	 */
	fun planWithin(state: State, maxCount: Int, goal: Int, clickBudget: Int): Plan? {
		if (!state.fits(maxCount)) return null
		val steps = explore(state, maxCount, maxClicks = clickBudget)
		val highest = minOf(goal, maxCount, state.total)
		return (highest downTo state.destination + 1).firstNotNullOfOrNull { count ->
			steps.pathTo(state.settled(count))?.let { Plan(it, count) }
		}
	}

	/** An empty cursor takes the whole slot with a left click and the larger half with a right click. */
	private fun State.pickUp(click: Click): State {
		val slot = this[click.target]
		val taken =
			when (click.button) {
				Button.Left -> slot
				Button.Right -> (slot + 1) / 2
			}
		return copy(cursor = taken).withSlot(click.target, slot - taken)
	}

	/** A held stack drops as much as fits with a left click and a single item with a right click. */
	private fun State.place(click: Click, maxCount: Int): State {
		val slot = this[click.target]
		val offered =
			when (click.button) {
				Button.Left -> cursor
				Button.Right -> 1
			}
		val placed = minOf(offered, maxCount - slot).coerceAtLeast(0)
		return copy(cursor = cursor - placed).withSlot(click.target, slot + placed)
	}

	/**
	 * Breadth-first search from [start]: every state reachable within [maxClicks] clicks, mapped to the step
	 * that first reached it ([start] itself maps to `null`). Following the steps back from a state yields its
	 * shortest plan.
	 */
	private fun explore(start: State, maxCount: Int, maxClicks: Int): Map<State, Step?> {
		val steps = hashMapOf<State, Step?>(start to null)
		var frontier = listOf(start)
		var depth = 0
		while (frontier.isNotEmpty() && depth < maxClicks) {
			frontier =
				frontier.flatMap { state ->
					CLICKS.mapNotNull { click ->
						apply(state, click, maxCount)
							.takeUnless { it in steps }
							?.also { steps[it] = Step(state, click) }
					}
				}
			depth++
		}
		return steps
	}

	private fun Map<State, Step?>.pathTo(end: State): List<Click>? {
		if (end !in this) return null
		return generateSequence(this[end]) { this[it.previous] }
			.map { it.click }
			.toList()
			.asReversed()
	}
}
