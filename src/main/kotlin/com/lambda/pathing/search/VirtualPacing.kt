package com.lambda.pathing.search

import com.lambda.pathing.execution.ImprovementArbiter
import com.lambda.pathing.physics.Vanilla

internal class VirtualPacing(private val microsPerExpansion: Long) : SearchClock, ExecutionFeedback {
	private class Offer(val visibleAt: Int, val path: PublishedPath)

	private val queued = ArrayDeque<Offer>()
	private var expansions = 0L
	private var waitedMillis = 0L
	private var running: PublishedPath? = null
	private var began: Int? = null
	private var acked = 0L
	private var declined = 0L

	var holdTicks = 0L
		private set

	var firstInstall = -1
		private set

	override val simulated: Boolean get() = true

	override fun elapsedMillis(): Long = expansions * microsPerExpansion / 1_000 + waitedMillis

	override fun onExpansion() {
		expansions++
	}

	override fun onWait(millis: Long) {
		waitedMillis += millis
	}

	private fun tick(): Int = (elapsedMillis() / MILLIS_PER_TICK).toInt()

	fun offer(path: PublishedPath) {
		queued += Offer(tick() + ADOPTION_LATENCY_TICKS, path)
	}

	override fun adoptedSequence(): Long = acked

	override fun refusedSequence(): Long = declined

	override fun cursorFrame(): Int? {
		val now = tick()
		val tape = running
		val start = began
		if (tape != null && start != null) {
			val overrun = now - start - tape.plan.tape.frameCount
			if (overrun > 0) {
				holdTicks += overrun
				began = start + overrun
			}
		}
		while (queued.isNotEmpty() && queued.first().visibleAt <= now) deliver(queued.removeFirst().path, now)
		val current = running ?: return null
		val from = began ?: return null
		return (now - from).coerceIn(0, current.plan.tape.frameCount)
	}

	private fun deliver(path: PublishedPath, now: Int) {
		val current = running
		val cursor = current?.let { (now - (began ?: now)).coerceIn(0, it.plan.tape.frameCount) }
		when (val verdict = ImprovementArbiter.judge(running = current, cursorFrame = cursor, awaitingObservation = false, offered = path)) {
			ImprovementArbiter.Verdict.BeginFresh -> {
				running = path
				began = now
				if (firstInstall < 0) firstInstall = now
				acked = path.publicationSequence.toLong()
			}

			is ImprovementArbiter.Verdict.Adopt -> {
				running = path
				acked = path.publicationSequence.toLong()
			}

			is ImprovementArbiter.Verdict.Keep -> declined = maxOf(declined, path.publicationSequence.toLong())

			ImprovementArbiter.Verdict.DeferForObservation -> Unit
		}
	}

	private companion object {
		const val MILLIS_PER_TICK = 1000L / Vanilla.TICKS_PER_SECOND

		const val ADOPTION_LATENCY_TICKS = 1
	}
}
