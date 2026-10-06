package com.lambda.pathing.search

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.DecisionPrice
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.MovementCatalog
import com.lambda.pathing.actions.PricedDecision
import com.lambda.pathing.actions.ProposalContext
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance

internal class ActionSet(
	private val catalog: MovementCatalog,
	private val field: ValueField,
	private val config: MotionConstraints,
	private val searchConfig: ValueFieldSearchConfig,
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
			val edge = byDestination[decision.step] ?: leading
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
			movement.proposals(proposalContext).forEach(::add)
		}

		for (step in steps.take(LAUNCH_STEPS)) {
			movementDecisions(anchor, step, ::add)
		}

		if (priced.isEmpty()) return emptyList()

		priced.sortWith(compareBy { temperature.surcharge(it.price) })
		val affordable = priced.filter { temperature.affords(it.price) }
		if (affordable.isNotEmpty()) return affordable

		val floor = priced.minOf { it.price.difficulty }
		return priced.filter { it.price.difficulty <= floor + DIFFICULTY_TIER }
	}

	fun next(anchor: ValueAnchor, temperature: Temperature, epoch: Int): PricedDecision? {
		for (priced in cached(anchor, temperature, epoch)) {
			val action = priced.decision
			if (action in anchor.attempted) continue
			anchor.attempted += action
			return priced
		}
		return null
	}

	fun nextSurcharge(anchor: ValueAnchor, temperature: Temperature, epoch: Int): Double? =
		cached(anchor, temperature, epoch)
			.firstOrNull { it.decision !in anchor.attempted }
			?.let { temperature.surcharge(it.price) }

	private fun cached(anchor: ValueAnchor, temperature: Temperature, epoch: Int): List<PricedDecision> {
		val hazard = anchor.hazardFrame
		val cached = anchor.actions
		if (cached != null && anchor.actionsHazardFrame == hazard && anchor.actionsEpoch == epoch) return cached
		return actions(anchor, temperature).also {
			anchor.actions = it
			anchor.actionsHazardFrame = hazard
			anchor.actionsEpoch = epoch
		}
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
