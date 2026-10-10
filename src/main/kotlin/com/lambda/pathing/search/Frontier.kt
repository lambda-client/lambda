package com.lambda.pathing.search

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.center
import com.lambda.pathing.core.headingAlignment
import java.util.*
import kotlin.math.acos
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

internal class Frontier(
	private val field: ValueField,
	private val config: MotionConstraints,
	private val searchConfig: SearchConfig,
	private var routeIndex: Map<Stance, Int>,
	private val window: Int,
) {
	class OpenEntry(
		val order: Double,
		val bound: Double,
		val anchor: ValueAnchor,
		val sequence: Long,
	)

	private val open = PriorityQueue(
		compareBy<OpenEntry>({ it.order }, { it.bound }, { it.anchor.elapsed }, { it.sequence })
	)
	private val parked = ArrayList<OpenEntry>()

	private class BlockedAttempt(val anchor: ValueAnchor, val action: TrajectoryDecision)

	private val blocked = ArrayList<BlockedAttempt>()

	private val buckets = HashMap<AnchorKey, MutableList<ValueAnchor>>()

	private var perKey = searchConfig.frontierPerKey

	private var insertionSequence = 0L

	val isExhausted: Boolean get() = open.isEmpty() && parked.isEmpty()
	val hasBlocked: Boolean get() = blocked.isNotEmpty()
	val hasOpen: Boolean get() = open.isNotEmpty()
	val hasParked: Boolean get() = parked.isNotEmpty()
	val parkedEntries: List<OpenEntry> get() = parked
	val openSize: Int get() = open.size
	val blockedSize: Int get() = blocked.size

	var anchorsAdmitted = 0
		private set
	var beamMerged = 0
		private set

	val parkedSize: Int get() = parked.size
	val openEntries: List<OpenEntry> get() = open.toList()

	fun bestLiveEntryDeeperThan(floor: Int): OpenEntry? =
		open.asSequence().filter { it.anchor.elapsed > floor }.minByOrNull { it.order }

	var deepestProgress = 0
		private set

	var root: ValueAnchor? = null
		private set

	var end = if (window > 0) window else Int.MAX_VALUE
		private set

	fun canReach(anchor: ValueAnchor): Boolean = root?.let { anchor.descendsFrom(it) } ?: true

	fun follow(publications: Publications) {
		val cursor = publications.cursor() ?: return
		publications.dropRefused()
		val running = publications.running ?: return
		val executing = cursor.coerceAtMost(running.brake.elapsed)
		if (executing > running.anchor.elapsed) {
			val held = root?.takeIf {
				it === running.brake || it.elapsed > running.anchor.elapsed && running.brake.descendsFrom(it) &&
						it.elapsed >= executing + Publications.DIVERGENCE_MARGIN_FRAMES
			}
			if (held == null) reRoot(running.holdAnchor(executing + Publications.HOLD_LEAD_FRAMES))
			return
		}
		val reached = running.anchor.ancestorAtOrBefore(executing) ?: return
		val current = root
		if (current == null || (reached !== current && reached.descendsFrom(current))) reRoot(reached)
	}

	fun reRoot(anchor: ValueAnchor) {
		root = anchor
		retainDescendants(anchor)
		reopen(anchor)
		if (window > 0) end = anchor.elapsed + window
		repartition(anchor, end)
	}

	val widenable: Boolean get() = perKey < searchConfig.frontierPerKey * MAX_WIDENING

	fun widen() {
		perKey *= 2
	}

	fun extend(): Boolean {
		if (window <= 0 || parked.isEmpty()) return false
		val before = open.size
		end += window
		repartition(root, end)
		return open.size > before
	}

	fun poll(): OpenEntry? = open.poll()

	fun park(entry: OpenEntry) {
		parked += entry
	}

	fun reopen(anchor: ValueAnchor) {
		if (!canReach(anchor)) return
		if (open.any { it.anchor === anchor }) return
		reoffer(anchor)
	}

	fun reoffer(anchor: ValueAnchor) {
		val guide = field.guideFor(anchor).takeIf { it.isFinite() } ?: return
		open += entryFor(anchor, guide)
	}

	private fun retainDescendants(root: ValueAnchor) {
		val retained = open.filterTo(ArrayList()) { it.anchor.descendsFrom(root) }
		open.clear()
		open.addAll(retained)
		blocked.retainAll { it.anchor.descendsFrom(root) }
	}

	fun clear() {
		open.clear()
		parked.clear()
		blocked.clear()
		buckets.clear()
	}

	fun dropDescendants(root: ValueAnchor) {
		fun stale(anchor: ValueAnchor) = anchor !== root && anchor.descendsFrom(root)
		val kept = open.filterTo(ArrayList()) { !stale(it.anchor) }
		open.clear()
		open.addAll(kept)
		parked.removeAll { stale(it.anchor) }
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
			val guide = field.guideFor(entry.anchor)
			if (guide.isFinite()) open += entryFor(entry.anchor, guide, entry.sequence)
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

	private fun repartition(root: ValueAnchor?, horizonEnd: Int) {
		val surviving = parked.filter { root == null || it.anchor.descendsFrom(root) }
		parked.clear()
		surviving.forEach { entry ->
			if (entry.anchor.elapsed < horizonEnd) open += entry else parked += entry
		}
		rebuildBeamBuckets()
	}

	fun admit(anchor: ValueAnchor, incumbentScore: Int?) {
		val guide = field.guideFor(anchor)
			.takeIf { it.isFinite() }
			?: if (anchor.parent == null) field.lowerBound(anchor.stance) else return
		deepestProgress = maxOf(deepestProgress, progressOf(anchor.stance))

		if (!canReach(anchor)) return
		val key = keyOf(anchor)

		anchorsAdmitted++
		val bucket = buckets.getOrPut(key) { ArrayList() }

		if (bucket.any { it.preferredForBeamOver(anchor) }) {
			beamMerged++
			return
		}
		val before = bucket.size
		bucket.removeAll { anchor.preferredForBeamOver(it) }
		beamMerged += before - bucket.size
		if (bucket.size >= perKey) {
			val worst = bucket.maxBy { it.elapsed }
			beamMerged++
			if (worst.elapsed <= anchor.elapsed) return
			bucket.remove(worst)
		}
		bucket += anchor

		incumbentScore?.let {
			if (anchor.arrivalEstimate(guide) >= it) return
		}

		open += entryFor(anchor, guide)
	}

	private fun progressOf(stance: Stance): Int = routeIndex[stance] ?: deepestProgress

	private fun entryFor(anchor: ValueAnchor, guide: Double, sequence: Long = insertionSequence++) = OpenEntry(
		order = anchor.elapsed + momentumAdjusted(anchor, guide) + anchor.pendingSurcharge,
		bound = anchor.elapsed +
				(field.lowerBound(anchor.stance) - MOMENTUM_CREDIT_MAX_TICKS).coerceAtLeast(0.0),
		anchor = anchor,
		sequence = sequence,
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
				val headingError = Math.toDegrees(acos(alignment.coerceIn(-1.0, 1.0)))
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
			speed = floor(anchor.speed / SPEED_BUCKET_BLOCKS).toInt(),
			verticalSpeed = floor(state.velocity.y / VERTICAL_SPEED_BUCKET).toInt(),
			direction = direction,
			airborne = !state.onGround,
			sprinting = state.isSprinting,
			hazardKnown = anchor.hazardFrame != null,
		)
	}

	private fun rebuildBeamBuckets() {
		buckets.clear()
		(open.asSequence() + parked.asSequence()).forEach { entry ->
			buckets.getOrPut(keyOf(entry.anchor)) { ArrayList() } += entry.anchor
		}
	}

	private companion object {

		const val MAX_WIDENING = 8

		const val SPEED_DOMINANCE_SLACK = 0.01

		const val POSITION_BUCKET_BLOCKS = 0.25

		const val SPEED_BUCKET_BLOCKS = 0.075

		const val DIRECTION_SECTOR_DEGREES = 30.0

		const val DIRECTION_FROM_HEADING_SPEED = 0.02

		const val VERTICAL_SPEED_BUCKET = 0.15
	}
}
