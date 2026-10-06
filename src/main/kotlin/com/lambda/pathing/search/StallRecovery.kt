package com.lambda.pathing.search

internal class StallRecovery(
	private val frontier: Frontier,
	private val publications: Publications,
	private val world: WorldLink?,
	private val syncWorld: () -> Unit,
) {
	private var blockedWaitMillis = 0L
	private var fruitlessWakes = 0

	fun resetWaitWindow() {
		blockedWaitMillis = 0
		fruitlessWakes = 0
	}

	fun waitForBlocked(): Boolean {
		if (frontier.hasParked || !frontier.hasBlocked || world == null) return false
		if (blockedWaitMillis >= MAX_BLOCKED_WAIT_MILLIS || fruitlessWakes >= MAX_FRUITLESS_WAKES) return false
		blockedWaitMillis += BLOCKED_WAIT_SLICE_MILLIS
		if (world.await(BLOCKED_WAIT_SLICE_MILLIS)) {
			syncWorld()
			frontier.wakeBlocked()

			if (frontier.hasOpen) fruitlessWakes = 0 else fruitlessWakes++
		}
		return true
	}

	fun waitForRouteExtension(): Boolean {
		if (world == null || blockedWaitMillis >= MAX_BLOCKED_WAIT_MILLIS) return false
		blockedWaitMillis += BLOCKED_WAIT_SLICE_MILLIS
		world.await(BLOCKED_WAIT_SLICE_MILLIS)
		syncWorld()
		frontier.wakeBlocked()
		return true
	}

	var tapeRestarts = 0
		private set

	private val restartedMoving = HashSet<Int>()

	fun beginLeg() {
		tapeRestarts = 0
		restartedMoving.clear()
	}

	fun restartable(): Boolean = tapeRestarts < MAX_TAPE_RESTARTS && publications.running != null

	fun restartSeed(): ValueAnchor? {
		if (tapeRestarts >= MAX_TAPE_RESTARTS) return null
		val seed = publications.tip?.continuation()?.takeIf { restartedMoving.add(it.elapsed) }
			?: publications.running?.brake?.continuation()
			?: return null
		tapeRestarts++
		return seed
	}

	private companion object {
		const val BLOCKED_WAIT_SLICE_MILLIS = 200L
		const val MAX_BLOCKED_WAIT_MILLIS = 4_000L
		const val MAX_FRUITLESS_WAKES = 2
		const val MAX_TAPE_RESTARTS = 5
	}
}
