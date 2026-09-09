package com.lambda.pathing.search

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.center
import java.util.*
import kotlin.math.atan2
import kotlin.math.floor

private data class AnchorKey(
	val stance: Stance,
	val localX: Int,
	val localY: Int,
	val localZ: Int,
	val speed: Int,
	val verticalSpeed: Int,
	val direction: Int,
	val airborne: Boolean,
	val sprinting: Boolean,
	val hazardKnown: Boolean,
)

internal fun interface ReachabilityPolicy {
	fun canReach(anchor: ValueAnchor): Boolean
}

internal class Frontier(
	private val field: ValueField,
	private val config: MotionConstraints,
	private val searchConfig: ValueFieldSearchConfig,
	private var routeIndex: Map<Stance, Int>,
	private val incumbentScore: () -> Int?,
) {
	var reachability: ReachabilityPolicy = ReachabilityPolicy { true }

	var probe: SearchProbe = SearchProbe.NONE

	class OpenEntry(
		val order: Double,
		val bound: Double,
		val anchor: ValueAnchor,
		internal var sequence: Long = Long.MAX_VALUE,
	) {

		internal var starveExempt = false
	}

	private val open = PriorityQueue(
		compareBy<OpenEntry>({ it.order }, { it.bound }, { it.anchor.elapsed }, { it.sequence })
	)
	private val parked = ArrayList<OpenEntry>()

	private val reserve = ArrayList<OpenEntry>()

	private class BlockedAttempt(val anchor: ValueAnchor, val action: TrajectoryDecision)

	private val blocked = ArrayList<BlockedAttempt>()

	private val buckets = HashMap<AnchorKey, MutableList<ValueAnchor>>()

	private var insertionSequence = 0L

	val isExhausted: Boolean get() = open.isEmpty() && parked.isEmpty()
	val hasBlocked: Boolean get() = blocked.isNotEmpty()
	val hasOpen: Boolean get() = open.isNotEmpty()
	val hasParked: Boolean get() = parked.isNotEmpty()
	val parkedEntries: List<OpenEntry> get() = parked
	val openSize: Int get() = open.size
	val blockedSize: Int get() = blocked.size

	var unreachableAdmissions = 0
		private set

	var anchorsAdmitted = 0
		private set
	var beamDominated = 0
		private set
	var beamEvicted = 0
		private set
	var beamCapped = 0
		private set

	val beamBuckets: Int get() = buckets.size
	val beamLargestBucket: Int get() = buckets.values.maxOfOrNull { it.size } ?: 0
	val parkedSize: Int get() = parked.size
	val deepestElapsed: Int
		get() =
			maxOf(open.maxOfOrNull { it.anchor.elapsed } ?: 0, parked.maxOfOrNull { it.anchor.elapsed } ?: 0)
	val openEntries: List<OpenEntry> get() = open.toList()

	fun bestLiveEntryDeeperThan(floor: Int): OpenEntry? {
		var best: OpenEntry? = null
		for (entry in open) {
			if (entry.anchor.elapsed <= floor) continue
			if (best == null || best.order.compareTo(entry.order) > 0) best = entry
		}
		for (entry in reserve) {
			if (entry.anchor.elapsed <= floor) continue
			if (best == null || best.order.compareTo(entry.order) > 0) best = entry
		}
		return best
	}

	var deepestProgress = 0
		private set

	fun poll(): OpenEntry? = open.poll()

	fun park(entry: OpenEntry) {
		parked += entry
	}

	fun starve(entry: OpenEntry) {
		reserve += entry
	}

	fun reviveStarved(alive: (ValueAnchor) -> Boolean): Boolean {
		while (reserve.isNotEmpty()) {
			val best = reserve.minByOrNull { it.order } ?: return false
			reserve.remove(best)
			if (!alive(best.anchor)) continue
			best.starveExempt = true
			open += best
			return true
		}
		return false
	}

	fun reopen(anchor: ValueAnchor) {
		if (!reachability.canReach(anchor)) return
		if (open.any { it.anchor === anchor }) return
		val guide = orderGuide(anchor).takeIf { it.isFinite() } ?: return
		enqueue(entryFor(anchor, guide))
	}

	private fun orderGuide(anchor: ValueAnchor): Double =
		field.guide(anchor.stance, SpeedClass.of(anchor.speed))

	fun offer(entry: OpenEntry) {
		enqueue(entry)
	}

	fun reoffer(anchor: ValueAnchor) {
		val guide = orderGuide(anchor).takeIf { it.isFinite() } ?: return
		enqueue(entryFor(anchor, guide))
	}

	fun retainDescendants(root: ValueAnchor) {
		val retained = open.filterTo(ArrayList()) { it.anchor.descendsFrom(root) }
		open.clear()
		open.addAll(retained)
		blocked.retainAll { it.anchor.descendsFrom(root) }
		reserve.retainAll { it.anchor.descendsFrom(root) }
		buckets.clear()
	}

	fun retainLineage(root: ValueAnchor) {
		retainDescendants(root)
		parked.retainAll { it.anchor.descendsFrom(root) }
	}

	fun dropDescendants(root: ValueAnchor) {
		fun stale(anchor: ValueAnchor) = anchor !== root && anchor.descendsFrom(root)
		val kept = open.filterTo(ArrayList()) { !stale(it.anchor) }
		open.clear()
		open.addAll(kept)
		parked.removeAll { stale(it.anchor) }
		reserve.removeAll { stale(it.anchor) }
		blocked.removeAll { stale(it.anchor) }
		rebuildBeamBuckets()
	}

	fun parkBlocked(anchor: ValueAnchor, action: TrajectoryDecision) {
		blocked += BlockedAttempt(anchor, action)
	}

	fun updateRoute(newIndex: Map<Stance, Int>) {
		routeIndex = newIndex
		deepestProgress = 0
		open.forEach { deepestProgress = maxOf(deepestProgress, progressOf(it.anchor.stance)) }
		parked.forEach { deepestProgress = maxOf(deepestProgress, progressOf(it.anchor.stance)) }
		rescore()
	}

	fun rescore() {
		fieldEpoch++
		val entries = open.toList()
		open.clear()
		entries.forEach { entry ->
			val guide = orderGuide(entry.anchor)
			if (guide.isFinite()) {
				val fresh = entryFor(entry.anchor, guide)
				fresh.sequence = entry.sequence
				open += fresh
			}
		}
		rebuildBeamBuckets()
	}

	fun wakeBlocked() {
		if (blocked.isEmpty()) return
		val woken = IdentityHashMap<ValueAnchor, Unit>()
		blocked.forEach { attempt ->
			attempt.anchor.attempted.remove(attempt.action)
			woken[attempt.anchor] = Unit
		}
		blocked.clear()
		woken.keys.forEach { reopen(it) }
	}

	fun repartition(root: ValueAnchor, horizonEnd: Int) {
		val surviving = parked.filter { it.anchor.descendsFrom(root) }
		parked.clear()
		surviving.forEach { entry ->
			if (entry.anchor.elapsed < horizonEnd) open += entry else parked += entry
		}
		rebuildBeamBuckets()
	}

	fun admit(anchor: ValueAnchor) {
		val guide = orderGuide(anchor)
			.takeIf { it.isFinite() }
			?: if (anchor.parent == null) field.lowerBound(anchor.stance) else return
		deepestProgress = maxOf(deepestProgress, progressOf(anchor.stance))

		val key = keyOf(anchor)
		if (!reachability.canReach(anchor)) {
			unreachableAdmissions++
			return
		}

		anchorsAdmitted++
		val bucket = buckets.getOrPut(key) { ArrayList() }

		if (bucket.any { it.preferredForBeamOver(anchor) }) {
			beamDominated++
			return
		}
		val before = bucket.size
		bucket.removeAll { anchor.preferredForBeamOver(it) }
		beamEvicted += before - bucket.size
		if (bucket.size >= searchConfig.frontierPerKey) {
			val worst = bucket.maxByOrNull { it.elapsed } ?: return
			if (worst.elapsed <= anchor.elapsed) {
				beamCapped++
				return
			}
			bucket.remove(worst)
			beamEvicted++
		}
		bucket += anchor

		incumbentScore()?.let {
			val bound = anchor.elapsed + guide +
					Solution.COLLISION_FRAME_PENALTY * anchor.collisionEvents
			if (bound >= it) return
		}

		enqueue(entryFor(anchor, guide))
	}

	fun progressOf(stance: Stance): Int = routeIndex[stance] ?: deepestProgress

	private fun entryFor(anchor: ValueAnchor, guide: Double) = OpenEntry(
		order = anchor.elapsed + momentumAdjusted(anchor, guide) + anchor.pendingSurcharge,
		bound = anchor.elapsed +
				(field.lowerBound(anchor.stance) - MOMENTUM_CREDIT_MAX_TICKS).coerceAtLeast(0.0),
		anchor = anchor,
	)

	private fun ValueAnchor.preferredForBeamOver(other: ValueAnchor): Boolean =
		elapsed <= other.elapsed &&
				speed >= other.speed - SPEED_DOMINANCE_SLACK &&
				collisionEvents <= other.collisionEvents &&
				inputSwitches <= other.inputSwitches

	private var fieldEpoch = 0

	private fun momentumAdjusted(anchor: ValueAnchor, guide: Double): Double {
		if (anchor.momentumEpoch != fieldEpoch) {
			anchor.momentumEpoch = fieldEpoch
			val next = field.steps(anchor.stance, 1, heading = anchor.heading())
				.firstOrNull()?.to?.center()
			if (next == null) {
				anchor.momentumCredit = Double.NaN
			} else {
				val alignment = headingAlignment(
					anchor.state.velocity.x, anchor.state.velocity.z,
					next.x - anchor.state.position.x, next.z - anchor.state.position.z,
				)
				val headingError = Math.toDegrees(kotlin.math.acos(alignment.coerceIn(-1.0, 1.0)))
				anchor.momentumCredit = momentumCredit(anchor.speed, alignment)
				anchor.momentumTurnCost = momentumTurnCost(anchor.speed, headingError, config.maxYawDegreesPerFrame)
			}
		}
		val credit = anchor.momentumCredit
		if (credit.isNaN()) return guide

		return guide - credit + anchor.momentumTurnCost
	}

	private fun sector(degrees: Double): Int =
		floor(((degrees % 360.0) + 360.0) % 360.0 / DIRECTION_SECTOR_DEGREES).toInt()

	private fun localBucket(coordinate: Double): Int =
		floor((coordinate - floor(coordinate)) / POSITION_BUCKET_BLOCKS).toInt()

	private fun keyOf(anchor: ValueAnchor): AnchorKey {
		val state = anchor.state

		val direction = if (anchor.speed > DIRECTION_FROM_HEADING_SPEED) {
			sector(Math.toDegrees(atan2(state.velocity.z, state.velocity.x)))
		} else {
			sector(state.rotation.yaw)
		}
		return AnchorKey(
			stance = anchor.stance,
			localX = localBucket(state.position.x),
			localY = localBucket(state.position.y),
			localZ = localBucket(state.position.z),
			speed = floor(anchor.speed / searchConfig.speedBucketBlocks).toInt(),
			verticalSpeed = floor(state.velocity.y / VERTICAL_SPEED_BUCKET).toInt(),
			direction = direction,
			airborne = !state.onGround,
			sprinting = state.isSprinting,
			hazardKnown = anchor.hazardFrame != null,
		)
	}

	private fun enqueue(entry: OpenEntry) {
		if (entry.sequence == Long.MAX_VALUE) entry.sequence = insertionSequence++
		open += entry
	}

	private fun rebuildBeamBuckets() {
		buckets.clear()
		(open.asSequence() + parked.asSequence()).forEach { entry ->
			buckets.getOrPut(keyOf(entry.anchor)) { ArrayList() } += entry.anchor
		}
	}

	private companion object {

		const val SPEED_DOMINANCE_SLACK = 0.01

		const val POSITION_BUCKET_BLOCKS = 0.25

		const val DIRECTION_SECTOR_DEGREES = 30.0

		const val DIRECTION_FROM_HEADING_SPEED = 0.02

		const val VERTICAL_SPEED_BUCKET = 0.15
	}
}
