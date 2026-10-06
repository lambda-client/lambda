package com.lambda.pathing.search

import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.Stance

internal class Annealing(
	private val field: ValueField,
	private val frontier: Frontier,
	private val vocabulary: ActionSet,
	private val budget: ExpansionBudget,
	private val maxTemperature: Double,
) {
	var temperature = INITIAL_TEMPERATURE
		private set

	private val exhausted: Boolean get() = temperature >= maxTemperature

	var actionsEpoch = 0
		private set

	private val spentAnchors = ArrayList<ValueAnchor>()

	val spent: List<ValueAnchor> get() = spentAnchors

	val revivable: Boolean get() = spentAnchors.isNotEmpty() && !exhausted

	private var progressBaseline = Double.POSITIVE_INFINITY
	private var bestGuideSeen = Double.POSITIVE_INFINITY
	private var heatedAt = 0
	private var escalationRoot: ValueAnchor? = null

	fun next(anchor: ValueAnchor): TrajectoryDecision? = vocabulary.next(anchor, temperature, actionsEpoch)

	fun surcharge(anchor: ValueAnchor): Double? = vocabulary.nextSurcharge(anchor, temperature, actionsEpoch)

	fun noteGuide(stance: Stance) {
		val guide = field.guide(stance)
		if (guide < bestGuideSeen) bestGuideSeen = guide
		if (bestGuideSeen <= progressBaseline - GUIDE_PROGRESS_HYSTERESIS_TICKS) {
			progressBaseline = bestGuideSeen
			if (cool()) actionsEpoch++
		}
	}

	fun spend(anchor: ValueAnchor) {
		if (!exhausted) spentAnchors += anchor
	}

	fun clearSpent() {
		spentAnchors.clear()
	}

	fun heat() {
		spentAnchors.retainAll { frontier.canReach(it) }
		if (frontier.hasBlocked || budget.count - heatedAt < HEAT_EXPANSIONS) return
		escalate()
	}

	fun revive(): Boolean {
		if (spentAnchors.isEmpty()) return false
		spentAnchors.retainAll { frontier.canReach(it) }
		return escalate()
	}

	private fun escalate(): Boolean {
		heatedAt = budget.count
		if (!raise()) return false
		actionsEpoch++
		spentAnchors.forEach { anchor ->
			surcharge(anchor)?.let { anchor.pendingSurcharge = it }
			frontier.reopen(anchor)
		}
		spentAnchors.clear()
		return true
	}

	fun rewindOnNewRoot() {
		val root = frontier.root ?: return
		if (root === escalationRoot) return
		escalationRoot = root
		heatedAt = budget.count
		if (cool()) actionsEpoch++
		restartProgress(root.stance)
	}

	fun rewindForRestart(seed: Stance) {
		cool()
		actionsEpoch++
		restartProgress(seed)
	}

	private fun raise(): Boolean {
		if (exhausted) return false
		temperature = (temperature * GROWTH).coerceAtMost(maxTemperature)
		return true
	}

	private fun cool(): Boolean {
		if (temperature <= INITIAL_TEMPERATURE) return false
		temperature = (temperature / GROWTH).coerceAtLeast(INITIAL_TEMPERATURE)
		return true
	}

	private fun restartProgress(from: Stance) {
		progressBaseline = Double.POSITIVE_INFINITY
		bestGuideSeen = Double.POSITIVE_INFINITY
		noteGuide(from)
	}

	private companion object {
		const val INITIAL_TEMPERATURE = 0.3

		const val GROWTH = 1.6

		const val HEAT_EXPANSIONS = 120

		const val GUIDE_PROGRESS_HYSTERESIS_TICKS = 2.0
	}
}
