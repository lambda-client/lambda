package com.lambda.pathing.session

internal class PhaseTimings {
	private val started = System.nanoTime()
	private val nanos = LinkedHashMap<String, Long>()

	fun <T> measure(phase: String, block: () -> T): T {
		val begin = System.nanoTime()
		try {
			return block()
		} finally {
			nanos.merge(phase, System.nanoTime() - begin, Long::plus)
		}
	}

	override fun toString(): String =
		nanos.entries.joinToString(", ", postfix = "; total ${(System.nanoTime() - started) / 1_000_000L} ms") { (phase, nanos) ->
			"$phase ${nanos / 1_000_000L} ms"
		}
}
