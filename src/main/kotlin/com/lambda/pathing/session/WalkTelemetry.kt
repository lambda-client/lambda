package com.lambda.pathing.session

import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.search.PublishedPath
import net.minecraft.util.math.Vec3d

data class Telemetry(
	val published: PublishedPath?,
	val maxDeviation: Double,
	val adopted: Int,
	val recoveries: Int,
	val rejectedImprovements: Int,
	val trail: List<Vec3d>,
	val holds: Int = 0,
	val repairs: Int = 0,
	val pushes: Int = 0,
	val leg: Int = 0,
	val queuedWaypoints: Int = 0,
	val sessionRestarts: Int = 0,
	val cadence: String = "",
	val rehearsal: RehearsalView? = null,
) {
	companion object {
		val EMPTY = Telemetry(
			published = null, maxDeviation = 0.0, adopted = 0, recoveries = 0,
			rejectedImprovements = 0, trail = emptyList(),
		)
	}
}

internal class WalkTelemetry {
	@Volatile
	var published: PublishedPath? = null
		set(value) {
			field = value
			cached = null
		}

	var maxDeviation: Double = 0.0
		private set

	var adopted: Int = 0
		private set

	var recoveries: Int = 0
		private set

	var rejectedImprovements: Int = 0
		private set

	var holds: Int = 0
		private set

	var repairs: Int = 0
		private set

	var pushes: Int = 0
		private set

	private val trail = ArrayDeque<Vec3d>()
	private var adoptedFrames = 0
	private var adoptionMillis = 0L
	private var lastAdoptedFrames = 0
	private var lastAdoptionMillis = System.currentTimeMillis()

	@Volatile
	private var cached: Telemetry? = null

	private val lock = Any()

	private inline fun update(change: () -> Unit) = synchronized(lock) {
		change()
		cached = null
	}

	fun recordTrail(position: Vec3d, deviation: Double) = update {
		if (trail.size == MAX_RETAINED_TRAIL_POINTS) trail.removeFirst()
		trail.addLast(position)
		maxDeviation = maxOf(maxDeviation, deviation)
	}

	fun countAdoption(tapeFrames: Int) = update {
		val now = System.currentTimeMillis()
		adoptedFrames += (tapeFrames - lastAdoptedFrames).coerceAtLeast(0)
		adoptionMillis += (now - lastAdoptionMillis).coerceAtLeast(0)
		lastAdoptedFrames = tapeFrames
		lastAdoptionMillis = now
		adopted++
	}

	fun countHold() = update { holds++ }

	@Volatile
	var verifiedChanges: Int = 0
		private set

	fun countVerifiedChange() = update { verifiedChanges++ }

	fun countRepair() = update { repairs++ }

	fun countRecovery() = update { recoveries++ }

	fun countPush() = update { pushes++ }

	fun countRejectedImprovement() = update { rejectedImprovements++ }

	fun snapshot(): Telemetry {
		cached?.let { return it }
		return synchronized(lock) {
			cached ?: Telemetry(
				published = published,
				maxDeviation = maxDeviation,
				adopted = adopted,
				recoveries = recoveries,
				rejectedImprovements = rejectedImprovements,
				trail = ArrayList(trail),
				holds = holds,
				repairs = repairs,
				pushes = pushes,
				cadence = cadence(),
			).also { cached = it }
		}
	}

	fun cadence(): String = synchronized(lock) {
		if (adopted == 0) return "no adoptions"
		val millis = adoptionMillis.coerceAtLeast(1L)
		"%d adoption(s) added %d frames over %d ms (%.1f frames/s produced vs %d consumed)"
			.format(adopted, adoptedFrames, millis, adoptedFrames * 1000.0 / millis, Vanilla.TICKS_PER_SECOND)
	}

	companion object {
		const val MAX_RETAINED_TRAIL_POINTS = 4_096

	}
}
