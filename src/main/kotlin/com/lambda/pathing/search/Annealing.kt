package com.lambda.pathing.search

import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.Stance

internal class Annealing(
	private val field: ValueField,
	private val frontier: Frontier,
	private val horizon: Horizon,
	private val surcharge: (ValueAnchor) -> Double?,
	maxTemperature: Double,
) {
	val temperature = Temperature(ceiling = maxTemperature)

	var actionsEpoch = 0
		private set

	private val spentAnchors = ArrayList<ValueAnchor>()

	val spent: List<ValueAnchor> get() = spentAnchors

	val hasSpent: Boolean get() = spentAnchors.isNotEmpty()

	private var progressBaseline = Double.POSITIVE_INFINITY
	private var bestGuideSeen = Double.POSITIVE_INFINITY
	private var expansionsSinceHeat = 0
	private var escalationRoot: ValueAnchor? = null

	fun tick() {
		expansionsSinceHeat++
	}

	fun noteGuide(stance: Stance) {
		val guide = field.guide(stance)
		if (guide < bestGuideSeen) bestGuideSeen = guide
		if (bestGuideSeen <= progressBaseline - GUIDE_PROGRESS_HYSTERESIS_TICKS) {
			progressBaseline = bestGuideSeen
			if (temperature.cool()) actionsEpoch++
		}
	}

	fun spend(anchor: ValueAnchor) {
		if (canEscalate()) spentAnchors += anchor
	}

	fun clearSpent() {
		spentAnchors.clear()
	}

	fun escalate(force: Boolean): Boolean {
		spentAnchors.retainAll { horizon.canReach(it) }
		if (!force && frontier.hasBlocked) return false
		if (!force && expansionsSinceHeat < HEAT_EXPANSIONS) return false
		expansionsSinceHeat = 0
		if (!temperature.raise()) return false
		actionsEpoch++
		spentAnchors.forEach { anchor ->
			surcharge(anchor)?.let { anchor.pendingSurcharge = it }
			frontier.reopen(anchor)
		}
		spentAnchors.clear()
		return true
	}

	fun rewindOnNewRoot() {
		val root = horizon.root ?: return
		if (root === escalationRoot) return
		escalationRoot = root
		expansionsSinceHeat = 0
		if (temperature.cool()) actionsEpoch++
		restartProgress(root.stance)
	}

	fun rewindForRestart(seed: Stance) {
		temperature.cool()
		actionsEpoch++
		restartProgress(seed)
	}

	private fun restartProgress(from: Stance) {
		progressBaseline = Double.POSITIVE_INFINITY
		bestGuideSeen = Double.POSITIVE_INFINITY
		noteGuide(from)
	}

	fun canEscalate(): Boolean = !temperature.exhausted

	private companion object {

		const val HEAT_EXPANSIONS = 120

		const val GUIDE_PROGRESS_HYSTERESIS_TICKS = 2.0
	}
}
