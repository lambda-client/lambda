package com.lambda.pathing.search

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

internal class RolloutCrew(workers: Int, private val rollouts: AnchorRollout) {
	private class Round(val batch: List<AnchorRollout.PreparedRollout>) {
		val next = AtomicInteger()
		val unfinished = AtomicInteger(batch.size)
	}

	@Volatile
	private var round: Round? = null

	@Volatile
	private var closed = false

	private val threads = List(workers) { index ->
		Thread({ work(adaptive = index >= HOT_WORKERS) }, "PathPlanner-rollout-$index").apply {
			isDaemon = true
			start()
		}
	}

	fun run(batch: List<AnchorRollout.PreparedRollout>) {
		val current = Round(batch)
		round = current
		threads.forEach(LockSupport::unpark)
		claim(current)
		while (current.unfinished.get() > 0) Thread.onSpinWait()
	}

	fun close() {
		closed = true
		threads.forEach(LockSupport::unpark)
	}

	private fun work(adaptive: Boolean) {
		var seen: Round? = null
		var limit = MAX_SPINS
		while (!closed) {
			var spins = 0
			var current = round
			while (current === seen && !closed) {
				if (spins < limit) {
					spins++
					Thread.onSpinWait()
				} else LockSupport.park(this)
				current = round
			}
			if (adaptive) limit = if (spins < limit) minOf(limit * 2, MAX_SPINS) else maxOf(limit / 2, MIN_SPINS)
			seen = current
			if (current != null) claim(current)
		}
	}

	private fun claim(current: Round) {
		while (true) {
			val index = current.next.getAndIncrement()
			if (index >= current.batch.size) return
			try {
				rollouts.execute(current.batch[index])
			} finally {
				current.unfinished.decrementAndGet()
			}
		}
	}

	private companion object {
		const val HOT_WORKERS = 3

		const val MIN_SPINS = 250

		const val MAX_SPINS = 20_000
	}
}
