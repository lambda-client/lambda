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
import com.lambda.util.item.StackMovePlanner.Button
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

	private fun assertSettles(start: State, clicks: List<Click>, maxCount: Int, destination: Int) {
		val end = simulate(start, clicks, maxCount)
		assertEquals(destination, end.destination, "destination after $clicks from $start")
		assertEquals(0, end.cursor, "cursor after $clicks from $start")
		assertEquals(start.total, end.total, "items must be conserved")
	}

	private fun assertReaches(start: State, maxCount: Int, goal: Int): List<Click> {
		val plan = assertNotNull(StackMovePlanner.plan(start, maxCount, goal), "no plan for $start -> $goal (max $maxCount)")
		assertSettles(start, plan, maxCount, goal)
		return plan
	}

	/** A random state within the model and a goal that is reachable from it. */
	private fun Random.reachableCase(maxCount: Int): Pair<State, Int>? {
		val source = nextInt(0, maxCount + 1)
		val destination = nextInt(0, maxCount + 1)
		val total = source + destination
		val low = (total - maxCount).coerceAtLeast(0)
		val high = minOf(maxCount, total)
		if (low > high) return null
		return State(0, source, destination) to nextInt(low, high + 1)
	}

	@Test
	fun wholeStackIntoEmptySlotIsTwoClicks() {
		val plan = assertReaches(State(0, 64, 0), 64, 64)
		assertEquals(listOf(Click(Target.Source, Button.Left), Click(Target.Destination, Button.Left)), plan)
	}

	@Test
	fun singleItemIsThreeClicks() {
		val plan = assertReaches(State(0, 64, 0), 64, 1)
		assertEquals(3, plan.size)
	}

	@Test
	fun halfStackUsesRightClickPickup() {
		val plan = assertReaches(State(0, 64, 0), 64, 32)
		assertEquals(listOf(Click(Target.Source, Button.Right), Click(Target.Destination, Button.Left)), plan)
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
	fun rejectsStatesOutsideTheModel() {
		// Counts above the stack size cannot be represented, so planning declines instead of guessing.
		assertNull(StackMovePlanner.plan(State(70, 0, 0), 64, 64))
		assertNull(StackMovePlanner.plan(State(0, 20, 0), 16, 16))
		assertNull(StackMovePlanner.planWithin(State(70, 0, 0), 64, 64, clickBudget = 100))
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
				val (start, goal) = random.reachableCase(maxCount) ?: return@repeat
				assertReaches(start, maxCount, goal)
			}
		}
	}

	@Test
	fun planWithinTakesTheGoalWhenItsPlanFitsTheBudget() {
		val plan = assertNotNull(StackMovePlanner.planWithin(State(0, 64, 0), 64, goal = 32, clickBudget = 2))
		assertEquals(32, plan.destination)
		assertEquals(listOf(Click(Target.Source, Button.Right), Click(Target.Destination, Button.Left)), plan.clicks)
	}

	@Test
	fun planWithinSettlesForFewerItemsWhenTheBudgetIsTight() {
		// 20 items need more than three clicks; a single item needs exactly three (pick up, place one, put back).
		val plan = assertNotNull(StackMovePlanner.planWithin(State(0, 64, 0), 64, goal = 20, clickBudget = 3))
		assertEquals(1, plan.destination)
		assertEquals(3, plan.clicks.size)
		assertSettles(State(0, 64, 0), plan.clicks, 64, 1)
	}

	@Test
	fun planWithinNeverLeavesItemsOnTheCursorForABiggerMove() {
		// Picking up and placing one item is two clicks but would strand 63 items on the cursor.
		assertNull(StackMovePlanner.planWithin(State(0, 64, 0), 64, goal = 20, clickBudget = 2))
		assertNull(StackMovePlanner.planWithin(State(0, 64, 0), 64, goal = 20, clickBudget = 0))
	}

	@Test
	fun planWithinClampsTheGoalToWhatCanBeHeld() {
		val plan = assertNotNull(StackMovePlanner.planWithin(State(0, 14, 50), 64, goal = 70, clickBudget = 10))
		assertEquals(64, plan.destination)
		assertNull(StackMovePlanner.planWithin(State(0, 10, 10), 64, goal = 10, clickBudget = 10), "nothing to add")
	}

	@Test
	fun planWithinIsAsGoodAsTheExactPlanAndNeverOvershoots() {
		val random = Random(42)
		for (maxCount in listOf(1, 16, 64)) {
			repeat(300) {
				val (start, goal) = random.reachableCase(maxCount) ?: return@repeat
				if (goal <= start.destination) return@repeat
				val budget = random.nextInt(0, 9)
				val exact = assertNotNull(StackMovePlanner.plan(start, maxCount, goal))
				val plan = StackMovePlanner.planWithin(start, maxCount, goal, budget)
				if (exact.size <= budget) {
					assertEquals(goal, assertNotNull(plan, "exact plan fits $budget clicks").destination)
				}
				if (plan == null) return@repeat
				assertTrue(plan.clicks.size <= budget, "plan ${plan.clicks} exceeds the budget of $budget")
				assertTrue(plan.destination in (start.destination + 1)..goal, "destination ${plan.destination} outside (${start.destination}, $goal]")
				assertSettles(start, plan.clicks, maxCount, plan.destination)
				assertEquals(
					plan.clicks.size,
					assertNotNull(StackMovePlanner.plan(start, maxCount, plan.destination)).size,
					"a budgeted plan must still be a shortest plan"
				)
			}
		}
	}
}
