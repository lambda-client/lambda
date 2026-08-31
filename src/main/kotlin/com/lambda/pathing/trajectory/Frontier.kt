package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.core.center
import java.util.PriorityQueue
import kotlin.math.atan2
import kotlin.math.floor

/**
 * When two bodies are the same body, for the purpose of deciding what happens next.
 *
 * This is the search's state abstraction, and getting it wrong is expensive in both
 * directions at once. The previous key was `stance x yaw/20 x horizontalSpeed/0.05 x
 * localX/0.125 x localZ/0.125 x velocityHeading/15 x 3 flags` -- 1.77 million cells per
 * stance, so no two anchors ever shared one and the beam merged nothing: a corpus run
 * created 24,698 anchors to build tapes that used about ninety of them. At the same time
 * it described no vertical state whatsoever. There was no `localY`, and `speedBucket` read
 * `velocity.horizontalLength()`, so a body rising through a cell at +0.4 and one falling
 * through it at -0.4 were the same key -- physically opposite futures the beam was free
 * to merge.
 *
 * So: every axis of position and velocity is represented, and each is bucketed coarsely
 * enough that anchors actually land together. Facing collapses into one direction field
 * rather than two correlated ones -- a moving body faces where it is going, and a stopped
 * one has no heading to speak of, so exactly one of the two carries information.
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
    private val publishedTip: () -> ValueAnchor? = { null },
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
     * Branches not worth deepening but still worth committing: their fork is close
     * enough to the cursor that a subtree grown on them dies with it, so they wait here
     * where the commit pool can still see them. A drained open list revives them --
     * they are the walk's escape hatch when the tip line cannot be extended.
     */
    private val reserve = ArrayList<OpenEntry>()

    private class BlockedAttempt(val anchor: ValueAnchor, val action: com.lambda.pathing.movement.TrajectoryDecision)

    private val blocked = ArrayList<BlockedAttempt>()

    private val buckets = HashMap<AnchorKey, MutableList<ValueAnchor>>()

    /** Accumulated merge outcomes per (origin bucket, movement, step): what keeps producing held states. */
    private val mergeOutcomes = HashMap<Triple<AnchorKey, com.lambda.pathing.core.MovementId, Stance?>, Double>()

    fun mergeSurcharge(anchor: ValueAnchor, movement: com.lambda.pathing.core.MovementId, step: Stance?): Double {
        if (searchConfig.mergeSurchargeTicks <= 0.0) return 0.0
        val accumulated = mergeOutcomes[Triple(keyOf(anchor), movement, step)] ?: return 0.0
        // The first merge is measurement, not yet a pattern: charging from the first
        // hit reshuffled the baseline (+16 frames) for the corpus's -8.
        return (accumulated - searchConfig.mergeSurchargeTicks).coerceAtLeast(0.0)
    }

    private fun noteMerge(child: ValueAnchor) {
        if (searchConfig.mergeSurchargeTicks <= 0.0) return
        val parent = child.parent ?: return
        val decision = child.decision ?: return
        mergeOutcomes.merge(
            Triple(keyOf(parent), decision.movement, decision.step),
            searchConfig.mergeSurchargeTicks,
        ) { a, b -> (a + b).coerceAtMost(MERGE_SURCHARGE_CAP_TICKS) }
    }
    private var insertionSequence = 0L

    val isExhausted: Boolean get() = open.isEmpty() && parked.isEmpty()
    val hasBlocked: Boolean get() = blocked.isNotEmpty()
    val hasOpen: Boolean get() = open.isNotEmpty()
    val hasParked: Boolean get() = parked.isNotEmpty()
    val parkedEntries: List<OpenEntry> get() = parked
    val reserveEntries: List<OpenEntry> get() = reserve
    val reserveSize: Int get() = reserve.size
    val openSize: Int get() = open.size
    val blockedSize: Int get() = blocked.size

    /**
     * Anchors refused admission because the body has already diverged from them.
     *
     * A continuing session prunes against its executed root; a session started fresh
     * from the same body has no root and prunes nothing. If the two differ sharply here,
     * the committed prefix is what is starving the search rather than the terrain.
     */
    var unreachableAdmissions = 0
        private set

    /**
     * What the beam actually does when an anchor is offered.
     *
     * Kept because the beam has twice been assumed to be working and twice been found
     * inert: a key too fine to collide means `frontierPerKey` never binds and merging
     * never happens, and nothing in the search says so out loud.
     */
    var anchorsAdmitted = 0
        private set
    var beamDominated = 0
        private set
    var beamEvicted = 0
        private set
    var beamCapped = 0
        private set

    /**
     * The shipping beam policy run in shadow while the real one is relaxed.
     *
     * The question it answers: run with a generous [ValueFieldSearchConfig.frontierPerKey],
     * mirror the strict policy here, and any winning-lineage anchor marked refused is one the
     * shipping beam would have thrown away and then paid to rediscover. Only the dominated and
     * capped refusals are lethal -- a bucket eviction leaves the anchor in the open queue.
     */
    var shadowRefusals = 0
        private set

    private val shadowBuckets = HashMap<AnchorKey, MutableList<ValueAnchor>>()
    private val nextCenters = HashMap<Stance, com.lambda.pathing.core.HorizontalPoint?>()
    private val shadowDead = java.util.IdentityHashMap<ValueAnchor, Boolean>()

    fun shadowRefusedDirectly(anchor: ValueAnchor): Boolean = shadowDead[anchor] == true

    val beamBuckets: Int get() = buckets.size
    val beamLargestBucket: Int get() = buckets.values.maxOfOrNull { it.size } ?: 0
    val parkedSize: Int get() = parked.size
    val deepestElapsed: Int get() =
        maxOf(open.maxOfOrNull { it.anchor.elapsed } ?: 0, parked.maxOfOrNull { it.anchor.elapsed } ?: 0)
    val openEntries: List<OpenEntry> get() = open.toList()

    var deepestProgress = 0
        private set

    fun poll(): OpenEntry? = open.poll()

    /** The deepest open entry, removed: the depth lane's pick. O(n), called sparingly. */
    fun pollDeepest(): OpenEntry? {
        val deepest = open.maxWithOrNull(
            compareBy({ it.anchor.elapsed }, { -it.order }, { -it.sequence }),
        ) ?: return null
        open.remove(deepest)
        return deepest
    }

    fun park(entry: OpenEntry) {
        parked += entry
    }

    fun starve(entry: OpenEntry) {
        reserve += entry
    }

    /**
     * One entry per drain, deliberately. Wholesale revival was measured in production
     * (600 expansions per body frame -- the search saturates its live subtree, so the
     * open list drains constantly) grinding every dead branch through its entire
     * vocabulary: 44k starve events against 27k admissions, and the churn ate the
     * surplus compute that just-in-time refinement lives on. Reviving only the
     * best-ordered survivor keeps the escape hatch at a price of one rollout per drain.
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
     * The guide the QUEUE ranks by: conditioned on the anchor's own speed class, so a
     * moving body's remaining estimate stops paying the transition tax its chain never
     * pays and a stopped one's includes its acceleration. Pruning never uses this --
     * the blended min stays the only safe lower bound.
     */
    private fun orderGuide(anchor: ValueAnchor): Double =
        field.guide(anchor.stance, SpeedClass.of(anchor.speed))

    fun offer(entry: OpenEntry) {
        enqueue(entry)
    }

    /**
     * Return an anchor to the queue after an attempt, re-scored from what it has left.
     *
     * This replaces a pair of fixed penalties -- a rejected attempt used to cost the
     * anchor 0.05 ticks and a successful one 3.0, so failure bought priority and an
     * anchor that kept failing stayed pinned at the head of the queue grinding its whole
     * vocabulary. The anchor's standing now follows the price of its cheapest remaining
     * movement, so it falls behind exactly as far as continuing there deserves.
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
        mergeOutcomes.clear()
        shadowBuckets.clear()
    }

    fun parkBlocked(anchor: ValueAnchor, action: com.lambda.pathing.movement.TrajectoryDecision) {
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
        if (searchConfig.beamShadowPerKey > 0) shadowAdmit(anchor, key)
        val bucket = buckets.getOrPut(key) { ArrayList() }
        if (searchConfig.frontierDomination != FrontierDomination.OFF) {
            val positionAware = searchConfig.frontierDomination == FrontierDomination.POSITION_AWARE
            if (bucket.any { it.preferredForBeamOver(anchor, positionAware) }) {
                beamDominated++
                noteMerge(anchor)
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

        // Pruned against the incumbent's SCORE with the anchor's own collision penalty:
        // collisions only accumulate, so this stays an admissible bound, and it is what
        // lets a clean landing survive an equal-length corner-catch incumbent -- with
        // bare frames the four-frame collision penalty could never buy anything back.
        incumbentScore()?.let {
            val bound = anchor.elapsed + guide +
                ValueFieldAnchorSearch.COLLISION_FRAME_PENALTY * anchor.collisionEvents
            if (bound >= it) return
        }

        enqueue(entryFor(anchor, guide))
    }

    fun progressOf(stance: Stance): Int = routeIndex[stance] ?: deepestProgress

    // The surcharge rides on `order` only. `bound` has to stay a true lower bound on the
    // arrival frame -- it is what licenses finalizing an incumbent -- and what a movement
    // costs the *search* is not time the body spends.
    private fun entryFor(anchor: ValueAnchor, guide: Double) = OpenEntry(
        order = anchor.elapsed + searchConfig.guideWeight * momentumAdjusted(anchor, guide) +
            anchor.pendingSurcharge - tipLineCredit(anchor),
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

    private fun nextCellCenter(stance: Stance): com.lambda.pathing.core.HorizontalPoint? {
        if (!nextCenters.containsKey(stance)) {
            nextCenters[stance] = field.steps(stance, 1).firstOrNull()?.to?.center()
        }
        return nextCenters[stance]
    }

    private fun tipLineCredit(anchor: ValueAnchor): Double {
        if (searchConfig.tipLineCreditTicks <= 0.0) return 0.0
        val tip = publishedTip() ?: return 0.0
        return if (anchor.descendsFrom(tip)) searchConfig.tipLineCreditTicks else 0.0
    }

    private fun momentumAdjusted(anchor: ValueAnchor, guide: Double): Double {
        val next = field.steps(anchor.stance, 1, heading = anchor.heading())
            .firstOrNull()?.to?.center() ?: return guide
        val alignment = headingAlignment(
            anchor.state.velocity.x, anchor.state.velocity.z,
            next.x - anchor.state.position.x, next.z - anchor.state.position.z,
        )
        val headingError = Math.toDegrees(kotlin.math.acos(alignment.coerceIn(-1.0, 1.0)))
        // The class-conditioned guide does NOT subsume this credit -- removing it was
        // measured at +27 baseline frames, seventeen new stall frames and a failed
        // field course. The class is one binary bit; the credit is continuous in speed
        // and alignment, and the queue needs both.
        return guide -
            momentumCredit(anchor.speed, alignment) +
            momentumTurnCost(anchor.speed, headingError, config.maxYawDegreesPerFrame)
    }

    private fun sector(degrees: Double, width: Double): Int =
        floor(((degrees % 360.0) + 360.0) % 360.0 / width).toInt()

    private fun localBucket(coordinate: Double): Int =
        floor((coordinate - floor(coordinate)) / POSITION_BUCKET_BLOCKS).toInt()

    private fun keyOf(anchor: ValueAnchor): AnchorKey {
        val state = anchor.state
        // A body with real momentum is characterised by where it is going; a stalled one
        // by where it is pointed, since that is what its next input will accelerate along.
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
        if (searchConfig.beamShadowPerKey > 0) {
            shadowBuckets.clear()
            (open.asSequence() + parked.asSequence())
                .filter { shadowDead[it.anchor] == null }
                .forEach { shadowBuckets.getOrPut(keyOf(it.anchor)) { ArrayList() } += it.anchor }
        }
    }

    private fun shadowAdmit(anchor: ValueAnchor, key: AnchorKey) {
        val parent = anchor.parent
        if (parent != null && shadowDead.containsKey(parent)) {
            shadowDead[anchor] = false
            return
        }
        val bucket = shadowBuckets.getOrPut(key) { ArrayList() }
        if (bucket.any { it.preferredForBeamOver(anchor) }) {
            shadowDead[anchor] = true
            shadowRefusals++
            return
        }
        bucket.removeAll { anchor.preferredForBeamOver(it) }
        if (bucket.size >= searchConfig.beamShadowPerKey) {
            val worst = bucket.maxByOrNull { it.elapsed } ?: return
            if (worst.elapsed <= anchor.elapsed) {
                shadowDead[anchor] = true
                shadowRefusals++
                return
            }
            bucket.remove(worst)
        }
        bucket += anchor
    }

    private companion object {

        const val SPEED_DOMINANCE_SLACK = 0.01

        /** Ceiling on the accumulated merge surcharge: a delay, never a wall. */
        const val MERGE_SURCHARGE_CAP_TICKS = 6.0

        /** Within-bucket positions differ by a quarter block at most; ties go to the earlier body. */
        const val POSITION_DOMINANCE_SLACK = 0.02

        /**
         * How finely two bodies on the same stance must differ to count as different.
         *
         * Too fine to prune walking and, at the same time, too coarse to describe a climb.
         * At an eighth of a block the key space runs to six figures per stance, so
         * `frontierPerKey` caps nothing on open ground -- a production parkour run carried
         * ~2,400 live anchors on a nineteen-node route, nearly all of them the same body a
         * hair apart. Widening these to half a block and forty-five degrees duly cut
         * search work by 9% and then hung `LadderExecutionTest`: a body on a vine rises
         * about 0.117 blocks a tick with no horizontal speed and a fixed yaw, and this key
         * has no vertical sub-block component at all, so a whole within-block climb
         * already collapses toward a single bucket.
         *
         * Pruning the frontier properly means keying on what actually separates states --
         * vertical position included -- rather than on wider versions of these. Left at
         * the values that work until then.
         */
        /** Quarter of a block on every axis: fine enough to matter at a jump lip. */
        const val POSITION_BUCKET_BLOCKS = 0.25

        /** One sector per 30 degrees. Twelve directions describe a body's intent. */
        const val DIRECTION_SECTOR_DEGREES = 30.0

        /**
         * Below this the velocity heading is numerical noise and the body's facing is
         * what decides where it goes next.
         */
        const val DIRECTION_FROM_HEADING_SPEED = 0.02

        /**
         * Vertical velocity resolution. Jump launch is 0.42 and a settled fall is well
         * past -1, so this separates rising, hanging and falling without splitting hairs.
         */
        const val VERTICAL_SPEED_BUCKET = 0.15
    }
}
