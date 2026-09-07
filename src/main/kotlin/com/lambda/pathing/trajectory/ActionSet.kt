package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.coarse.SpeedClass
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
 * The proposable successor set: how many coarse [steps] are offered from an anchor and how
 * many ticks off the best line ([marginTicks]) one may be. The excess is charged in the
 * frontier order, not filtered; see [ActionSet.actions].
 */
internal data class CorridorLevel(
    val steps: Int,
    val marginTicks: Double,
)

internal class ActionSet(
    private val catalog: MovementCatalog,
    private val field: CoarseValueField,
    private val config: MotionConstraints,
    private val searchConfig: ValueFieldSearchConfig,
    private val corridor: () -> CorridorLevel,
) {
    /**
     * The movements offered at [anchor], cheapest first. Each decision's price is the
     * owning movement's plus the detour excess (ticks its coarse step is worse than the best
     * successor); [temperature] then withholds what is not yet affordable. Sorted by price
     * so [Frontier] can re-rank an anchor as its cheap options are used up.
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
        // Blended guide, not the edge's arrival class: the detour ranking wants the safe
        // bound. See docs/decisions/beam.md.
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
            guideTicks = { field.guide(it) },
            movingGuideTicks = { field.guide(it, SpeedClass.MOVING) },
            momentumSkips = searchConfig.momentumSkips,
            momentumGait = searchConfig.momentumGait,
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

        // At equal surcharge an edge-spanning launch sorts ahead of a single-edge decision.
        // Ordering only, not cost; see docs/decisions/beam.md.
        fun spansEdges(decision: TrajectoryDecision): Boolean =
            decision is TrajectoryDecision.Launch && decision.solution == null &&
                decision.delayFrames == 0 && decision.step !in costOf
        val ordered = priced.sortedWith(
            compareBy({ temperature.surcharge(it.price) }, { if (spansEdges(it.decision)) 0 else 1 }),
        )
        val affordable = ordered.filter { temperature.affords(it.price) }
        if (affordable.isNotEmpty()) return affordable

        // Nothing affordable yet: offer the whole cheapest tier, not a single decision.
        // See docs/decisions/annealing.md.
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
        /** Coarse steps whose owner movements are asked for decisions at the normal margin. */
        private const val LAUNCH_STEPS = 2

        /** Width of the "equally hard" band used when nothing at all is affordable. */
        private const val DIFFICULTY_TIER = 0.1
    }
}
