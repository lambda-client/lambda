package com.lambda.pathing.search

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.DecisionPrice
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.families.MovementCatalog
import com.lambda.pathing.actions.PricedDecision
import com.lambda.pathing.actions.ProposalContext
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.Stance

internal class ActionSet(
	private val catalog: MovementCatalog,
	private val field: ValueField,
	private val config: MotionConstraints,
) {
	private fun actions(anchor: ValueAnchor, temperature: Double): List<PricedDecision> {
		if (anchor.parent != null && field.guide(anchor.stance) <= FINISH_VALUE_TICKS) {
			return emptyList()
		}

		val steps = field.steps(anchor.stance, CORRIDOR_STEPS, CORRIDOR_MARGIN_TICKS)
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
			)
		}

		val priced = ArrayList<PricedDecision>()

		fun add(decision: TrajectoryDecision) {
			val edge = byDestination.getValue(decision.step)
			val excess = costOf.getValue(edge.to) - bestCost
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
		)
		for (movement in catalog.movements) {
			movement.proposals(proposalContext).forEach(::add)
		}

		for (step in steps.take(LAUNCH_STEPS)) {
			val context = contextFor(step)
			val owner = catalog[step.movement]
			owner?.decisions(context)?.forEach(::add)
			for (movement in catalog.movements) {
				if (movement !== owner && movement.offersFor(step)) movement.decisions(context).forEach(::add)
			}
		}

		if (priced.isEmpty()) return emptyList()

		priced.sortWith { a, b -> a.price.surcharge.compareTo(b.price.surcharge) }
		val affordable = priced.filter { it.price.difficulty <= temperature }
		if (affordable.isNotEmpty()) return affordable

		val floor = priced.minOf { it.price.difficulty }
		return priced.filter { it.price.difficulty <= floor + DIFFICULTY_TIER }
	}

	fun next(anchor: ValueAnchor, temperature: Double, epoch: Int): TrajectoryDecision? {
		val actions = cached(anchor, temperature, epoch)
		while (anchor.actionCursor < actions.size) {
			val decision = actions[anchor.actionCursor++].decision
			if (anchor.attempt(decision)) return decision
		}
		return null
	}

	fun nextSurcharge(anchor: ValueAnchor, temperature: Double, epoch: Int): Double? {
		val actions = cached(anchor, temperature, epoch)
		while (anchor.actionCursor < actions.size) {
			val action = actions[anchor.actionCursor]
			if (!anchor.hasAttempted(action.decision)) return action.price.surcharge
			anchor.actionCursor++
		}
		return null
	}

	private fun cached(anchor: ValueAnchor, temperature: Double, epoch: Int): List<PricedDecision> {
		val hazard = anchor.hazardFrame
		val cached = anchor.actions
		if (cached != null && anchor.actionsHazardFrame == hazard && anchor.actionsEpoch == epoch) return cached
		return actions(anchor, temperature).also {
			anchor.actions = it
			anchor.actionCursor = 0
			anchor.actionsHazardFrame = hazard
			anchor.actionsEpoch = epoch
		}
	}

	private val DecisionPrice.surcharge: Double get() = ticks + RISK_WEIGHT * difficulty

	private companion object {
		const val RISK_WEIGHT = 4.0

		private const val CORRIDOR_STEPS = 3
		private const val CORRIDOR_MARGIN_TICKS = 4.0

		private const val LAUNCH_STEPS = 2

		private const val DIFFICULTY_TIER = 0.1
	}
}
