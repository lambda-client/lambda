package com.lambda.pathing.search

internal class StallRecovery(
	private val frontier: Frontier,
	private val publications: Publications,
	private val world: WorldLink?,
) {
	private var blockedWaitMillis = 0L
	private var fruitlessWakes = 0

	fun resetWaitWindow() {
		blockedWaitMillis = 0
		fruitlessWakes = 0
	}

	fun mayAwaitBlocked(): Boolean {
		if (frontier.hasParked || !frontier.hasBlocked || world == null) return false
		if (blockedWaitMillis >= MAX_BLOCKED_WAIT_MILLIS || fruitlessWakes >= MAX_FRUITLESS_WAKES) return false
		blockedWaitMillis += WAIT_SLICE_MILLIS
		return true
	}

	fun noteWake(productive: Boolean) {
		if (productive) fruitlessWakes = 0 else fruitlessWakes++
	}

	fun mayAwaitRouteExtension(): Boolean {
		if (world == null) return false
		publications.cursor()?.let { cursor -> return publications.running.let { it != null && cursor < it.brake.elapsed } }
		if (blockedWaitMillis >= MAX_BLOCKED_WAIT_MILLIS) return false
		blockedWaitMillis += WAIT_SLICE_MILLIS
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
		val seed = publications.tip?.continuation()?.takeIf { publications.adoptable(it) && restartedMoving.add(it.elapsed) }
			?: publications.running?.holdAnchor((publications.cursor() ?: 0) + Publications.HOLD_LEAD_FRAMES)?.restingChild()?.takeIf(publications::adoptable)
			?: return null
		tapeRestarts++
		return seed
	}

	companion object {
		const val WAIT_SLICE_MILLIS = 200L
		private const val MAX_BLOCKED_WAIT_MILLIS = 4_000L
		private const val MAX_FRUITLESS_WAKES = 2
		private const val MAX_TAPE_RESTARTS = 5
	}
}
