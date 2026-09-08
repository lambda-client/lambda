package com.lambda.pathing.coarse

import com.lambda.pathing.core.Stance

/**
 * What the coarse search is trying to reach, abstracted from the one concrete [Stance] it
 * has always been. Today the D* goal node is still a single packed stance ([anchorStance]);
 * the interface exists so the membership rule and the arrival test can vary without the
 * planner learning about each shape. See docs/graph-engine-rethink.md §9.
 *
 * Contract for further shapes (not shipped; the D* goal node must first become virtual):
 * - `Near(center, radius)`: [satisfied] is a distance test; [heuristic] must stay admissible
 *   for EVERY satisfying stance, i.e. `max(0, h(center) - radius * L)` where `L` is the
 *   Lipschitz constant of the point heuristic in its target argument (the LARGEST per-block
 *   rate in [SimpleMoveLibrary.heuristicCaps], not the smallest -- subtracting too little
 *   overestimates). Multiple satisfying stances need virtual zero-cost goal edges, which the
 *   anchor mechanism already is.
 * - `Compound(primary, next)`: satisfied by `primary`; the heuristic is `primary.heuristic`
 *   alone (the continuation is priced by the route that follows, never here), so it is
 *   admissible whenever `primary` is.
 */
interface GoalShape {

	/** Admissible lower bound on the ticks from [from] to any satisfying stance. */
	fun heuristic(from: Stance): Double

	/** The executor's arrival test. */
	fun satisfied(stance: Stance): Boolean

	/** The concrete D* goal node today: the stance the value field descends towards. */
	val anchorStance: Stance

	/** Exactly one stance satisfies: the goal the planner has always had. */
	data class Point(val stance: Stance, val moves: SimpleMoveLibrary) : GoalShape {

		override fun heuristic(from: Stance): Double = moves.heuristic(from, stance)

		override fun satisfied(stance: Stance): Boolean = stance == this.stance

		override val anchorStance: Stance get() = stance
	}
}
