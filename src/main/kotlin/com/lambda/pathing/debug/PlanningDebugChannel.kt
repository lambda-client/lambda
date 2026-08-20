/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.debug

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.trajectory.TrajectoryDiagnostic
import com.lambda.pathing.trajectory.TrajectoryRollout
import net.minecraft.util.math.Vec3d

/**
 * Worker-to-renderer sidechannel for watching a plan get built: the coarse route the
 * moment D* converges (and each reroute), and the candidate rollouts the seed search
 * tries and keeps. Everything published here is immutable and advisory --
 * nothing reads it back into planning, so it can never affect a plan.
 *
 * Writes are volatile swaps of immutable snapshots; the renderer reads whatever
 * version it happens to see. Publication is gated on [active] so the per-attempt
 * copying costs nothing when the debug render is off.
 */
object PlanningDebugChannel {
    /** One simulated candidate, decimated for rendering. */
    class Attempt(
        val points: List<Vec3d>,
        /** Reached a certified stable stop (a publishable candidate). */
        val certified: Boolean,
        /** Simple name of the failure that ended it, when it failed. */
        val diagnostic: String?,
    )

    @Volatile
    private var active = false

    /** The current coarse route, from first convergence through every reroute. */
    @Volatile
    var coarseRoute: CoarseRoutePlan? = null
        private set

    /** Most recent candidate rollouts, oldest first. */
    @Volatile
    var attempts: List<Attempt> = emptyList()
        private set

    /**
     * One candidate continuation: where a line the search is keeping alive would go.
     *
     * Coarse by design -- a point per anchor, not per frame. These exist to show *which
     * options are on the table*, and at anchor resolution the alternatives are already
     * distinguishable while costing almost nothing to publish.
     */
    class CandidateLine(val points: List<Vec3d>, val best: Boolean)

    /**
     * The candidates the horizon is currently choosing between, best flagged.
     *
     * The population is the whole point of a horizon: several ways to spend the next
     * stretch, kept alive until one has to be picked. Without seeing them there is no way
     * to tell a search that is genuinely weighing options from one that is following the
     * only line it has.
     */
    @Volatile
    var candidateLines: List<CandidateLine> = emptyList()
        private set

    fun publishCandidates(lines: List<CandidateLine>) {
        if (active) candidateLines = lines
    }

    private val ring = ArrayDeque<Attempt>()

    /** Called by the manager when planning starts; wipes the previous plan's debris. */
    fun begin(enabled: Boolean) {
        synchronized(ring) { ring.clear() }
        coarseRoute = null
        attempts = emptyList()
        candidateLines = emptyList()
        active = enabled
    }

    fun publishRoute(route: CoarseRoutePlan) {
        if (active) coarseRoute = route
    }


    fun publishAttempt(rollout: TrajectoryRollout, certified: Boolean, diagnostic: TrajectoryDiagnostic?) {
        if (!active) return
        val points = ArrayList<Vec3d>(rollout.frames.size / DECIMATION + 2)
        points += rollout.initialState.position
        for (index in rollout.frames.indices step DECIMATION) {
            points += rollout.frames[index].state.position
        }
        rollout.frames.lastOrNull()?.let { last ->
            if ((rollout.frames.size - 1) % DECIMATION != 0) points += last.state.position
        }
        val attempt = Attempt(points, certified, diagnostic?.let { it::class.simpleName })
        synchronized(ring) {
            ring += attempt
            while (ring.size > MAX_ATTEMPTS) ring.removeFirst()
            attempts = ArrayList(ring)
        }
    }

    fun reset() {
        active = false
        coarseRoute = null
        attempts = emptyList()
        candidateLines = emptyList()
        synchronized(ring) { ring.clear() }
    }

    /** Newest rollouts a human can still tell apart on screen. */
    private const val MAX_ATTEMPTS = 32

    /** Recent cut points worth showing; older ones say nothing the newest do not. */

    private const val DECIMATION = 2
}
