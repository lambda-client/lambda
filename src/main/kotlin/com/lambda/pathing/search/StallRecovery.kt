package com.lambda.pathing.search

internal class StallRecovery(
	private val frontier: Frontier,
	private val horizon: HorizonController,
	private val worldWait: ((Long) -> Boolean)?,
	private val syncWorld: () -> Unit,

	private val surcharge: (ValueAnchor) -> Double?,
	private val probe: SearchProbe,
) {

	private val spentAnchors = ArrayList<ValueAnchor>()

	val spent: List<ValueAnchor> get() = spentAnchors
	val hasSpent: Boolean get() = spentAnchors.isNotEmpty()

	fun spend(anchor: ValueAnchor) {
		spentAnchors += anchor
	}

	fun retainReachable() {
		spentAnchors.retainAll { horizon.canReach(it) }
	}

	fun revive() {
		spentAnchors.forEach { anchor ->
			surcharge(anchor)?.let { anchor.pendingSurcharge = it }
			frontier.reopen(anchor)
		}
		spentAnchors.clear()
	}

	private var blockedWaitMillis = 0L
	private var fruitlessWakes = 0

	fun resetWaitWindow() {
		blockedWaitMillis = 0
		fruitlessWakes = 0
	}

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

	private val restartedMoving = HashSet<Int>()

	private val restartedJunctions = HashSet<Int>()

	fun beginLeg() {
		tapeRestarts = 0
		restartedMoving.clear()
		restartedJunctions.clear()
		spentAnchors.clear()
	}

	fun restartable(): Boolean =
		tapeRestarts < MAX_TAPE_RESTARTS && horizon.latestBrakeContinuation() != null

	fun restartSeed(expansions: Int, drops: Int): ValueAnchor? {
		if (tapeRestarts >= MAX_TAPE_RESTARTS) return null

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
