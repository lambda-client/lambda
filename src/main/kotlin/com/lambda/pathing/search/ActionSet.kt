package com.lambda.pathing.search

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.DecisionPrice
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.MovementCatalog
import com.lambda.pathing.actions.PricedDecision
import com.lambda.pathing.actions.ProposalContext
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance

internal class ActionSet(
	private val catalog: MovementCatalog,
	private val field: ValueField,
	private val config: MotionConstraints,
	private val searchConfig: ValueFieldSearchConfig,

	private val momentum: Boolean = false,
) {

	fun actions(anchor: ValueAnchor, temperature: Temperature): List<PricedDecision> {
		if (anchor.parent != null && field.guide(anchor.stance) <= searchConfig.finishValueTicks) {
			return emptyList()
		}

		val steps = field.steps(
			anchor.stance, CORRIDOR_STEPS, CORRIDOR_MARGIN_TICKS,
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
			guideTicks = { field.guide(it) },
			movingGuideTicks = { field.guide(it, SpeedClass.MOVING) },
			momentum = momentum,
		)
		for (movement in catalog.movements) {
			val proposals = movement.proposals(proposalContext)
			proposals.walks.forEach(::add)
			proposals.launches.forEach(::add)
		}

		val launchSteps =
			if (CORRIDOR_MARGIN_TICKS > searchConfig.branchMarginTicks) CORRIDOR_STEPS else LAUNCH_STEPS
		for (step in steps.take(launchSteps)) {
			movementDecisions(anchor, step, ::add)
		}

		if (priced.isEmpty()) return emptyList()

		fun spansEdges(decision: TrajectoryDecision): Boolean =
			decision.spansEdges && decision.step !in costOf

		priced.sortWith(
			compareBy({ temperature.surcharge(it.price) }, { if (spansEdges(it.decision)) 0 else 1 }),
		)
		val affordable = priced.filter { temperature.affords(it.price) }
		if (affordable.isNotEmpty()) return affordable

		val floor = priced.minOf { it.price.difficulty }
		return priced.filter { it.price.difficulty <= floor + DIFFICULTY_TIER }
	}

	private inline fun movementDecisions(anchor: ValueAnchor, edge: CoarseEdge, accept: (TrajectoryDecision) -> Unit) {
		val owner = catalog[edge.movement]
		val context = DecisionContext(anchor, edge, config, field.view, steering = field)
		if (owner != null && owner.id != MovementId.WALK) owner.decisions(context).forEach(accept)
		for (movement in catalog.movements) {
			if (movement !== owner && movement.offersFor(edge)) movement.decisions(context).forEach(accept)
		}
	}

	private companion object {

		private const val CORRIDOR_STEPS = 3
		private const val CORRIDOR_MARGIN_TICKS = 4.0

		private const val LAUNCH_STEPS = 2

		private const val DIFFICULTY_TIER = 0.1
	}
}
