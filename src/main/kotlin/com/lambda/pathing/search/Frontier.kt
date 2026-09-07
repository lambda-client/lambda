package com.lambda.pathing.search

import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.core.Stance
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.core.center
import java.util.PriorityQueue
import kotlin.math.atan2
import kotlin.math.floor
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.actions.TrajectoryDecision

/**
 * The beam's state abstraction: two anchors with the same key are the same body for the
 * purpose of deciding what happens next. Every axis of position and velocity is bucketed
 * coarsely enough that anchors actually collide; facing is one direction field (velocity
 * heading when moving, yaw when stopped). Bucket sizes: docs/decisions/beam.md.
 */
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
    private val field: CoarseValueField,
    private val config: MotionConstraints,
    private val searchConfig: ValueFieldSearchConfig,
    private var routeIndex: Map<Stance, Int>,
    private val incumbentScore: () -> Int?,
) {
    var reachability: ReachabilityPolicy = ReachabilityPolicy { true }
    class OpenEntry(
        val order: Double,
        val bound: Double,
        val anchor: ValueAnchor,
        internal var sequence: Long = Long.MAX_VALUE,
    ) {
        /** Revived from the starved reserve: expand it rather than re-starving it forever. */
        internal var starveExempt = false
    }

    private val open = PriorityQueue<OpenEntry>(
        compareBy<OpenEntry>({ it.order }, { it.bound }, { it.anchor.elapsed }, { it.sequence })
    )
    private val parked = ArrayList<OpenEntry>()

    /**
     * Fork-starved branches: not worth deepening (their subtree dies with a fork near the
     * cursor) but still visible to the commit pool, and revived when the open list drains.
     */
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

    /**
     * Anchors refused admission because the body has already diverged from them. A high
     * count relative to a fresh session means the committed prefix, not the terrain, is
     * starving the search.
     */
    var unreachableAdmissions = 0
        private set

    /** Beam outcomes per offered anchor; reported so an inert beam is visible. See docs/decisions/beam.md. */
    var anchorsAdmitted = 0
        private set
    var beamDominated = 0
        private set
    var beamEvicted = 0
        private set
    var beamCapped = 0
        private set

    private val nextCenters = HashMap<Stance, HorizontalPoint?>()

    val beamBuckets: Int get() = buckets.size
    val beamLargestBucket: Int get() = buckets.values.maxOfOrNull { it.size } ?: 0
    val parkedSize: Int get() = parked.size
    val deepestElapsed: Int get() =
        maxOf(open.maxOfOrNull { it.anchor.elapsed } ?: 0, parked.maxOfOrNull { it.anchor.elapsed } ?: 0)
    val openEntries: List<OpenEntry> get() = open.toList()

    /**
     * The best-ordered live entry deeper than [floor], open list first (heap order) then
     * the reserve, without copying either. Ties go to the first met, exactly as
     * the former `(open + reserve).filter { .. }.minByOrNull { it.order }` broke them.
     */
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

    /**
     * Revive the best-ordered live reserve entry -- one per drain, so the escape hatch
     * costs one rollout per drain. See docs/decisions/publication-protocol.md.
     */
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

    /**
     * The guide the queue ranks by, conditioned on the anchor's own speed class. Pruning
     * never uses this; the blended minimum is the only safe lower bound.
     */
    private fun orderGuide(anchor: ValueAnchor): Double =
        field.guide(anchor.stance, SpeedClass.of(anchor.speed))

    fun offer(entry: OpenEntry) {
        enqueue(entry)
    }

    /**
     * Return an anchor to the queue after an attempt, re-scored at the price of its
     * cheapest remaining movement (carried in `pendingSurcharge`). See docs/decisions/beam.md.
     */
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

    /** Forget every anchor below [root] (not [root] itself): their rollouts read a world that is gone. */
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
        nextCenters.clear()
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
        val woken = java.util.IdentityHashMap<ValueAnchor, Unit>()
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
        if (searchConfig.frontierDomination != FrontierDomination.OFF) {
            val positionAware = searchConfig.frontierDomination == FrontierDomination.POSITION_AWARE
            if (bucket.any { it.preferredForBeamOver(anchor, positionAware) }) {
                beamDominated++
                return
            }
            val before = bucket.size
            bucket.removeAll { anchor.preferredForBeamOver(it, positionAware) }
            beamEvicted += before - bucket.size
        }
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

        // Pruned against the incumbent's score including the anchor's own collision
        // penalty; collisions only accumulate, so the bound stays admissible.
        incumbentScore()?.let {
            val bound = anchor.elapsed + guide +
                Solution.COLLISION_FRAME_PENALTY * anchor.collisionEvents
            if (bound >= it) return
        }

        enqueue(entryFor(anchor, guide))
    }

    fun progressOf(stance: Stance): Int = routeIndex[stance] ?: deepestProgress

    // The surcharge rides on `order` only; `bound` must stay a true lower bound on arrival
    // because it licenses finalizing an incumbent.
    private fun entryFor(anchor: ValueAnchor, guide: Double) = OpenEntry(
        order = anchor.elapsed + momentumAdjusted(anchor, guide) + anchor.pendingSurcharge,
        bound = anchor.elapsed +
            (field.lowerBound(anchor.stance) - MOMENTUM_CREDIT_MAX_TICKS).coerceAtLeast(0.0),
        anchor = anchor,
    )

    private fun ValueAnchor.preferredForBeamOver(
        other: ValueAnchor,
        positionAware: Boolean = false,
    ): Boolean =
        elapsed <= other.elapsed &&
            speed >= other.speed - SPEED_DOMINANCE_SLACK &&
            collisionEvents <= other.collisionEvents &&
            inputSwitches <= other.inputSwitches &&
            (!positionAware || atLeastAsCloseToNextCell(other))

    private fun ValueAnchor.atLeastAsCloseToNextCell(other: ValueAnchor): Boolean {
        val next = nextCellCenter(stance) ?: return true
        return kotlin.math.hypot(state.position.x - next.x, state.position.z - next.z) <=
            kotlin.math.hypot(other.state.position.x - next.x, other.state.position.z - next.z) +
            POSITION_DOMINANCE_SLACK
    }

    private fun nextCellCenter(stance: Stance): HorizontalPoint? {
        if (!nextCenters.containsKey(stance)) {
            nextCenters[stance] = field.steps(stance, 1).firstOrNull()?.to?.center()
        }
        return nextCenters[stance]
    }

    /**
     * Bumped by every [rescore]: the field's guide values are frozen between rescores
     * (every invalidation is followed by one), so anything derived from them can be
     * cached on the anchor against this epoch.
     */
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
        // The class-conditioned guide does not subsume this credit; see docs/decisions/beam.md.
        return guide - credit + anchor.momentumTurnCost
    }

    private fun sector(degrees: Double, width: Double): Int =
        floor(((degrees % 360.0) + 360.0) % 360.0 / width).toInt()

    private fun localBucket(coordinate: Double): Int =
        floor((coordinate - floor(coordinate)) / POSITION_BUCKET_BLOCKS).toInt()

    private fun keyOf(anchor: ValueAnchor): AnchorKey {
        val state = anchor.state
        // A moving body is characterised by where it is going, a stalled one by where it points.
        val direction = if (anchor.speed > DIRECTION_FROM_HEADING_SPEED) {
            sector(Math.toDegrees(atan2(state.velocity.z, state.velocity.x)), DIRECTION_SECTOR_DEGREES)
        } else {
            sector(state.rotation.yaw, DIRECTION_SECTOR_DEGREES)
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

        /** Within-bucket positions differ by a quarter block at most; ties go to the earlier body. */
        const val POSITION_DOMINANCE_SLACK = 0.02

        /** Position bucket on every axis; half a block collapses slow climbs. See docs/decisions/beam.md. */
        const val POSITION_BUCKET_BLOCKS = 0.25

        /** One sector per 30 degrees. */
        const val DIRECTION_SECTOR_DEGREES = 30.0

        /** Below this speed the velocity heading is noise and facing decides the direction. */
        const val DIRECTION_FROM_HEADING_SPEED = 0.02

        /** Vertical velocity bucket: separates rising, hanging and falling. */
        const val VERTICAL_SPEED_BUCKET = 0.15
    }
}
