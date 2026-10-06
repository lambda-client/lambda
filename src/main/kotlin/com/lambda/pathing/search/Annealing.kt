package com.lambda.pathing.search

import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.Stance

internal class Annealing(
	private val field: ValueField,
	private val frontier: Frontier,
	private val horizon: HorizonController,
	private val recovery: StallRecovery,
	maxTemperature: Double,
) {
	val temperature = Temperature(ceiling = maxTemperature)

	var actionsEpoch = 0
		private set

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

	fun escalate(force: Boolean): Boolean {
		recovery.retainReachable()
		if (!force && frontier.hasBlocked) return false
		if (!force && expansionsSinceHeat < HEAT_EXPANSIONS) return false
		expansionsSinceHeat = 0
		if (!temperature.raise()) return false
		actionsEpoch++
		recovery.revive()
		return true
	}

	fun refine(holdingMotion: Boolean) {
		if (!holdingMotion) return
		if (expansionsSinceHeat < REFINEMENT_HEAT_EXPANSIONS) return
		expansionsSinceHeat = 0
		if (temperature.raise()) actionsEpoch++
	}

	fun rewindOnNewRoot() {
		val root = horizon.reachableRoot ?: return
		if (root === escalationRoot) return
		escalationRoot = root
		progressBaseline = Double.POSITIVE_INFINITY
		bestGuideSeen = Double.POSITIVE_INFINITY
		expansionsSinceHeat = 0
		if (temperature.cool()) actionsEpoch++
		noteGuide(root.stance)
	}

	fun rewindForRestart(seed: Stance) {
		temperature.cool()
		actionsEpoch++
		progressBaseline = Double.POSITIVE_INFINITY
		bestGuideSeen = Double.POSITIVE_INFINITY
		noteGuide(seed)
	}

	fun canEscalate(): Boolean = !temperature.exhausted

	private companion object {

		const val HEAT_EXPANSIONS = 120

		const val REFINEMENT_HEAT_EXPANSIONS = 600

		const val GUIDE_PROGRESS_HYSTERESIS_TICKS = 2.0
	}
}
