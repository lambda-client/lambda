package com.lambda.pathing.search

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.DecisionPrice
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.families.MovementCatalog
import com.lambda.pathing.actions.PricedDecision
import com.lambda.pathing.actions.ProposalContext
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.coarse.MomentumRules
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.Stance
import kotlin.math.atan2

internal class ActionSet(
	private val catalog: MovementCatalog,
	private val field: ValueField,
	private val config: MotionConstraints,
	private val exploration: Exploration? = null,
	private val finalLeg: () -> Boolean = { true },
) {
	private fun actions(anchor: ValueAnchor, temperature: Double, lastResort: Boolean): List<PricedDecision> {
		if (anchor.parent != null && anchor.finished && finalLeg()) {
			return emptyList()
		}

		val steps = field.steps(anchor.stance, exploration?.corridorSteps ?: CORRIDOR_STEPS, exploration?.corridorMarginTicks ?: CORRIDOR_MARGIN_TICKS)
		if (steps.isEmpty()) return emptyList()

		val costOf = HashMap<Stance, Double>(steps.size)

		steps.forEach { costOf[it.to] = it.lowerBoundTicks + field.guide(it.to, MomentumRules.arrival(it, field.view)) }
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
				lastResort = lastResort,
			)
		}

		val priced = ArrayList<PricedDecision>()
		val sprinting = anchor.state.isSprinting && true in config.sprintModes

		fun add(decision: TrajectoryDecision) {
			if (sprinting && walksWithoutSprint(decision)) return
			val edge = byDestination[decision.step] ?: steps.first()
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

		for (step in steps.take(exploration?.launchSteps ?: LAUNCH_STEPS)) {
			val context = contextFor(step)
			val owner = catalog[step.movement]
			owner?.decisions(context)?.forEach(::add)
			for (movement in catalog.movements) {
				if (movement !== owner && movement.offersFor(step)) movement.decisions(context).forEach(::add)
			}
		}

		if (priced.isEmpty()) return emptyList()

		if (exploration == null) {
			priced.sortWith { a, b -> a.price.surcharge.compareTo(b.price.surcharge) }
		} else {
			val keyed = priced.map { it to it.price.surcharge + exploration.jitter(anchor.stance, anchor.elapsed, it.decision) }
			priced.clear()
			keyed.sortedBy { it.second }.mapTo(priced) { it.first }
		}
		val affordable = priced.filter { it.price.difficulty <= temperature }
		if (affordable.isNotEmpty()) return affordable

		val floor = priced.minOf { it.price.difficulty }
		return priced.filter { it.price.difficulty <= floor + DIFFICULTY_TIER }
	}

	fun explorations(anchor: ValueAnchor): List<TrajectoryDecision> {
		val exploration = exploration ?: return emptyList()
		if (anchor.parent == null) return exploration.seeds.map { if (it is TrajectoryDecision.Heading) it.copy(descent = config.maxSafeFallDistance) else it }
		if (!anchor.state.onGround || anchor.state.touchingWater) return emptyList()
		val step = field.steps(anchor.stance, exploration.corridorSteps, exploration.corridorMarginTicks).firstOrNull()?.to ?: return emptyList()
		val position = anchor.state.position
		val velocity = anchor.state.velocity
		val heading = if (velocity.horizontalLength() > MotionConstraints.STOPPED_SPEED) {
			Math.toDegrees(atan2(velocity.z, velocity.x)) - 90.0
		} else {
			Math.toDegrees(atan2(step.z + 0.5 - position.z, step.x + 0.5 - position.x)) - 90.0
		}
		val follow = ArrayList<TrajectoryDecision>()
		var offset = -FOLLOW_SPREAD_DEGREES
		while (offset <= FOLLOW_SPREAD_DEGREES) {
			for (delay in 0..FOLLOW_MAX_DELAY) for (sprint in config.sprintModes) {
				follow += TrajectoryDecision.Heading(sprint, step, heading + offset, delay, descent = config.maxSafeFallDistance)
			}
			offset += FOLLOW_YAW_STEP_DEGREES
		}
		return follow
	}

	private fun walksWithoutSprint(decision: TrajectoryDecision): Boolean = when (decision) {
		is TrajectoryDecision.Walk -> !decision.sprint
		is TrajectoryDecision.Heading -> !decision.sprint
		else -> false
	}

	fun next(anchor: ValueAnchor, temperature: Double, lastResort: Boolean, epoch: Int): TrajectoryDecision? {
		val actions = cached(anchor, temperature, lastResort, epoch)
		while (anchor.actionCursor < actions.size) {
			val decision = actions[anchor.actionCursor++].decision
			if (anchor.attempt(decision)) return decision
		}
		return null
	}

	fun nextSurcharge(anchor: ValueAnchor, temperature: Double, lastResort: Boolean, epoch: Int): Double? {
		val actions = cached(anchor, temperature, lastResort, epoch)
		while (anchor.actionCursor < actions.size) {
			val action = actions[anchor.actionCursor]
			if (!anchor.hasAttempted(action.decision)) return action.price.surcharge
			anchor.actionCursor++
		}
		return null
	}

	private fun cached(anchor: ValueAnchor, temperature: Double, lastResort: Boolean, epoch: Int): List<PricedDecision> {
		val hazard = anchor.hazardFrame
		val cached = anchor.actions
		if (cached != null && anchor.actionsHazardFrame == hazard && anchor.actionsEpoch == epoch) return cached
		return actions(anchor, temperature, lastResort).also {
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

		private const val FOLLOW_SPREAD_DEGREES = 12.0
		private const val FOLLOW_YAW_STEP_DEGREES = 2.0
		private const val FOLLOW_MAX_DELAY = 4
	}
}
