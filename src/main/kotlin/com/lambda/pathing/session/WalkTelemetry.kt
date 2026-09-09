package com.lambda.pathing.session

import com.lambda.pathing.search.PublishedPath
import net.minecraft.util.math.Vec3d

data class Telemetry(
	val published: PublishedPath?,
	val maxDeviation: Double,
	val adopted: Int,
	val recoveries: Int,
	val rejectedImprovements: Int,

	val executed: List<PublishedPath>,

	val trail: List<Vec3d>,

	val holds: Int = 0,
	val repairs: Int = 0,
	val leg: Int = 0,
	val queuedWaypoints: Int = 0,
	val sessionRestarts: Int = 0,

	val cadence: String = "",
) {
	companion object {
		val EMPTY = Telemetry(
			published = null, maxDeviation = 0.0, adopted = 0, recoveries = 0,
			rejectedImprovements = 0, executed = emptyList(), trail = emptyList(),
		)
	}
}

internal class WalkTelemetry {
	@Volatile
	var published: PublishedPath? = null
		set(value) {
			field = value
			invalidate()
		}

	var maxDeviation: Double = 0.0
		private set

	var adopted: Int = 0
		private set

	var recoveries: Int = 0
		private set

	var rejectedImprovements: Int = 0
		private set

	private val executedPaths = ArrayDeque<PublishedPath>()
	private val trail = ArrayDeque<Vec3d>()

	@Volatile
	private var cached: Telemetry? = null

	private val lock = Any()

	private fun invalidate() {
		cached = null
	}

	fun recordExecuted(path: PublishedPath) = synchronized(lock) {
		if (executedPaths.size == MAX_RETAINED_PUBLICATIONS) executedPaths.removeFirst()
		executedPaths.addLast(path)
		invalidate()
	}

	fun recordTrail(position: Vec3d, deviation: Double) = synchronized(lock) {
		if (trail.size == MAX_RETAINED_TRAIL_POINTS) trail.removeFirst()
		trail.addLast(position)
		maxDeviation = maxOf(maxDeviation, deviation)
		invalidate()
	}

	fun countAdoption() = synchronized(lock) {
		adopted++
		invalidate()
	}

	fun countRecovery() = synchronized(lock) {
		recoveries++
		invalidate()
	}

	fun countRejectedImprovement() = synchronized(lock) {
		rejectedImprovements++
		invalidate()
	}

	fun snapshot(): Telemetry {
		cached?.let { return it }
		return synchronized(lock) {
			cached ?: Telemetry(
				published = published,
				maxDeviation = maxDeviation,
				adopted = adopted,
				recoveries = recoveries,
				rejectedImprovements = rejectedImprovements,
				executed = ArrayList(executedPaths),
				trail = ArrayList(trail),
			).also { cached = it }
		}
	}

	companion object {
		const val MAX_RETAINED_PUBLICATIONS = 256
		const val MAX_RETAINED_TRAIL_POINTS = 4_096
	}
}
