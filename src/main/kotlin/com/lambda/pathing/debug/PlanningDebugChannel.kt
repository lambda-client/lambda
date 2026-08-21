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

object PlanningDebugChannel {
    class Attempt(
        val points: List<Vec3d>,
        val certified: Boolean,
        val diagnostic: String?,
    )

    @Volatile
    private var active = false

    @Volatile
    var coarseRoute: CoarseRoutePlan? = null
        private set

    @Volatile
    var attempts: List<Attempt> = emptyList()
        private set

    class CandidateLine(val points: List<Vec3d>, val best: Boolean)

    @Volatile
    var candidateLines: List<CandidateLine> = emptyList()
        private set

    fun publishCandidates(lines: List<CandidateLine>) {
        if (active) candidateLines = lines
    }

    private val ring = ArrayDeque<Attempt>()

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

    private const val MAX_ATTEMPTS = 32

    private const val DECIMATION = 2
}
