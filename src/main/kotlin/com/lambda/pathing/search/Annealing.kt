package com.lambda.pathing.search

import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.Stance

internal class Annealing(
	private val field: ValueField,
	private val frontier: Frontier,
	private val horizon: HorizonController,
	private val recovery: StallRecovery,
	private val expandGuide: ((Double) -> Unit)?,
	maxTemperature: Double,
) {
	val temperature = Temperature(ceiling = maxTemperature)

	var actionsEpoch = 0
		private set

	var guideExpansions = 0
		private set

	private var progressBaseline = Double.POSITIVE_INFINITY
	private var bestGuideSeen = Double.POSITIVE_INFINITY
	private var expansionsSinceGuideProgress = 0
	private var expansionsSinceHeat = 0
	private var escalationRoot: ValueAnchor? = null

	fun tick() {
		expansionsSinceGuideProgress++
		expansionsSinceHeat++
	}

	fun noteGuide(stance: Stance) {
		val guide = field.guide(stance)
		if (guide < bestGuideSeen) bestGuideSeen = guide
		if (bestGuideSeen <= progressBaseline - GUIDE_PROGRESS_HYSTERESIS_TICKS) {
			progressBaseline = bestGuideSeen
			expansionsSinceGuideProgress = 0
			if (temperature.cool()) actionsEpoch++
		}
	}

	fun escalate(force: Boolean): Boolean {
		recovery.retainReachable()

		if (!force && frontier.hasBlocked) return false

		if (force || expansionsSinceHeat >= HEAT_EXPANSIONS) {
			expansionsSinceHeat = 0
			if (temperature.raise()) {
				actionsEpoch++
				recovery.revive()
				return true
			}
		}

		val dry = !frontier.hasOpen && !frontier.hasParked
		if (force && !dry && expansionsSinceGuideProgress < FORCE_ESCALATION_MIN_EXPANSIONS) return false
		if (!force && expansionsSinceGuideProgress < ESCALATION_EXPANSIONS) return false
		if (guideExpansions >= MAX_GUIDE_EXPANSIONS) return false
		guideExpansions++
		actionsEpoch++
		expansionsSinceGuideProgress = 0
		expandGuide?.invoke(GUIDE_EXPANSION_TICKS * guideExpansions)
		horizon.invalidateCommitMemo()
		field.clearGuideCache()
		frontier.rescore()
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
		expansionsSinceGuideProgress = 0
		expansionsSinceHeat = 0
		if (temperature.cool()) actionsEpoch++
		guideExpansions = 0
		noteGuide(root.stance)
	}

	fun rewindForRestart(seed: Stance) {
		guideExpansions = 0
		temperature.cool()
		actionsEpoch++
		progressBaseline = Double.POSITIVE_INFINITY
		bestGuideSeen = Double.POSITIVE_INFINITY
		expansionsSinceGuideProgress = 0
		noteGuide(seed)
	}

	fun canEscalate(): Boolean =
		!temperature.exhausted || guideExpansions < MAX_GUIDE_EXPANSIONS

	private companion object {

		const val ESCALATION_EXPANSIONS = 1500

		const val HEAT_EXPANSIONS = 120

		const val REFINEMENT_HEAT_EXPANSIONS = 600

		const val FORCE_ESCALATION_MIN_EXPANSIONS = 32

		const val GUIDE_EXPANSION_TICKS = 8.0
		const val MAX_GUIDE_EXPANSIONS = 3

		const val GUIDE_PROGRESS_HYSTERESIS_TICKS = 2.0
	}
}
