package com.lambda.pathing.search

import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.physics.MovementSimulationState

internal class Publication(
	val sequence: Long,
	val anchor: ValueAnchor,
	val brake: ValueAnchor,
	val plan: MotionPlanResult.Success,
)

internal class Publications(
	private val clock: SearchClock,
	private val execution: ExecutionFeedback,
) {
	private val published = ArrayDeque<Publication>()
	private var sequence = 0L
	private var unadoptedSinceMillis: Long? = null

	var observedCursor: Int = -1
		private set

	val newest: Publication? get() = published.lastOrNull()

	val tip: ValueAnchor? get() = newest?.anchor

	val running: Publication? get() = published.lastOrNull { it.sequence <= adopted() }

	val upToDate: Boolean get() = newest.let { it == null || adopted() >= it.sequence }

	fun adopted(): Long = execution.adoptedSequence()

	fun cursor(): Int? = execution.cursorFrame()?.also { observedCursor = it }

	fun record(anchor: ValueAnchor, brake: ValueAnchor, plan: MotionPlanResult.Success) {
		running?.let { current -> published.removeAll { it.sequence < current.sequence } }
		published += Publication(++sequence, anchor, brake, plan)
	}

	fun expireUnadopted() {
		if (upToDate) {
			unadoptedSinceMillis = null
			return
		}
		val now = clock.elapsedMillis()
		val since = unadoptedSinceMillis
		if (since == null) {
			unadoptedSinceMillis = now
			return
		}
		if (now - since < ADOPTION_TIMEOUT_MILLIS) return
		unadoptedSinceMillis = null
		val adopted = adopted()
		while (published.lastOrNull()?.let { it.sequence > adopted } == true) published.removeLast()
	}

	fun adoptable(anchor: ValueAnchor): Boolean {
		val executing = cursor() ?: return true
		val divergence = divergence(anchor)
		return divergence >= anchor.elapsed || divergence - executing >= DIVERGENCE_MARGIN_FRAMES
	}

	fun finalWindowClosing(anchor: ValueAnchor): Boolean {
		val executing = cursor() ?: return false
		val divergence = divergence(anchor)
		return divergence != Int.MAX_VALUE && divergence - executing < DIVERGENCE_MARGIN_FRAMES + FINAL_FORK_HEADROOM_FRAMES
	}

	fun divergence(anchor: ValueAnchor): Int {
		val running = running ?: return Int.MAX_VALUE
		if (anchor.divergenceSequence == running.sequence) return anchor.divergenceElapsed
		val divergence = when {
			!anchor.descendsFrom(running.brake) -> forkFrame(running.anchor, anchor)
			running.brake.state.resting -> Int.MAX_VALUE
			else -> running.brake.elapsed
		}
		anchor.divergenceElapsed = divergence
		anchor.divergenceSequence = running.sequence
		return divergence
	}

	private var lineOf: ValueAnchor? = null
	private val line = HashSet<ValueAnchor>()

	private fun forkFrame(running: ValueAnchor, candidate: ValueAnchor): Int {
		if (lineOf !== running) {
			line.clear()
			generateSequence(running) { it.parent }.forEach { line += it }
			lineOf = running
		}
		return generateSequence(candidate) { it.parent }.firstOrNull { it in line }?.elapsed ?: 0
	}

	fun repairCut(mutations: Set<VoxelPos>, chunks: Set<PathingChunk>): ValueAnchor? {
		if (mutations.isEmpty() && chunks.isEmpty()) return null
		val newest = newest ?: return null
		val plan = newest.plan.plan
		val first = plan.firstFrameReading(mutations, chunks) ?: return null
		val frame = plan.segments.repairFrame(first, maxOf(observedCursor, 0), DIVERGENCE_MARGIN_FRAMES) ?: return null
		return newest.anchor.ancestorAt(frame)
	}

	companion object {
		const val DIVERGENCE_MARGIN_FRAMES = 3

		private const val FINAL_FORK_HEADROOM_FRAMES = 12

		private const val ADOPTION_TIMEOUT_MILLIS = 150L
	}
}

private val MovementSimulationState.resting: Boolean
	get() = onGround && jumpingCooldown == 0 && velocity.x == 0.0 && velocity.z == 0.0
