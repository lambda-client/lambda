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

import com.lambda.util.item.StackMovePlanner
import com.lambda.util.item.StackMovePlanner.Click
import com.lambda.util.item.StackMovePlanner.State
import com.lambda.util.item.StackMovePlanner.Target
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StackMovePlannerTest {
	private fun simulate(start: State, clicks: List<Click>, maxCount: Int) =
		clicks.fold(start) { state, click -> StackMovePlanner.apply(state, click, maxCount) }

	private fun assertReaches(start: State, maxCount: Int, goal: Int): List<Click> {
		val plan = assertNotNull(StackMovePlanner.plan(start, maxCount, goal), "no plan for $start -> $goal (max $maxCount)")
		val end = simulate(start, plan, maxCount)
		assertEquals(goal, end.destination, "destination after $plan from $start")
		assertEquals(0, end.cursor, "cursor after $plan from $start")
		assertEquals(start.total, end.total, "items must be conserved")
		return plan
	}

	@Test
	fun wholeStackIntoEmptySlotIsTwoClicks() {
		val plan = assertReaches(State(0, 64, 0), 64, 64)
		assertEquals(listOf(Click(Target.Source, 0), Click(Target.Destination, 0)), plan)
	}

	@Test
	fun singleItemIsThreeClicks() {
		val plan = assertReaches(State(0, 64, 0), 64, 1)
		assertEquals(3, plan.size)
	}

	@Test
	fun halfStackUsesRightClickPickup() {
		val plan = assertReaches(State(0, 64, 0), 64, 32)
		assertEquals(listOf(Click(Target.Source, 1), Click(Target.Destination, 0)), plan)
	}

	@Test
	fun partialCountBeatsPlacingOneAtATime() {
		val plan = assertReaches(State(0, 64, 0), 64, 10)
		assertTrue(plan.size < 12, "expected a smarter plan than 12 single placements, got ${plan.size}")
	}

	@Test
	fun mergesIntoPartialDestination() {
		// The whole source fits: pick up, place.
		assertEquals(2, assertReaches(State(0, 14, 50), 64, 64).size)
		// Only 14 of 20 fit, so the surplus has to go back: pick up, place, return.
		assertEquals(3, assertReaches(State(0, 20, 50), 64, 64).size)
	}

	@Test
	fun continuesFromItemsAlreadyOnCursor() {
		assertReaches(State(5, 10, 0), 64, 15)
		assertReaches(State(30, 0, 10), 64, 20)
	}

	@Test
	fun alreadySatisfiedNeedsNoClicks() {
		assertEquals(emptyList(), StackMovePlanner.plan(State(0, 10, 7), 64, 7))
	}

	@Test
	fun rejectsUnreachableGoals() {
		assertNull(StackMovePlanner.plan(State(0, 10, 0), 64, 11))
		assertNull(StackMovePlanner.plan(State(0, 64, 64), 64, 65))
		assertNull(StackMovePlanner.plan(State(0, 64, 64), 64, 10), "source cannot hold the surplus")
		assertNull(StackMovePlanner.plan(State(0, 5, 0), 0, 1))
	}

	@Test
	fun unstackableItemsMoveWhole() {
		val plan = assertReaches(State(0, 1, 0), 1, 1)
		assertEquals(2, plan.size)
	}

	@Test
	fun everyReachableGoalHasAValidPlan() {
		val random = Random(1337)
		for (maxCount in listOf(1, 16, 64)) {
			repeat(300) {
				val source = random.nextInt(0, maxCount + 1)
				val destination = random.nextInt(0, maxCount + 1)
				val total = source + destination
				val low = (total - maxCount).coerceAtLeast(0)
				val high = minOf(maxCount, total)
				if (low > high) return@repeat
				val goal = random.nextInt(low, high + 1)
				assertReaches(State(0, source, destination), maxCount, goal)
			}
		}
	}
}
