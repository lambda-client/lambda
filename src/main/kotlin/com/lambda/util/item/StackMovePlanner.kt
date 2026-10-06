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

object StackMovePlanner {
	enum class Target {
		Source,
		Destination
	}

	enum class Button(val id: Int) {
		Left(0),
		Right(1)
	}

	data class Click(val target: Target, val button: Button)

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

		fun fits(maxCount: Int) =
			cursor in 0..maxCount && source in 0..maxCount && destination in 0..maxCount

		fun settled(count: Int) = State(cursor = 0, source = total - count, destination = count)
	}

	data class Plan(val clicks: List<Click>, val destination: Int)

	private data class Step(val previous: State, val click: Click)

	private val CLICKS = Target.entries.flatMap { target -> Button.entries.map { Click(target, it) } }

	fun planWithin(state: State, maxCount: Int, goal: Int, clickBudget: Int): Plan? {
		if (!state.fits(maxCount)) return null
		val steps = explore(state, maxCount, maxClicks = clickBudget)
		val highest = minOf(goal, maxCount, state.total)
		return (highest downTo state.destination + 1).firstNotNullOfOrNull { count ->
			steps.pathTo(state.settled(count))?.let { Plan(it, count) }
		}
	}

	fun plan(state: State, maxCount: Int, goal: Int): List<Click>? {
		val end = state.settled(goal)
		if (!state.fits(maxCount) || !end.fits(maxCount)) return null
		return explore(state, maxCount, maxClicks = Int.MAX_VALUE).pathTo(end)
	}

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

	fun apply(state: State, click: Click, maxCount: Int): State =
		if (state.cursor == 0) state.pickUp(click) else state.place(click, maxCount)

	private fun State.pickUp(click: Click): State {
		val slot = this[click.target]
		val taken =
			when (click.button) {
				Button.Left -> slot
				Button.Right -> (slot + 1) / 2
			}
		return copy(cursor = taken).withSlot(click.target, slot - taken)
	}

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
}
