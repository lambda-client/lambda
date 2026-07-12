/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.pathing.maneuver

import com.lambda.interaction.managers.rotating.Rotation.Companion.rotationTo
import com.lambda.pathing.primitives.MoveRates
import com.lambda.pathing.primitives.MoveTable
import com.lambda.pathing.manager.TraversalHandle
import com.lambda.util.math.flooredBlockPos
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.MovementSimulator
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
import com.lambda.worldview.WorldView
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * WP3.2 landing-anchored maneuver discovery (research plan §4.4, notes
 * T2/T7) with lazy edge evaluation (LIS semantics, plan §5 upgrade table):
 * when the backward search first asks for the predecessors of a ledge node,
 * propose sprint-jump takeoffs beyond the template range after cheap
 * prefilters only, each carrying a provable lower-bound cost. The expensive
 * tick-accurate simulation runs later, exclusively for edges the search
 * actually selects onto the candidate path ([validatePathEdges]) — never
 * for every ledge the backward search expands. Because the optimistic cost
 * never exceeds the simulated cost, a path whose discovered edges have all
 * been validated is exactly the path the eager (validate-at-proposal)
 * planner would have selected; laziness moves work off the critical path
 * without changing route selection.
 *
 * Thread contract: this object is confined to the planner worker. Its
 * simulations read the session [WorldView] through a
 * [SnapshotSimulationEnvironment] and the immutable [PlayerPhysicsProfile]
 * — never the live world or the live player entity (a worker read of either
 * is torn state; the July 2026 async revert traces to exactly that).
 *
 * Landing-anchored on purpose: D* Lite expands backward from the goal, so
 * predecessor-directed discovery is the search's native direction, and
 * each landing is examined at most once per world state (memoized; a
 * nearby block change re-opens it through [invalidateAround]).
 */
class ManeuverDiscovery(
    private val profile: PlayerPhysicsProfile,
    private val view: WorldView,
    /**
     * Discovered maneuvers are shortcuts, not the completeness substrate.
     * Only propose takeoffs that make net progress from this traversal's
     * origin; walking templates remain free to detour in every direction.
     * This avoids proposing the half of the jump fan that points back behind
     * the agent during the expensive initial backward search.
     */
    private val preferredOrigin: FastVector? = null,
) {
    private val environment = SnapshotSimulationEnvironment(view)

    /** Per-landing discovery state; presence = landing was examined. */
    private val landings = HashMap<FastVector, LandingDiscovery>()

    /** takeoff → (landing → cost); mirror for successor queries. */
    private val edgesFrom = HashMap<FastVector, HashMap<FastVector, Double>>()

    /** (takeoff, landing) → intermediate landings, for chain macro-edges. */
    private val chainMids = HashMap<Pair<FastVector, FastVector>, List<FastVector>>()

    /**
     * Validated entry interval per accepted single-jump edge (T3). Only
     * momentum-critical edges appear here: an edge validated across the
     * whole field band publishes NO envelope, and the executor plays it
     * exactly as it did before envelopes existed.
     */
    private val entryEnvelopes = HashMap<Pair<FastVector, FastVector>, EntrySpeedEnvelope>()

    var landingsExamined = 0; private set
    var simulationsRun = 0; private set
    var edgesDiscovered = 0; private set
    var edgesValidated = 0; private set
    var edgesRejected = 0; private set

    /** Last chain-sim failure detail, for CHAIN_DEBUG logging only. */
    private var lastChainFailure: String = ""

    /**
     * Proposal bookkeeping for one landing. [fanCursor] walks the
     * longest-first single-jump candidate fan so a failed validation can
     * back-fill the next candidate — keeping the *live* per-landing edge
     * budget identical to what eager validation would have produced.
     */
    private class LandingDiscovery {
        var fanCursor = 0
        /** Simulations spent resolving this landing (budgeted). */
        var simsUsed = 0
        /** takeoff → cost; optimistic until removed from [optimistic]. */
        val edges = HashMap<FastVector, Double>()
        /**
         * Takeoffs whose physics has not been evaluated yet, in proposal
         * (= fan, longest-first) order so resolution validates in the same
         * sequence eager discovery did.
         */
        val optimistic = LinkedHashSet<FastVector>()
    }

    /**
     * Discovered incoming edges of [landing], proposing on first call.
     * Invoked from the graph's predecessor provider on the planner worker;
     * trigger gating and memoization keep it off the hot path.
     */
    fun predecessorsInto(landing: FastVector): Map<FastVector, Double> {
        landings[landing]?.let { return it.edges }
        if (!isLedge(landing)) {
            landings[landing] = EMPTY_LANDING
            return emptyMap()
        }

        landingsExamined++
        val discovery = LandingDiscovery()
        landings[landing] = discovery
        proposeSingleJumps(landing, discovery)
        proposeChains(landing, discovery)
        edgesDiscovered += discovery.edges.size
        return discovery.edges
    }

    /** Existing incoming discoveries without expanding a new landing fan. */
    fun knownPredecessorsInto(landing: FastVector): Map<FastVector, Double> =
        landings[landing]?.edges ?: emptyMap()

    /**
     * Walks the single-jump fan from the landing's cursor, admitting
     * prefilter-clean candidates with optimistic costs until the live-edge
     * budget is met. Offsets are ordered longest-first, so the
     * path-shortening jumps are proposed before the budget runs out.
     */
    private fun proposeSingleJumps(landing: FastVector, discovery: LandingDiscovery): Set<FastVector> {
        var added: HashSet<FastVector>? = null
        while (discovery.edges.size < MAX_EDGES_PER_LANDING && discovery.fanCursor < SINGLE_JUMP_FAN.size) {
            val (offset, rise) = SINGLE_JUMP_FAN[discovery.fanCursor++]
            // Ascending jumps (+1 landing) have shorter sprint reach;
            // descending ones keep the flat band (falling carries).
            if (rise < 0 && offset.distance > ASCEND_MAX_DISTANCE) continue
            // Flat 2-gaps are the gapJump template's own move; the 2.0–2.2
            // band exists for the rise classes no template represents. The
            // cardinal-2 ascend is likewise the rise-1 gapJump template.
            if (offset.distance < FLAT_MIN_DISTANCE && rise <= 0) continue
            val takeoff = fastVectorOf(landing.x - offset.dx, landing.y + rise, landing.z - offset.dz)
            if (takeoff in discovery.edges) continue
            if (!progressesFromOrigin(takeoff, landing)) continue
            if (!MoveTable.isStance(view, takeoff.x, takeoff.y, takeoff.z)) continue
            // Only consider real gaps: if every column under the jump line
            // is standable at takeoff level, walking is strictly cheaper and
            // the templates already connect it. Descending jumps additionally
            // require the chasm to be real at landing level — where a
            // walkable floor exists down there, the walk-off drop templates
            // already serve.
            if (!lineCrossesGap(takeoff, landing)) continue
            if (rise > 0 && !lineCrossesGapAtLevel(takeoff, landing, landing.y)) continue
            if (!arcPossiblyClear(takeoff, landing)) continue
            if (offset.distance >= MOMENTUM_RUNWAY_MIN_DISTANCE && !hasEntryRunway(takeoff, landing)) continue

            // Tiny axis-alignment epsilon: an angled jump and a straight
            // one often land on the same integer tick, and an arbitrary
            // tie-break zig-zags the path. Cardinal jumps also carry
            // symmetric lateral tolerance, so prefer them whenever the
            // tick cost truly ties.
            val tieBreak = AXIS_TIE_EPSILON * minOf(abs(offset.dx), abs(offset.dz))
            // The envelope risk surcharge must be in the OPTIMISTIC bound
            // too: penalizing only at validation put every envelope edge +2
            // over what the search assumed, and LazySP then walked the
            // entire extension fan edge by edge before settling (measured:
            // ~1s repair passes on exhaustive expect-failure searches).
            val envelopeRisk =
                if (isEnvelopeSamplingClass(takeoff, landing)) ENVELOPE_RISK_PENALTY_TICKS else 0.0
            registerEdge(landing, discovery, takeoff, optimisticJumpCost(offset.distance) + tieBreak + envelopeRisk)
            (added ?: HashSet<FastVector>().also { added = it }) += takeoff
        }
        return added ?: emptySet()
    }

    /**
     * WP3.3 chain proposals (v1: two colinear hops). Momentum carried
     * through a mid landing reaches takeoffs the single-jump band never
     * can — including 1-wide middles that only work with the policy's
     * mid-air braking, the class the single-jump envelope refuses. Chains
     * enter optimistically too: a chain-only connection must be visible to
     * the search before it can be selected for lazy validation.
     */
    private fun proposeChains(landing: FastVector, discovery: LandingDiscovery) {
        for (offset in CHAIN_OFFSETS) {
            val mid = fastVectorOf(landing.x - offset.dx, landing.y, landing.z - offset.dz)
            if (!MoveTable.isStance(view, mid.x, mid.y, mid.z)) continue
            if (!lineCrossesGap(mid, landing)) continue
            val takeoff = fastVectorOf(mid.x - offset.dx, mid.y, mid.z - offset.dz)
            if (takeoff in discovery.edges) continue
            if (!progressesFromOrigin(takeoff, landing)) continue
            if (!MoveTable.isStance(view, takeoff.x, takeoff.y, takeoff.z)) continue
            if (!lineCrossesGap(takeoff, mid)) continue
            if (!arcPossiblyClear(takeoff, mid) || !arcPossiblyClear(mid, landing)) continue

            val tieBreak = AXIS_TIE_EPSILON * minOf(abs(offset.dx), abs(offset.dz))
            registerEdge(landing, discovery, takeoff, 2.0 * optimisticJumpCost(offset.distance) + tieBreak)
            chainMids[takeoff to landing] = listOf(mid)
        }
    }

    private fun registerEdge(landing: FastVector, discovery: LandingDiscovery, takeoff: FastVector, cost: Double) {
        discovery.edges[takeoff] = cost
        discovery.optimistic += takeoff
        edgesFrom.getOrPut(takeoff) { HashMap() }[landing] = cost
    }

    /**
     * Admissible optimistic cost in ticks: horizontal displacement at the
     * measured sustained sprint-jump rate. Entry shaping, mid-air braking,
     * and landing settle always cost extra ticks on top, so the simulated
     * cost can only revise upward — the LazySP requirement that makes a
     * fully-validated path eager-equivalent.
     */
    private fun optimisticJumpCost(distance: Double): Double =
        distance / MoveRates.SPRINT_JUMP_BPT

    /** Discovered outgoing edges of [takeoff] (never triggers discovery). */
    fun successorsFrom(takeoff: FastVector): Map<FastVector, Double> =
        edgesFrom[takeoff] ?: emptyMap()

    /**
     * Whether [from] → [to] is a discovered jump edge. The executor keys
     * takeoff semantics on this: a discovered edge launches at the segment
     * start (the validated takeoff node — the sim jumps on tick 0 and flies
     * over any supported run-in), unlike refiner-merged template gaps whose
     * takeoff window anchors to the hole.
     */
    fun isDiscoveredJump(from: FastVector, to: FastVector): Boolean =
        edgesFrom[from]?.containsKey(to) == true

    /**
     * Intermediate landings of the discovered chain edge [from] → [to],
     * or null for single jumps and unknown pairs. The executor keys its
     * chain-policy playback on this.
     */
    fun chainWaypoints(from: FastVector, to: FastVector): List<FastVector>? = chainMids[from to to]

    /** True if any edge of [path] is discovered and not yet sim-validated. */
    fun hasOptimisticEdge(path: List<FastVector>): Boolean {
        for (index in 0 until path.lastIndex) {
            if (landings[path[index + 1]]?.optimistic?.contains(path[index]) == true) return true
        }
        return false
    }

    /**
     * Resolves every landing that [path] selects a not-yet-validated edge
     * into. Resolution is landing-granular on purpose: with lower-bound
     * optimism, correcting a single edge just makes the repair pick a
     * sibling candidate into the same landing, so edge-at-a-time validation
     * alternated once per sibling (408 repair cycles on the bedrock course).
     * Resolving the whole fan — validate each pending candidate, back-fill
     * proposals for failures, all under the landing's sim budget — keeps the
     * LazySP loop at one round per *new landing* the path visits, and the
     * surviving edge set is the one eager validation would have kept.
     * Returns the nodes whose edge sets changed for the planner's
     * synchronizeAffected pass; the caller loops until the selected path
     * carries no optimistic edge, at which point it is exactly the eager
     * planner's path and safe to publish.
     */
    fun validatePathEdges(path: List<FastVector>, horizon: Double? = null): Set<FastVector> {
        val affected = HashSet<FastVector>()
        val origin = path.firstOrNull() ?: return affected
        for (index in 0 until path.lastIndex) {
            val takeoff = path[index]
            val landing = path[index + 1]
            val discovery = landings[landing] ?: continue
            if (takeoff !in discovery.optimistic) continue
            // Horizon-limited passes (partial publications) only resolve
            // maneuvers the executor could imminently reach; the frontier
            // tail is re-routed by repair anyway, and validating it burned
            // a full compute slice per round while the player waited.
            if (horizon != null &&
                hypot((takeoff.x - origin.x).toDouble(), (takeoff.z - origin.z).toDouble()) > horizon
            ) {
                continue
            }
            resolveLanding(landing, discovery, affected)
        }
        return affected
    }

    private fun resolveLanding(
        landing: FastVector,
        discovery: LandingDiscovery,
        affected: MutableSet<FastVector>,
    ) {
        while (discovery.optimistic.isNotEmpty()) {
            if (discovery.simsUsed >= MAX_SIMS_PER_LANDING) {
                // Budget exhausted: unproven candidates leave the graph —
                // the same admission bar eager discovery's sim budget set.
                discovery.fanCursor = SINGLE_JUMP_FAN.size
                for (takeoff in discovery.optimistic) {
                    discovery.edges.remove(takeoff)
                    removeMirror(takeoff, landing)
                    affected += takeoff
                }
                discovery.optimistic.clear()
                affected += landing
                return
            }

            val takeoff = discovery.optimistic.first()
            discovery.optimistic.remove(takeoff)
            val edge = takeoff to landing
            val chain = chainMids[edge]
            val simsBefore = simulationsRun
            val validation = if (chain == null) {
                simulateJump(takeoff, landing)
            } else {
                val mid = chain.single()
                val slow = simulateChain(takeoff, mid, landing, ENTRY_SPEED_LOW)
                val fast = simulateChain(takeoff, mid, landing, ENTRY_SPEED_HIGH)
                if (CHAIN_DEBUG) {
                    com.lambda.Lambda.LOG.info(
                        "[ChainDiscovery] takeoff=(${takeoff.x},${takeoff.y},${takeoff.z}) mid=(${mid.x},${mid.z}) " +
                            "landing=(${landing.x},${landing.z}) slow=$slow fast=$fast lastFail=$lastChainFailure"
                    )
                }
                if (slow == null || fast == null) null else JumpValidation((slow + fast) / 2.0)
            }
            discovery.simsUsed += simulationsRun - simsBefore

            affected += takeoff
            affected += landing
            if (validation == null) {
                edgesRejected++
                discovery.edges.remove(takeoff)
                removeMirror(takeoff, landing)
                chainMids.remove(edge)
                entryEnvelopes.remove(edge)
                // Restore the live-edge budget with the next fan candidates,
                // exactly as eager validation would have kept probing. The
                // proposals join [LandingDiscovery.optimistic] and are
                // validated by this same loop.
                affected += proposeSingleJumps(landing, discovery)
            } else {
                edgesValidated++
                val dx = abs(landing.x - takeoff.x)
                val dz = abs(landing.z - takeoff.z)
                // Envelope (momentum) edges carry execution risk a whole-band
                // edge does not — a narrow certified entry box, phase-aligned
                // launches, no disturbance slack. Price that risk so they win
                // only when they genuinely shorten the route, never on ties
                // against robust edges (observed: an envelope edge outbidding
                // P0's whole-band edge by ε and then missing strict landings).
                // Keyed on geometry, not the envelope outcome, so the cost
                // matches the optimistic bound and only ever revises upward
                // (the LazySP invariant).
                val riskPenalty = if (chain == null && isEnvelopeSamplingClass(takeoff, landing)) {
                    ENVELOPE_RISK_PENALTY_TICKS
                } else 0.0
                val cost = validation.cost + AXIS_TIE_EPSILON * minOf(dx, dz) + riskPenalty
                discovery.edges[takeoff] = cost
                edgesFrom.getOrPut(takeoff) { HashMap() }[landing] = cost
                validation.envelope?.let { entryEnvelopes[edge] = it }
            }
        }
    }

    private fun removeMirror(takeoff: FastVector, landing: FastVector) {
        edgesFrom[takeoff]?.let { outgoing ->
            outgoing.remove(landing)
            if (outgoing.isEmpty()) edgesFrom.remove(takeoff)
        }
    }

    /** Immutable executor-facing provenance for the (validated) edges of [path]. */
    fun annotationsFor(path: List<FastVector>): Map<Pair<FastVector, FastVector>, TraversalHandle.EdgeAnnotation> {
        val result = HashMap<Pair<FastVector, FastVector>, TraversalHandle.EdgeAnnotation>()
        for (index in 0 until path.lastIndex) {
            val from = path[index]
            val to = path[index + 1]
            if (landings[to]?.optimistic?.contains(from) == true) continue
            val chain = chainWaypoints(from, to)
            val discovered = isDiscoveredJump(from, to)
            if (chain == null && !discovered) continue
            val edge = from to to
            result[edge] = TraversalHandle.EdgeAnnotation(
                chainWaypoints = chain?.toList(),
                discoveredJump = discovered,
                entrySpeedEnvelope = entryEnvelopes[edge],
            )
        }
        return result
    }

    /**
     * Drops all world-dependent discovery state. Used for chunk transitions,
     * where the conservative unknown/known boundary can change an arbitrary
     * part of a cached maneuver's swept region.
     */
    fun clearWorldCache() {
        landings.clear()
        edgesFrom.clear()
        chainMids.clear()
        entryEnvelopes.clear()
    }

    /**
     * A block changed: drop every discovered edge whose flight region could
     * read it and un-memoize the affected landings so they re-discover on
     * next expansion. Returns the nodes whose edge sets changed, for the
     * planner's synchronizeAffected pass.
     */
    fun invalidateAround(x: Int, y: Int, z: Int): Set<FastVector> {
        if (landings.isEmpty()) return emptySet()
        val affected = HashSet<FastVector>()
        val reopened = landings.keys.filter { landing ->
            abs(landing.x - x) <= INVALIDATION_RADIUS_XZ &&
                abs(landing.z - z) <= INVALIDATION_RADIUS_XZ &&
                abs(landing.y - y) <= INVALIDATION_RADIUS_Y
        }
        for (landing in reopened) {
            val discovery = landings.remove(landing) ?: continue
            if (discovery.edges.isEmpty()) continue
            affected += landing
            discovery.edges.keys.forEach { takeoff ->
                edgesFrom[takeoff]?.let { outgoing ->
                    outgoing.remove(landing)
                    if (outgoing.isEmpty()) edgesFrom.remove(takeoff)
                }
                chainMids.remove(takeoff to landing)
                entryEnvelopes.remove(takeoff to landing)
                affected += takeoff
            }
        }
        return affected
    }

    /**
     * Trigger predicate (plan: overhang/ledge gating): the node sits at a
     * platform edge — at least one cardinal neighbor column has no floor.
     */
    private fun isLedge(node: FastVector): Boolean {
        val y = node.y
        return !view.traits(node.x + 1, y - 1, node.z).standableFullTop ||
            !view.traits(node.x - 1, y - 1, node.z).standableFullTop ||
            !view.traits(node.x, y - 1, node.z + 1).standableFullTop ||
            !view.traits(node.x, y - 1, node.z - 1).standableFullTop
    }

    /**
     * Conservative pre-sim rejection for flat jumps: the player's AABB spans
     * ≥1.8 blocks of height from feet that never rise a full block above
     * takeoff level, so a solid block one above stance level on any interior
     * column the jump line crosses is untraversable at *every* entry — no
     * simulation needed. On dense terrain (the bedrock suite) this prunes
     * the large majority of candidates before the expensive sims run;
     * anything marginal (corner grazes, raised landings) still simulates.
     */
    private fun arcPossiblyClear(takeoff: FastVector, landing: FastVector): Boolean {
        val dx = (landing.x - takeoff.x).toDouble()
        val dz = (landing.z - takeoff.z).toDouble()
        val length = hypot(dx, dz)
        val steps = max(1, (length * 2.5).toInt())
        for (i in 1 until steps) {
            val t = i.toDouble() / steps
            val bx = Math.floor(takeoff.x + 0.5 + dx * t).toInt()
            val bz = Math.floor(takeoff.z + 0.5 + dz * t).toInt()
            if (bx == takeoff.x && bz == takeoff.z) continue
            if (bx == landing.x && bz == landing.z) continue
            if (!view.traits(bx, takeoff.y + 1, bz).passable) return false
        }
        return true
    }

    /** Some interior column under the takeoff→landing line has no floor at takeoff level. */
    private fun lineCrossesGap(takeoff: FastVector, landing: FastVector): Boolean =
        lineCrossesGapAtLevel(takeoff, landing, takeoff.y)

    /** The same interior-support probe at an explicit stance level [y]. */
    private fun lineCrossesGapAtLevel(takeoff: FastVector, landing: FastVector, y: Int): Boolean {
        val dx = (landing.x - takeoff.x).toDouble()
        val dz = (landing.z - takeoff.z).toDouble()
        val length = hypot(dx, dz)
        val steps = max(1, (length * 2).toInt())
        for (i in 1 until steps) {
            val t = i.toDouble() / steps
            val bx = Math.floor(takeoff.x + 0.5 + dx * t).toInt()
            val bz = Math.floor(takeoff.z + 0.5 + dz * t).toInt()
            if (bx == takeoff.x && bz == takeoff.z) continue
            if (bx == landing.x && bz == landing.z) continue
            if (!view.traits(bx, y - 1, bz).standableFullTop) return true
        }
        return false
    }

    /**
     * Validation as a poor-man's entry envelope (T3-lite): the executor
     * arrives at the takeoff anywhere in a realistic sprint band, so the
     * edge only exists if BOTH band endpoints land safely — the slow entry
     * must reach the landing node, and the fast entry may carry at most one
     * block deeper into the same platform. Anything narrower (a 1-wide
     * landing that only works at one exact speed) is momentum-chain
     * territory and stays out of the graph until WP3.3 scripts it.
     *
     * Beyond the speed band, the executed launch *position* spreads too:
     * command→input latency plus segment-advance timing put the first real
     * takeoff tick up to ~half a block past the node center (field
     * telemetry: every bedrock landing miss launched at progress 0.45–0.7
     * and clipped raised terrain the center-launched sim had cleared by
     * centimeters). So the deep launch is probed at BOTH band endpoints —
     * the slow deep arc descends earliest and catches raised mid columns
     * the fast flat arc skims over. An edge whose corridor only works from
     * the exact center is not executable — reject it here, never paper over
     * it with executor choreography. Lateral probes were tried and dropped:
     * measured launches are center-aligned (≤0.06 error; the alignment
     * gates work) and the extra sims tripled discovery cost.
     */
    private fun simulateJump(takeoff: FastVector, landing: FastVector): JumpValidation? {
        // T3 envelope sampling is reserved for the classes that need it:
        // the extension fan (distance/rise outside the pre-P2 bands) and
        // ascending jumps (speed-critical by nature). The pre-P2 flat and
        // descend-1 fan keeps its exact 1–3-sim whole-band admission —
        // sampling here retunes every landing's sim budget and the bedrock
        // course loses its proven edges to admission noise (measured).
        val samplingEligible = isEnvelopeSamplingClass(takeoff, landing)
        val slow = simulateEntry(takeoff, landing, ENTRY_SPEED_LOW, allowCarryPast = false)
        if (slow == null && !samplingEligible) return null
        val fast = simulateEntry(takeoff, landing, ENTRY_SPEED_HIGH, allowCarryPast = true)
        if (slow != null && fast != null) {
            // Deep launch is probed at fast entry only: a slow approach
            // covers little ground between takeoff-gate evaluations and
            // always launches near the window front, so slow+deep is not a
            // pose the executor produces. Fast+deep is (measured 0.45–0.7
            // past center). A whole-band edge that cannot take the deep
            // pose is rejected outright, exactly as before T3: its field
            // launch spread is wider than its corridor.
            simulateEntry(takeoff, landing, ENTRY_SPEED_HIGH, allowCarryPast = true, progressOffset = DEEP_LAUNCH_PROGRESS)
                ?: return null
            if (!robustEntryBox(
                    takeoff, landing,
                    minSpeed = ENTRY_SPEED_LOW,
                    maxSpeed = ENTRY_SPEED_HIGH,
                    minProgress = 0.0,
                    maxProgress = DEEP_LAUNCH_PROGRESS,
                )
            ) return null
            return JumpValidation((slow + fast) / 2.0)
        }
        if (!samplingEligible) return null
        return sampleEnvelope(takeoff, landing, slow, fast)
    }

    private fun isEnvelopeSamplingClass(takeoff: FastVector, landing: FastVector): Boolean {
        val rise = takeoff.y - landing.y
        if (rise < 0 || rise > 1) return true
        val distance = hypot((landing.x - takeoff.x).toDouble(), (landing.z - takeoff.z).toDouble())
        return distance < 2.2 || distance > 4.3
    }

    /**
     * T3 entry-envelope admission: a field-band endpoint failed, so the
     * generic whole-band rule rejects — but a momentum-bound jump (long,
     * ascending, or deep-descending) may still be valid on a narrower entry
     * interval. Find the largest contiguous successful run of
     * [ENTRY_SPEED_SAMPLES] anchored at whichever endpoint passed
     * (interior-only intervals are probed from the band middle), then probe
     * progressively deeper launch poses at the run's hot end to certify how
     * far past the node center the launch may fire. A single-sample run is
     * legitimate — maximum-distance jumps can have exactly one working
     * speed. The executor refuses to launch outside the published interval
     * and the planner replans instead, so a narrow envelope never turns
     * into edge choreography.
     */
    private fun sampleEnvelope(
        takeoff: FastVector,
        landing: FastVector,
        slow: Double?,
        fast: Double?,
    ): JumpValidation? {
        if (ENVELOPE_DEBUG) {
            com.lambda.Lambda.LOG.info(
                "[EnvelopeDiscovery] sampling takeoff=(${takeoff.x},${takeoff.y},${takeoff.z}) " +
                    "landing=(${landing.x},${landing.y},${landing.z}) slow=$slow fast=$fast"
            )
        }
        // Stage 1: node-center anchor. An executable run must include at
        // least one speed real ground movement can deliver — a run of only
        // the 0.30 robustness endpoint describes an entry state sprint
        // equilibrium (0.2806) never produces, and admitting it publishes
        // an edge the executor can only refuse.
        val achievable = achievableEntry(takeoff, landing)
        sampleSpeedRun(takeoff, landing, progressOffset = 0.0, seeded = true, seedSlow = slow, seedFast = fast)?.let { run ->
            if (ENTRY_SPEED_SAMPLES[run.lo] <= achievable) {
                val windowEnd = probeWindowEnd(takeoff, landing, run.hi, anchor = 0.0, probes = 3)
                if (windowEnd >= MIN_TAKEOFF_WINDOW) {
                    if (!robustEntryBox(
                            takeoff, landing,
                            ENTRY_SPEED_SAMPLES[run.lo], ENTRY_SPEED_SAMPLES[run.hi],
                            0.0, windowEnd,
                        )
                    ) return null
                    return admitEnvelope(takeoff, landing, run, minProgress = 0.0, maxProgress = windowEnd)
                }
                return null
            }
        }
        // Stage 2/3: deep and lip anchors. Maximum-distance jumps are
        // launched at (or near) the lip — the shorter remaining flight is
        // what brings the required entry speed down into the achievable
        // range, exactly how the class is jumped by hand ("you need to
        // jump right on the edge, not much tolerance"). The certified
        // window then STARTS deep and the executor's phase alignment plus
        // launch sim own the precision.
        // Prefer an envelope whose minimum speed plain ground sprint can
        // deliver (stored ~0.153); an ice-tier envelope is only a
        // fallback — publishing it when a ground-executable lip window
        // exists strands every ordinary approach below vmin.
        var fallback: JumpValidation? = null
        for (anchor in ENVELOPE_ANCHORS) {
            val run = sampleSpeedRun(takeoff, landing, progressOffset = anchor) ?: continue
            if (ENTRY_SPEED_SAMPLES[run.lo] > achievable) continue
            val windowEnd = probeWindowEnd(takeoff, landing, run.hi, anchor = anchor, probes = 2)
            if (windowEnd - anchor < MIN_TAKEOFF_WINDOW) continue
            if (!robustEntryBox(
                    takeoff, landing,
                    ENTRY_SPEED_SAMPLES[run.lo], ENTRY_SPEED_SAMPLES[run.hi],
                    anchor, windowEnd,
                )
            ) continue
            val validation = admitEnvelope(takeoff, landing, run, minProgress = anchor, maxProgress = windowEnd)
            if (ENTRY_SPEED_SAMPLES[run.lo] <= ENTRY_SPEED_GROUND_MAX) return validation
            if (fallback == null) fallback = validation
        }
        return fallback
    }

    /**
     * Deepest certified launch pose past [anchor]: probe in
     * [WINDOW_PROBE_STEP] increments at the run's hot speed; the first
     * failure ends the window. The certified window is what the executor's
     * phase alignment aims a launch tick into.
     */
    private fun probeWindowEnd(
        takeoff: FastVector,
        landing: FastVector,
        hiIndex: Int,
        anchor: Double,
        probes: Int,
    ): Double {
        var end = anchor
        for (k in 1..probes) {
            // Clamp to the deepest pose a real player can stand on — a
            // synthetic sim start hanging past the physical support would
            // "validate" a pose that never occurs.
            val progress = (anchor + k * WINDOW_PROBE_STEP).coerceAtMost(MAX_LIP_PROGRESS)
            if (progress <= end) break
            simulateEntry(
                takeoff, landing, ENTRY_SPEED_SAMPLES[hiIndex], allowCarryPast = true, progressOffset = progress,
            ) ?: break
            end = progress
        }
        return end
    }

    private class SpeedRun(val lo: Int, val hi: Int, val loCost: Double, val hiCost: Double)

    /**
     * Largest contiguous successful run of [ENTRY_SPEED_SAMPLES] at one
     * launch pose, anchored at whichever field-band endpoint passes
     * (interior-only runs are probed from the band middle). A single-sample
     * run is legitimate — maximum-distance jumps can have exactly one
     * working speed.
     */
    private fun sampleSpeedRun(
        takeoff: FastVector,
        landing: FastVector,
        progressOffset: Double,
        seeded: Boolean = false,
        seedSlow: Double? = null,
        seedFast: Double? = null,
    ): SpeedRun? {
        val n = ENTRY_SPEED_SAMPLES.size
        val costs = arrayOfNulls<Double>(n)
        fun probe(i: Int): Double? = simulateEntry(
            takeoff, landing, ENTRY_SPEED_SAMPLES[i], allowCarryPast = true, progressOffset = progressOffset,
        ).also { costs[i] = it }

        val slow = if (seeded) seedSlow.also { costs[0] = it } else probe(0)
        val fast = if (seeded) seedFast.also { costs[n - 1] = it } else probe(n - 1)
        var lo: Int
        var hi: Int
        when {
            fast != null -> {
                hi = n - 1
                lo = n - 1
                while (lo > 0 && probe(lo - 1) != null) lo--
            }
            slow != null -> {
                lo = 0
                hi = 0
                while (hi < n - 1 && probe(hi + 1) != null) hi++
            }
            else -> {
                // Both endpoints failed: reject. Interior-only intervals
                // were sampled once (band-middle probe + expansion) and
                // never admitted a useful edge, while the extra sims per
                // hopeless candidate starved landing budgets on dense
                // terrain — cheap rejection is worth more than the class.
                return null
            }
        }
        return SpeedRun(lo, hi, costs[lo]!!, costs[hi]!!)
    }

    private fun admitEnvelope(
        takeoff: FastVector,
        landing: FastVector,
        run: SpeedRun,
        minProgress: Double,
        maxProgress: Double,
    ): JumpValidation {
        val envelope = EntrySpeedEnvelope(
            min = ENTRY_SPEED_SAMPLES[run.lo],
            max = ENTRY_SPEED_SAMPLES[run.hi],
            minTakeoffProgress = minProgress,
            maxTakeoffProgress = maxProgress,
        )
        if (ENVELOPE_DEBUG) {
            com.lambda.Lambda.LOG.info(
                "[EnvelopeDiscovery] admitted takeoff=(${takeoff.x},${takeoff.y},${takeoff.z}) " +
                    "landing=(${landing.x},${landing.y},${landing.z}) envelope=$envelope"
            )
        }
        return JumpValidation(cost = (run.loCost + run.hiCost) / 2.0, envelope = envelope)
    }

    /**
     * Admission result of one candidate edge: simulated tick cost plus the
     * validated entry interval — null envelope for whole-band edges (the
     * executor plays those with no entry constraints, as before T3).
     */
    private data class JumpValidation(val cost: Double, val envelope: EntrySpeedEnvelope? = null)

    /**
     * Certify the corners of the live launch-state box, not just its ideal
     * center line. Production approaches carry small lateral position and
     * velocity errors that the old harness never generated; an edge that
     * only works at exactly zero error is not reliable enough to publish.
     */
    private fun robustEntryBox(
        takeoff: FastVector,
        landing: FastVector,
        minSpeed: Double,
        maxSpeed: Double,
        minProgress: Double,
        maxProgress: Double,
    ): Boolean {
        val states = arrayOf(
            minSpeed to minProgress,
            maxSpeed to minProgress,
            minSpeed to maxProgress,
            maxSpeed to maxProgress,
        )
        for ((speed, progress) in states) {
            for (side in intArrayOf(-1, 1)) {
                for (drift in intArrayOf(-1, 1)) {
                    if (simulateEntry(
                            takeoff = takeoff,
                            landing = landing,
                            entrySpeed = speed,
                            allowCarryPast = true,
                            progressOffset = progress,
                            lateralOffset = side * ROBUST_LATERAL_OFFSET,
                            lateralVelocity = drift * ROBUST_LATERAL_VELOCITY,
                        ) == null
                    ) return false
                }
            }
        }
        return true
    }

    /**
     * One tick-accurate simulation (T7 first-collision semantics): entry at
     * [entrySpeed] along the jump line, jump on the first tick, hold
     * forward. Runs against the session's snapshot view — worker-legal, and
     * consistent with the planning world by construction; live divergence
     * shows up at the executor's launch gate and repairs like any other.
     */
    private fun simulateEntry(
        takeoff: FastVector,
        landing: FastVector,
        entrySpeed: Double,
        allowCarryPast: Boolean,
        progressOffset: Double = 0.0,
        lateralOffset: Double = 0.0,
        lateralVelocity: Double = 0.0,
    ): Double? {
        val center = Vec3d.ofBottomCenter(takeoff.toBlockPos())
        val to = Vec3d.ofBottomCenter(landing.toBlockPos())
        val line = to.subtract(center).multiply(1.0, 0.0, 1.0).normalize()
        val perpendicular = Vec3d(-line.z, 0.0, line.x)
        // Offset entries steer like the executor does: yaw at the landing
        // from where the player actually stands, not along the center line.
        val from = center.add(line.multiply(progressOffset)).add(perpendicular.multiply(lateralOffset))
        val rotation = from.rotationTo(to)
        val direction = to.subtract(from).multiply(1.0, 0.0, 1.0).normalize()
        val landingBlock = landing.toBlockPos()

        simulationsRun++
        val simulator = MovementSimulator(
            profile = profile,
            environment = environment,
            initialState = MovementSimulationState.synthetic(
                profile = profile,
                position = from,
                rotation = rotation,
                velocity = direction.multiply(entrySpeed).add(perpendicular.multiply(lateralVelocity)),
                onGround = true,
                isSprinting = true,
            ),
            skipEntityCollisions = true,
        )

        for (tick in 0 until MAX_SIMULATION_TICKS) {
            // The executed flight brakes near the landing (ManeuverPolicy)
            // — validate with the same inputs, never a different rule.
            val before = simulator.lastTick
            val brake = !before.onGround && tick > 0 && ManeuverPolicy.shouldBrake(
                hypot(to.x - before.position.x, to.z - before.position.z),
                hypot(before.velocity.x, before.velocity.z),
                ManeuverPolicy.SINGLE_BRAKE_LEAD_TICKS,
            )
            val current = simulator.tickMovement(
                MovementSimulationInput(
                    forward = if (brake) 0.0 else 1.0,
                    strafe = 0.0,
                    jump = tick == 0,
                    sneak = false,
                    sprint = true,
                    useItemSlowdown = false,
                    rotation = rotation,
                )
            )

            if (current.onGround && tick > 1) {
                val feet = current.position.flooredBlockPos
                // Carry landings: one block of overshoot in any direction is
                // acceptable (fast/deep entries), but only onto the *same
                // platform at the same level* — a raised or dropped touchdown
                // is never the validated maneuver. Direction-agnostic on
                // purpose: angled jumps overshoot diagonally along the pad.
                val carryLanding = allowCarryPast &&
                    feet.y == landingBlock.y &&
                    abs(feet.x - landingBlock.x) <= 1 && abs(feet.z - landingBlock.z) <= 1 &&
                    MoveTable.isStance(view, feet.x, landing.y, feet.z)
                return when {
                    feet == landingBlock -> (tick + 1).toDouble()
                    carryLanding -> (tick + 1).toDouble()
                    else -> null
                }
            }
            if (current.simulator.state.horizontalCollision) return null
            if (current.position.y < minOf(from.y, to.y) - 0.2) return null
        }
        return null
    }

    /**
     * Policy-driven chain validation ([ManeuverPolicy] — the same rule the
     * executor plays back): sprint entry at [entrySpeed], jump on every
     * grounded tick, brake mid-air near each landing. Success = grounded at
     * the final landing having touched each waypoint in order; grounded
     * anywhere else fails (a chain that scuffs an unplanned block is not
     * the validated maneuver). Rim landings count ([ManeuverPolicy.WAYPOINT_TOLERANCE]).
     */
    private fun simulateChain(
        takeoff: FastVector,
        mid: FastVector,
        landing: FastVector,
        entrySpeed: Double,
    ): Double? {
        val from = Vec3d.ofBottomCenter(takeoff.toBlockPos())
        val to = Vec3d.ofBottomCenter(landing.toBlockPos())
        val rotation = from.rotationTo(to)
        val direction = to.subtract(from).multiply(1.0, 0.0, 1.0).normalize()
        val targets = listOf(Vec3d.ofBottomCenter(mid.toBlockPos()), to)

        simulationsRun++
        val simulator = MovementSimulator(
            profile = profile,
            environment = environment,
            initialState = MovementSimulationState.synthetic(
                profile = profile,
                position = from,
                rotation = rotation,
                velocity = direction.multiply(entrySpeed),
                onGround = true,
                isSprinting = true,
            ),
            skipEntityCollisions = true,
        )

        var targetIndex = 0
        for (tick in 0 until ManeuverPolicy.MAX_CHAIN_TICKS) {
            val before = simulator.lastTick
            val target = targets[targetIndex]
            val distanceToTarget = hypot(target.x - before.position.x, target.z - before.position.z)
            val speed = hypot(before.velocity.x, before.velocity.z)
            val brake = !before.onGround && ManeuverPolicy.shouldBrake(distanceToTarget, speed)

            val current = simulator.tickMovement(
                MovementSimulationInput(
                    forward = if (brake) 0.0 else 1.0,
                    strafe = 0.0,
                    jump = before.onGround,
                    sneak = false,
                    sprint = true,
                    useItemSlowdown = false,
                    rotation = rotation,
                )
            )

            if (current.position.y < from.y - 0.2) {
                lastChainFailure = "fell t=$tick pos=(%.2f,%.2f) target=$targetIndex".format(current.position.x, current.position.z)
                return null
            }
            if (current.simulator.state.horizontalCollision) {
                lastChainFailure = "collision t=$tick"
                return null
            }
            if (!current.onGround || tick <= 1) continue

            val feet = current.position
            val onCurrent = abs(targets[targetIndex].y - feet.y) <= WAYPOINT_VERTICAL_TOLERANCE &&
                hypot(targets[targetIndex].x - feet.x, targets[targetIndex].z - feet.z) <=
                ManeuverPolicy.WAYPOINT_TOLERANCE
            val onPrevious = targetIndex > 0 &&
                abs(targets[targetIndex - 1].y - feet.y) <= WAYPOINT_VERTICAL_TOLERANCE &&
                hypot(targets[targetIndex - 1].x - feet.x, targets[targetIndex - 1].z - feet.z) <=
                ManeuverPolicy.WAYPOINT_TOLERANCE
            val onTakeoff = abs(from.y - feet.y) <= WAYPOINT_VERTICAL_TOLERANCE &&
                hypot(from.x - feet.x, from.z - feet.z) <= ManeuverPolicy.WAYPOINT_TOLERANCE
            when {
                onCurrent && targetIndex == targets.lastIndex -> return (tick + 1).toDouble()
                onCurrent -> targetIndex++
                onPrevious || onTakeoff -> Unit // between hops / pre-launch
                else -> {
                    lastChainFailure = "offWaypoint t=$tick pos=(%.2f,%.2f) target=$targetIndex".format(feet.x, feet.z)
                    return null
                }
            }
        }
        lastChainFailure = "timeout"
        return null
    }

    private data class Offset(val dx: Int, val dz: Int) {
        val distance: Double = hypot(dx.toDouble(), dz.toDouble())
    }

    private fun progressesFromOrigin(takeoff: FastVector, landing: FastVector): Boolean {
        val origin = preferredOrigin ?: return true
        val takeoffDx = (takeoff.x - origin.x).toDouble()
        val takeoffDz = (takeoff.z - origin.z).toDouble()
        val landingDx = (landing.x - origin.x).toDouble()
        val landingDz = (landing.z - origin.z).toDouble()
        // Slack, not strict progress: a route detouring around a pillar
        // locally moves against the origin-distance gradient, and a strict
        // gate silently drops exactly the sideways jumps that would replace
        // two template hops with one — the executor then plays the slower
        // plan and the path looks needlessly long.
        return hypot(takeoffDx, takeoffDz) < hypot(landingDx, landingDz) + PROGRESS_SLACK_BLOCKS
    }

    /**
     * Cheap geometric half of the momentum contract: a candidate that can
     * only validate near sprint carry needs at least two standable blocks
     * behind its takeoff along the jump line, or no approach can ever
     * deliver the entry state and every simulation is wasted budget.
     */
    private fun hasEntryRunway(takeoff: FastVector, landing: FastVector): Boolean {
        val dx = (landing.x - takeoff.x).toDouble()
        val dz = (landing.z - takeoff.z).toDouble()
        val length = hypot(dx, dz)
        if (length <= 1.0E-6) return false
        for (back in 1..2) {
            val bx = takeoff.x - Math.round(dx / length * back).toInt()
            val bz = takeoff.z - Math.round(dz / length * back).toInt()
            if (!MoveTable.isStance(view, bx, takeoff.y, bz)) return false
        }
        return true
    }

    /**
     * The stored entry speed the approach surface can actually deliver at
     * this takeoff. Plain ground sprint stores ~0.153 regardless of runway
     * length; only a low-friction (ice-family) runway preserves stored
     * velocity near the field band's hot end. An envelope demanding more
     * than the surface can produce is a poison edge: the planner selects
     * it, the executor refuses it, and the penalization cascade starves
     * the graph (measured on bedrock: 27 previously-green edges).
     */
    private fun achievableEntry(takeoff: FastVector, landing: FastVector): Double {
        val dx = (landing.x - takeoff.x).toDouble()
        val dz = (landing.z - takeoff.z).toDouble()
        val length = hypot(dx, dz)
        if (length <= 1.0E-6) return ENTRY_SPEED_GROUND_MAX
        for (back in 0..2) {
            val bx = takeoff.x - Math.round(dx / length * back).toInt()
            val bz = takeoff.z - Math.round(dz / length * back).toInt()
            if (view.traits(bx, takeoff.y - 1, bz).slipperiness < ICE_SLIPPERINESS_MIN) {
                return ENTRY_SPEED_GROUND_MAX
            }
        }
        return ENTRY_SPEED_ACHIEVABLE_MAX
    }

    private companion object {
        /** Shared empty state for non-ledge landings (memoization marker). */
        val EMPTY_LANDING = LandingDiscovery()

        /** Chain-candidate outcome logging; keep off outside investigations. */
        const val CHAIN_DEBUG = false

        /** Envelope-sampling outcome logging; keep off outside investigations. */
        const val ENVELOPE_DEBUG = false

        const val MAX_SIMULATION_TICKS = 20
        const val WAYPOINT_VERTICAL_TOLERANCE = 0.20
        const val INVALIDATION_RADIUS_Y = 3
        const val AXIS_TIE_EPSILON = 0.05

        // Entry-speed envelope endpoints. Field telemetry on jagged bedrock
        // shows turns and fresh landings frequently deliver only ~0.12–0.15
        // blocks/tick at takeoff; validating from 0.22 admitted jumps the
        // executor could not reproduce. A maneuver now has to work from the
        // actual low-entry regime as well as full sprint carry.
        const val ENTRY_SPEED_LOW = 0.12
        const val ENTRY_SPEED_HIGH = 0.30

        // How far past the takeoff center the deep-launch probes start
        // (see simulateJump). Matches the measured launch spread.
        const val DEEP_LAUNCH_PROGRESS = 0.45

        // Takeoff levels relative to the landing, landing-anchored: same
        // level, one below (ascending sprint jump onto a +1 landing), and
        // one to three above (descending jumps across chasms). Descend-2/-3
        // originally cost a full fan slot for zero bedrock uses under
        // whole-band admission; T3 envelope admission is what makes them
        // land often enough to keep (P2).
        val TAKEOFF_RISES = intArrayOf(0, 1, -1, 2, 3)

        // Ascending (+1 landing) sprint-jump reach cap; flat/descending
        // keep the candidate band's own limit.
        const val ASCEND_MAX_DISTANCE = 3.2

        // Origin-progress slack for candidate pruning (see progressesFromOrigin).
        const val PROGRESS_SLACK_BLOCKS = 1.0

        // Per-landing budget of LIVE (validated + pending) edges. Sized so
        // a landing keeps ~6 plausible candidates; validation failures
        // back-fill from the fan cursor, reproducing eager admission.
        const val MAX_EDGES_PER_LANDING = 6

        // Per-landing simulation budget for resolution — the same admission
        // bar the eager validator's budget set; candidates beyond it never
        // enter the graph. Raised from 24 with T3: envelope sampling costs
        // 2–10 sims per candidate where whole-band validation cost 1–3,
        // and without headroom the tier-1 fan starves before the edges the
        // bedrock course depends on validate.
        const val MAX_SIMS_PER_LANDING = 96

        // Robust launch certificate corners. Executor gates are slightly
        // wider, leaving quantization margin without publishing center-line-
        // only jumps that fail under ordinary production approach drift.
        const val ROBUST_LATERAL_OFFSET = 0.12
        const val ROBUST_LATERAL_VELOCITY = 0.04

        // Sprint-jump reach beyond the template gapJump (2 blocks): 2.0–5.0
        // blocks of horizontal displacement, any integer direction — this is
        // where the F3 connections live. 2.0–2.2 admits the two-ahead class
        // when a rise is involved (no template covers a real 2-gap with a
        // vertical delta; flat (2,0) stays template territory — see
        // FLAT_MIN_DISTANCE). 4.3–5.0 is the momentum band (4-wide gaps),
        // valid only on a narrow entry interval — T3 envelope territory.
        // Longest first: with a per-landing budget, the jumps that shorten
        // the path the most get proposed before the budget runs out.
        val CANDIDATE_OFFSETS: List<Offset> = buildList {
            for (dx in -5..5) for (dz in -5..5) {
                val distance = hypot(dx.toDouble(), dz.toDouble())
                if (distance in 2.0..5.0) add(Offset(dx, dz))
            }
        }.sortedByDescending { it.distance }

        // Chains keep the pre-P2 proposal band: a two-hop macro over 5-block
        // hops would span 10 blocks of corridor, and the momentum band is
        // exactly what single-jump envelopes now represent more cheaply.
        val CHAIN_OFFSETS: List<Offset> = CANDIDATE_OFFSETS.filter { it.distance in 2.2..4.3 }

        // Flat same-level jumps below this stay template territory; a rise
        // in either direction has no template and enters the fan from 2.0.
        const val FLAT_MIN_DISTANCE = 2.2

        // Candidates at or beyond this displacement can only validate on
        // the hot end of the entry band, so they are proposed only when the
        // world has a physical runway behind the takeoff. Without this
        // prefilter the momentum fan monopolizes every landing's sim budget
        // on runway-less proposals that reject after seven sims each.
        const val MOMENTUM_RUNWAY_MIN_DISTANCE = 4.5

        // Entry speeds sampled by T3 envelope admission (endpoints match
        // ENTRY_SPEED_LOW/HIGH; interior step 0.03 b/t).
        val ENTRY_SPEED_SAMPLES = doubleArrayOf(0.12, 0.15, 0.18, 0.21, 0.24, 0.27, 0.30)

        // Entry speeds are STORED velocity (player.velocity is sampled
        // post-friction): sustained ground sprint stores only ~0.153 even
        // though it displaces 0.281/tick. Higher stored entries exist —
        // ice runways (~0.255) and landing carry — but nothing on foot
        // stores more than this. An envelope whose minimum exceeds it is
        // unexecutable by definition and must not enter the graph.
        const val ENTRY_SPEED_ACHIEVABLE_MAX = 0.26

        // Stored-velocity ceiling of a plain ground sprint approach; an
        // envelope executable at or below this works from any runway.
        const val ENTRY_SPEED_GROUND_MAX = 0.16

        // Slipperiness at or above this preserves stored velocity like ice
        // (ice 0.98, packed/blue ice 0.989); anything lower is ground-tier.
        const val ICE_SLIPPERINESS_MIN = 0.97

        // Launch-pose anchors for momentum classes: deep (0.30), at the
        // lip (0.50), and on the last standable sliver (0.65) — where
        // maximum-distance jumps are actually launched. The shorter
        // remaining flight brings the required entry speed into the
        // achievable range; feet stay supported up to ~0.8 past center
        // (0.5 block edge + 0.3 hitbox radius).
        val ENVELOPE_ANCHORS = doubleArrayOf(0.30, 0.50, 0.65)

        // Deepest pose a real player can still stand on (with margin).
        const val MAX_LIP_PROGRESS = 0.75

        // Window-extent probe step past an anchor, and the minimum
        // certified window width. The executor's launch-phase alignment
        // places a launch tick inside ~0.10 of position (one 0.6-throttle
        // trim shifts the lattice by (1−0.6)·stride ≈ 0.11), so a 0.10
        // window is executable; anything narrower is a phase lottery and
        // never becomes a graph edge.
        const val WINDOW_PROBE_STEP = 0.13
        const val MIN_TAKEOFF_WINDOW = 0.10

        // Flat cost surcharge on envelope-admitted edges (see resolveLanding).
        const val ENVELOPE_RISK_PENALTY_TICKS = 2.0

        /**
         * The single-jump proposal fan, tiered: the pre-P2 proven fan
         * proposes first — putting the extended momentum classes ahead of
         * it (global longest-first) let their expensive envelope sampling
         * exhaust every landing's sim budget before a single P0 winner
         * validated, and the bedrock course lost its jump edges wholesale.
         * Extension classes spend only leftover budget.
         */
        val SINGLE_JUMP_FAN: List<Pair<Offset, Int>> = buildList {
            val p0Band = 2.2..4.3
            for (offset in CANDIDATE_OFFSETS) {
                if (offset.distance !in p0Band) continue
                for (rise in intArrayOf(0, 1, -1)) add(offset to rise)
            }
            for (offset in CANDIDATE_OFFSETS) {
                val inP0Band = offset.distance in p0Band
                for (rise in TAKEOFF_RISES) {
                    if (inP0Band && rise in -1..1) continue
                    add(offset to rise)
                }
            }
        }

        // A two-hop chain starts at landing - 2*offset. Include one extra
        // block for the player footprint/collision neighborhood. Derive this
        // from the candidate library so extending jump reach cannot silently
        // leave stale discovery edges after a world change.
        val INVALIDATION_RADIUS_XZ: Int =
            CANDIDATE_OFFSETS.maxOf { maxOf(abs(it.dx), abs(it.dz)) } * 2 + 1
    }
}
