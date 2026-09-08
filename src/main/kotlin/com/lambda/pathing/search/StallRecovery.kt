package com.lambda.pathing.search

/**
 * What the session does when the open list runs dry short of the goal: revive anchors
 * whose vocabulary an escalation widened, wait on the world while only blocked attempts
 * remain, and restart from the published tape's continuation. Blocked-wait ladder and
 * restart evidence: docs/decisions/session-loop.md.
 */
internal class StallRecovery(
    private val frontier: Frontier,
    private val horizon: HorizonController,
    private val worldWait: ((Long) -> Boolean)?,
    private val syncWorld: () -> Unit,
    /** The price of the cheapest movement an anchor has left, or null when it has none. */
    private val surcharge: (ValueAnchor) -> Double?,
    private val probe: SearchProbe,
) {
    // Anchors whose corridor-level vocabulary is exhausted; revived when it widens.
    private val spentAnchors = ArrayList<ValueAnchor>()

    val spent: List<ValueAnchor> get() = spentAnchors
    val hasSpent: Boolean get() = spentAnchors.isNotEmpty()

    fun spend(anchor: ValueAnchor) {
        spentAnchors += anchor
    }

    fun retainReachable() {
        spentAnchors.retainAll { horizon.canReach(it) }
    }

    /**
     * Return the anchors that ran out of vocabulary to the queue, re-priced: the
     * escalation that revived them is what put new movements within reach.
     */
    fun revive() {
        spentAnchors.forEach { anchor ->
            surcharge(anchor)?.let { anchor.pendingSurcharge = it }
            frontier.reopen(anchor)
        }
        spentAnchors.clear()
    }

    private var blockedWaitMillis = 0L
    private var fruitlessWakes = 0

    /** Publication is progress: the wait ladder starts over. */
    fun resetWaitWindow() {
        blockedWaitMillis = 0
        fruitlessWakes = 0
    }

    /**
     * With only blocked attempts left, wait one slice for the world and wake them.
     * Returns whether a slice was spent (the caller re-evaluates the frontier).
     */
    fun waitForBlocked(): Boolean {
        if (frontier.hasParked || !frontier.hasBlocked || worldWait == null) return false
        if (blockedWaitMillis >= MAX_BLOCKED_WAIT_MILLIS || fruitlessWakes >= MAX_FRUITLESS_WAKES) return false
        blockedWaitMillis += BLOCKED_WAIT_SLICE_MILLIS
        if (worldWait.invoke(BLOCKED_WAIT_SLICE_MILLIS)) {
            syncWorld()
            frontier.wakeBlocked()

            if (frontier.hasOpen) fruitlessWakes = 0 else fruitlessWakes++
        }
        return true
    }

    /**
     * A truncated route cannot finalize: wait one slice for it to extend instead of
     * refusing. Returns whether a slice was spent.
     */
    fun waitForRouteExtension(): Boolean {
        if (worldWait == null || blockedWaitMillis >= MAX_BLOCKED_WAIT_MILLIS) return false
        blockedWaitMillis += BLOCKED_WAIT_SLICE_MILLIS
        worldWait.invoke(BLOCKED_WAIT_SLICE_MILLIS)
        syncWorld()
        frontier.wakeBlocked()
        return true
    }

    var tapeRestarts = 0
        private set

    // Tip frames already restarted from while moving; a second restart there concedes to the brake.
    private val restartedMoving = HashSet<Int>()

    // Junction frames already restarted from; each junction restart walks one junction further back.
    private val restartedJunctions = HashSet<Int>()

    /**
     * A new leg of a compound route: the restart budget, the restart memory and the spent
     * anchors all belonged to the leg just finished. Without this a five-leg route shares
     * one budget of [MAX_TAPE_RESTARTS] and dies "exhausted" on its last leg.
     */
    fun beginLeg() {
        tapeRestarts = 0
        restartedMoving.clear()
        restartedJunctions.clear()
        spentAnchors.clear()
    }

    fun restartable(): Boolean =
        tapeRestarts < MAX_TAPE_RESTARTS && horizon.latestBrakeContinuation() != null

    /**
     * The continuation anchor a drained search restarts from, or null when none is left:
     * the moving tip first (restarting onto the brake commits the body to halting), the
     * resting brake otherwise. Records the restart and forgets the spent anchors; the
     * caller re-roots and rewinds its own windows. See docs/decisions/session-loop.md.
     */
    fun restartSeed(expansions: Int, drops: Int): ValueAnchor? {
        if (tapeRestarts >= MAX_TAPE_RESTARTS) return null
        // Tip first (same line, fresh attempts), then one junction back per restart (a
        // different line into the obstacle, the plan DAG's cut), the brake last.
        val moving = horizon.movingTipContinuation()?.takeIf { restartedMoving.add(it.elapsed) }
        val seed = moving
            ?: horizon.junctionContinuation(restartedJunctions)
            ?: horizon.latestBrakeContinuation()
            ?: return null
        probe.restarted(
            moving = moving != null,
            seedElapsed = seed.elapsed,
            executing = horizon.observedCursor,
            expansions = expansions,
            drops = drops,
            spent = spentAnchors.size,
        )
        tapeRestarts++
        spentAnchors.clear()
        return seed
    }

    private companion object {
        const val BLOCKED_WAIT_SLICE_MILLIS = 200L
        const val MAX_BLOCKED_WAIT_MILLIS = 4_000L
        const val MAX_FRUITLESS_WAKES = 2
        const val MAX_TAPE_RESTARTS = 5
    }
}
