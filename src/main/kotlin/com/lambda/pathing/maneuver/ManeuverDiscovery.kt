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
import com.lambda.util.math.flooredBlockPos
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.MovementSimulator
import com.lambda.util.world.FastVector
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
import com.lambda.worldview.WorldView
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * WP3.2 landing-anchored maneuver discovery (research plan §4.4, notes
 * T2/T7): when the backward search first asks for the predecessors of a
 * ledge node, propose sprint-jump takeoffs beyond the template range and
 * validate each candidate with one tick-accurate forward simulation. The
 * survivors become ordinary graph edges (takeoff → landing) with
 * tick-denominated costs — F3 connections the 45°-quantized template set
 * cannot represent (angled gaps, 2-3 block gaps).
 *
 * Landing-anchored on purpose: D* Lite expands backward from the goal, so
 * predecessor-directed discovery is the search's native direction, and
 * each landing is examined at most once per world state (memoized; a
 * nearby block change re-opens it through [invalidateAround]).
 *
 * v1 scope, honestly stated: flat jumps only (dy = 0) between grid nodes
 * (conjecture C1's voxel-A→voxel-B claim), entry assumed sprinting along
 * the jump line — which the executor's long-gap policy reproduces — and
 * first-collision = failure (T7's discovery semantics; no bonk-and-continue
 * recovery jumps). The analytic yaw sweep and entry envelopes are the
 * planned upgrades, not prerequisites.
 */
class ManeuverDiscovery(
    private val player: ClientPlayerEntity,
    private val view: WorldView,
) {
    /** Landings already examined for this world state. */
    private val examinedLandings = HashSet<FastVector>()

    /** landing → (takeoff → cost); the authoritative store. */
    private val edgesInto = HashMap<FastVector, Map<FastVector, Double>>()

    /** takeoff → (landing → cost); mirror for successor queries. */
    private val edgesFrom = HashMap<FastVector, HashMap<FastVector, Double>>()

    var landingsExamined = 0; private set
    var simulationsRun = 0; private set
    var edgesDiscovered = 0; private set

    /**
     * Discovered incoming edges of [landing], running discovery on first
     * call. Invoked from the graph's predecessor provider — on the client
     * thread, inside the planner's compute budget; trigger gating and
     * memoization keep it off the hot path.
     */
    fun predecessorsInto(landing: FastVector): Map<FastVector, Double> {
        if (!examinedLandings.add(landing)) return edgesInto[landing] ?: emptyMap()
        if (!isLedge(landing)) return emptyMap()

        landingsExamined++
        var found: HashMap<FastVector, Double>? = null
        for (offset in CANDIDATE_OFFSETS) {
            val takeoff = fastVectorOf(landing.x - offset.dx, landing.y, landing.z - offset.dz)
            if (!MoveTable.isStance(view, takeoff.x, takeoff.y, takeoff.z)) continue
            // Only simulate real gaps: if every column under the jump line is
            // standable, walking is strictly cheaper and the templates
            // already connect it.
            if (!lineCrossesGap(takeoff, landing)) continue

            val cost = simulateJump(takeoff, landing) ?: continue
            // Tiny axis-alignment epsilon: an angled jump and a straight one
            // often land on the same integer tick, and an arbitrary tie-break
            // zig-zags the path. Cardinal jumps also carry symmetric lateral
            // tolerance, so prefer them whenever the tick cost truly ties.
            val tieBreak = AXIS_TIE_EPSILON * minOf(abs(offset.dx), abs(offset.dz))
            (found ?: HashMap<FastVector, Double>().also { found = it })[takeoff] = cost + tieBreak
        }

        val edges = found ?: return emptyMap()
        edgesInto[landing] = edges
        edges.forEach { (takeoff, cost) ->
            edgesFrom.getOrPut(takeoff) { HashMap() }[landing] = cost
        }
        edgesDiscovered += edges.size
        return edges
    }

    /** Discovered outgoing edges of [takeoff] (never triggers discovery). */
    fun successorsFrom(takeoff: FastVector): Map<FastVector, Double> =
        edgesFrom[takeoff] ?: emptyMap()

    /**
     * A block changed: drop every discovered edge whose flight region could
     * read it and un-memoize the affected landings so they re-discover on
     * next expansion. Returns the nodes whose edge sets changed, for the
     * planner's synchronizeAffected pass.
     */
    fun invalidateAround(x: Int, y: Int, z: Int): Set<FastVector> {
        if (edgesInto.isEmpty() && examinedLandings.isEmpty()) return emptySet()
        val affected = HashSet<FastVector>()
        val reopened = examinedLandings.filter { landing ->
            abs(landing.x - x) <= INVALIDATION_RADIUS_XZ &&
                abs(landing.z - z) <= INVALIDATION_RADIUS_XZ &&
                abs(landing.y - y) <= INVALIDATION_RADIUS_Y
        }
        for (landing in reopened) {
            examinedLandings.remove(landing)
            edgesInto.remove(landing)?.let { edges ->
                affected += landing
                edges.keys.forEach { takeoff ->
                    edgesFrom[takeoff]?.remove(landing)
                    affected += takeoff
                }
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

    /** Some interior column under the takeoff→landing line has no floor. */
    private fun lineCrossesGap(takeoff: FastVector, landing: FastVector): Boolean {
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
            if (!view.traits(bx, takeoff.y - 1, bz).standableFullTop) return true
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
     */
    private fun simulateJump(takeoff: FastVector, landing: FastVector): Double? {
        val slow = simulateEntry(takeoff, landing, ENTRY_SPEED_LOW, allowCarryPast = false) ?: return null
        val fast = simulateEntry(takeoff, landing, ENTRY_SPEED_HIGH, allowCarryPast = true) ?: return null
        return (slow + fast) / 2.0
    }

    /**
     * One tick-accurate simulation (T7 first-collision semantics): entry at
     * [entrySpeed] along the jump line, jump on the first tick, hold
     * forward. Runs against the live client world — for a static planning
     * window this matches the session view; a divergence shows up as an
     * executor deviation and repairs like any other.
     */
    private fun simulateEntry(
        takeoff: FastVector,
        landing: FastVector,
        entrySpeed: Double,
        allowCarryPast: Boolean,
    ): Double? {
        val from = Vec3d.ofBottomCenter(takeoff.toBlockPos())
        val to = Vec3d.ofBottomCenter(landing.toBlockPos())
        val rotation = from.rotationTo(to)
        val direction = to.subtract(from).multiply(1.0, 0.0, 1.0).normalize()
        val landingBlock = landing.toBlockPos()
        // One block deeper along the jump line — acceptable for the fast
        // entry only when it is the same platform at the same level.
        val carryBlock = to.add(direction).flooredBlockPos
        val carryAcceptable = allowCarryPast &&
            MoveTable.isStance(view, carryBlock.x, landing.y, carryBlock.z)

        simulationsRun++
        val simulator = MovementSimulator(
            player = player,
            initialState = MovementSimulationState.at(
                player = player,
                position = from,
                rotation = rotation,
                velocity = direction.multiply(entrySpeed),
                onGround = true,
                isSprinting = true,
            ),
        ).also { it.skipEntityCollisions = true }

        for (tick in 0 until MAX_SIMULATION_TICKS) {
            val current = simulator.tickMovement(
                MovementSimulationInput(
                    forward = 1.0,
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
                return when {
                    feet == landingBlock -> (tick + 1).toDouble()
                    carryAcceptable && feet == carryBlock -> (tick + 1).toDouble()
                    else -> null
                }
            }
            if (current.simulator.state.horizontalCollision) return null
            if (current.position.y < from.y - 0.2) return null
        }
        return null
    }

    private data class Offset(val dx: Int, val dz: Int)

    private companion object {
        const val MAX_SIMULATION_TICKS = 20
        const val INVALIDATION_RADIUS_XZ = 5
        const val INVALIDATION_RADIUS_Y = 3
        const val AXIS_TIE_EPSILON = 0.05

        // Entry-speed envelope endpoints: the sprint band the executor
        // realistically delivers at a takeoff (short approach after spawn or
        // a landing → ~0.22; open sprint or post-landing carry → ~0.30).
        const val ENTRY_SPEED_LOW = 0.22
        const val ENTRY_SPEED_HIGH = 0.30

        // Sprint-jump reach beyond the template gapJump (2 blocks): 2.3–4.3
        // blocks of horizontal displacement, any integer direction — this
        // is where the F3 connections live (angled gaps included).
        val CANDIDATE_OFFSETS: List<Offset> = buildList {
            for (dx in -4..4) for (dz in -4..4) {
                val distance = hypot(dx.toDouble(), dz.toDouble())
                if (distance in 2.3..4.3) add(Offset(dx, dz))
            }
        }
    }
}
