package com.lambda.pathing.search

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

internal class RolloutCrew(workers: Int, private val execute: (AnchorRollout.PreparedRollout) -> Unit) {
	private class Round(val batch: List<AnchorRollout.PreparedRollout>) {
		val next = AtomicInteger()
		val unfinished = AtomicInteger(batch.size)
	}

	@Volatile
	private var round: Round? = null

	@Volatile
	private var closed = false

	private val threads = List(workers) { index ->
		Thread({ work() }, "PathPlanner-rollout-$index").apply {
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

	private fun work() {
		var seen: Round? = null
		while (!closed) {
			var spins = 0
			var current = round
			while (current === seen && !closed) {
				if (++spins < SPIN_LIMIT) Thread.onSpinWait() else LockSupport.park(this)
				current = round
			}
			seen = current
			if (current != null) claim(current)
		}
	}

	private fun claim(current: Round) {
		while (true) {
			val index = current.next.getAndIncrement()
			if (index >= current.batch.size) return
			try {
				execute(current.batch[index])
			} finally {
				current.unfinished.decrementAndGet()
			}
		}
	}

	private companion object {
		const val SPIN_LIMIT = 20_000
	}
}
