package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.CoarseEdge
import com.lambda.pathing.movement.DecisionContext
import com.lambda.pathing.movement.DecisionPrice
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.MovementCatalog
import com.lambda.pathing.movement.PricedDecision
import com.lambda.pathing.movement.ProposalContext
import com.lambda.pathing.movement.TrajectoryDecision

/**
 * How wide the trajectory search may look when the guide stops moving.
 *
 * [steps] and [marginTicks] widen the *proposable* successor set: how many coarse
 * steps are offered, and how far from optimal one may be. [guideExpansionTicks] is
 * unrelated -- it is how far past the goal-optimal band the coarse value field is
 * labelled, so that a detour has somewhere mapped to land. Escalation wants a lot of
 * the last and can afford the second only because the excess is charged rather than
 * waved through: see the detour price in [ActionSet.actions].
 */
internal data class CorridorLevel(
    val steps: Int,
    val marginTicks: Double,
    val guideExpansionTicks: Double,
)

internal class ActionSet(
    private val catalog: MovementCatalog,
    private val field: CoarseValueField,
    private val config: MotionConstraints,
    private val searchConfig: ValueFieldSearchConfig,
    private val corridor: () -> CorridorLevel,
) {
    /**
     * The movements offered at [anchor], cheapest first.
     *
     * Ordering used to be positional -- descents, then launches, then walks, with the
     * list flipped when the leading coarse step was not a walk. Nothing in it knew what a
     * decision cost, so an anchor was ground through its whole vocabulary at one priority
     * before the search descended. Now every decision carries a price and the list is
     * sorted by it, which is what lets [Frontier] re-rank an anchor as its cheap options
     * are used up.
     *
     * Two prices are added to whatever the owning movement charges:
     *
     * - the **detour excess**, how many ticks worse than the best available successor this
     *   decision's coarse step is. A wide corridor is an escape hatch, and this is what
     *   keeps it from also being a preference -- a step nine blocks off the best line stays
     *   reachable and stops competing with one on it.
     * - **temperature**, which withholds difficult movements until the search has evidence
     *   it needs them.
     */
    fun actions(anchor: ValueAnchor, temperature: Temperature): List<PricedDecision> {

        if (anchor.parent != null && field.guide(anchor.stance) <= searchConfig.finishValueTicks) {
            return emptyList()
        }

        val level = corridor()
        val steps = field.steps(
            anchor.stance, level.steps, level.marginTicks,
            anchor.heading(),
        )
        if (steps.isEmpty()) return emptyList()

        val costOf = HashMap<Stance, Double>(steps.size)
        steps.forEach { costOf[it.to] = it.lowerBoundTicks + field.guide(it.to) }
        val bestCost = costOf.values.min()
        val byDestination = steps.associateBy { it.to }
        val contexts = HashMap<Stance, DecisionContext>(steps.size)

        fun contextFor(edge: CoarseEdge): DecisionContext = contexts.getOrPut(edge.to) {
            DecisionContext(
                body = anchor,
                edge = edge,
                constraints = config,
                view = field.view,
                steering = field,
                chainLength = searchConfig.chainLength,
            )
        }

        val leading = steps.first()
        val priced = ArrayList<PricedDecision>()

        fun add(decision: TrajectoryDecision) {
            val edge = decision.step?.let { byDestination[it] } ?: leading
            val excess = (costOf[edge.to] ?: bestCost) - bestCost
            val owner = catalog[decision.movement]
            val movementPrice = owner?.price(decision, contextFor(edge)) ?: DecisionPrice.FREE
            priced += PricedDecision(decision, movementPrice + DecisionPrice(ticks = excess))
        }

        val proposalContext = ProposalContext(
            body = anchor,
            steps = steps,
            constraints = config,
            view = field.view,
            steering = field,
            headingFanDegrees = searchConfig.headingFanDegrees,
        )
        for (movement in catalog.movements) {
            val proposals = movement.proposals(proposalContext)
            proposals.walks.forEach(::add)
            proposals.launches.forEach(::add)
        }

        val launchSteps =
            if (level.marginTicks > searchConfig.branchMarginTicks) level.steps else LAUNCH_STEPS
        for (step in steps.take(launchSteps)) {
            movementDecisions(anchor, step).forEach(::add)
        }

        if (priced.isEmpty()) return emptyList()

        val ordered = priced.sortedBy { temperature.surcharge(it.price) }
        val affordable = ordered.filter { temperature.affords(it.price) }
        if (affordable.isNotEmpty()) return affordable

        // Nothing here is affordable yet, which means this stance needs more than the
        // search has so far bought -- a lip that only a run-up clears, say. Offer the
        // whole cheapest tier rather than a single decision: the variants within a tier
        // are the launch offsets and hop gaps that decide whether it works at all, and
        // handing out one per escalation is how a certifiable gap goes uncertified.
        val floor = ordered.minOf { it.price.difficulty }
        return ordered.filter { it.price.difficulty <= floor + DIFFICULTY_TIER }
    }

    private fun movementDecisions(anchor: ValueAnchor, edge: CoarseEdge): List<TrajectoryDecision> {
        val owner = catalog[edge.movement]
        val context = DecisionContext(anchor, edge, config, field.view, steering = field)
        return buildList {

            if (owner != null && owner.id != MovementId.WALK) addAll(owner.decisions(context))
            catalog.movements.forEach { movement ->
                if (movement !== owner && movement.offersFor(edge)) addAll(movement.decisions(context))
            }
        }
    }

    private companion object {

        private const val LAUNCH_STEPS = 2

        /** Width of the "equally hard" band used when nothing at all is affordable. */
        private const val DIFFICULTY_TIER = 0.1
    }
}
