package com.lambda.pathing.search

import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.Stance

/**
 * Movement difficulty the search currently buys, and the stall evidence that buys it:
 * stalls heat the [temperature] first and buy guide reach second; guide progress cools
 * it; every executed root re-earns the evidence. [actionsEpoch] bumps whenever the
 * vocabulary an anchor was priced against may have changed, invalidating cached action
 * lists. See docs/decisions/annealing.md.
 */
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

    /** Guide reach bought since the last executed root. */
    var guideExpansions = 0
        private set

    private var progressBaseline = Double.POSITIVE_INFINITY
    private var bestGuideSeen = Double.POSITIVE_INFINITY
    private var expansionsSinceGuideProgress = 0
    private var expansionsSinceHeat = 0
    private var escalationRoot: ValueAnchor? = null

    /** One expansion of stall evidence. */
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

    /**
     * Buy the search more room after a stall: heat first (cheap, local), then guide reach.
     * Returns whether anything was bought. See docs/decisions/annealing.md.
     */
    fun escalate(force: Boolean): Boolean {
        recovery.retainReachable()
        // Blocked attempts are not stall evidence; a streaming walk exhausts its subtree routinely.
        if (!force && frontier.hasBlocked) return false

        // Forced heat takes no expansion quota: a drained anchor has no expansions left to spend.
        if (force || expansionsSinceHeat >= HEAT_EXPANSIONS) {
            expansionsSinceHeat = 0
            if (temperature.raise()) {
                actionsEpoch++
                recovery.revive()
                return true
            }
        }

        // Guide reach, on the full stall evidence; the quota is waived when the frontier is dry.
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

    /**
     * With motion in hand ([holdingMotion]: an incumbent or a published tip), buy
     * difficulty to shorten it: after [REFINEMENT_HEAT_EXPANSIONS] without improvement the
     * next tier of movements is unlocked so shortcuts can compete.
     */
    fun refine(holdingMotion: Boolean) {
        if (!holdingMotion) return
        if (expansionsSinceHeat < REFINEMENT_HEAT_EXPANSIONS) return
        expansionsSinceHeat = 0
        if (temperature.raise()) actionsEpoch++
    }

    /**
     * Each executed root starts a fresh escalation window: stall evidence is re-earned on
     * the terrain ahead, and temperature steps down one tier rather than resetting.
     * See docs/decisions/annealing.md.
     */
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

    /**
     * A tape restart re-earns guide evidence from [seed] and cools one tier; the heat
     * window and the escalation root are left alone. See docs/decisions/session-loop.md.
     */
    fun rewindForRestart(seed: Stance) {
        guideExpansions = 0
        temperature.cool()
        actionsEpoch++
        progressBaseline = Double.POSITIVE_INFINITY
        bestGuideSeen = Double.POSITIVE_INFINITY
        expansionsSinceGuideProgress = 0
        noteGuide(seed)
    }

    /** Whether a stall still has something left to buy. */
    fun canEscalate(): Boolean =
        !temperature.exhausted || guideExpansions < MAX_GUIDE_EXPANSIONS

    private companion object {
        /** Expansions of flat guide that buy guide reach; see docs/decisions/annealing.md. */
        const val ESCALATION_EXPANSIONS = 1500

        /** Expansions of flat guide that buy the next tier of movement difficulty. */
        const val HEAT_EXPANSIONS = 120

        /** Expansions without improvement, holding motion, before difficulty is bought to shorten it. */
        const val REFINEMENT_HEAT_EXPANSIONS = 600

        const val FORCE_ESCALATION_MIN_EXPANSIONS = 32

        /** Guide reach bought per stall, and how many such purchases a root may make. */
        const val GUIDE_EXPANSION_TICKS = 8.0
        const val MAX_GUIDE_EXPANSIONS = 3

        const val GUIDE_PROGRESS_HYSTERESIS_TICKS = 2.0
    }
}
