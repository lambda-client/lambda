package com.lambda.pathing.session

internal class PhaseTimings {
	private val started = System.nanoTime()
	private val nanos = LinkedHashMap<String, Long>()

	@Volatile
	var current: String? = null
		private set

	fun <T> measure(phase: String, block: () -> T): T {
		val begin = System.nanoTime()
		current = phase
		try {
			return block()
		} finally {
			current = null
			nanos.merge(phase, System.nanoTime() - begin, Long::plus)
		}
	}

	val elapsedMillis: Long get() = (System.nanoTime() - started) / 1_000_000L

	override fun toString(): String =
		nanos.entries.joinToString(", ", postfix = "; total $elapsedMillis ms") { (phase, nanos) ->
			"$phase ${nanos / 1_000_000L} ms"
		}
}
