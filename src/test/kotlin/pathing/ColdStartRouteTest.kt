/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.core.Stance
import com.lambda.pathing.session.RouteResolution
import com.lambda.pathing.world.InterestPrimer
import com.lambda.pathing.world.PathingWorld
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import net.minecraft.util.math.BlockPos
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration

/**
 * The in-game cold start, end to end below the session layer: a [PathingWorld] fed by a
 * [FakeCaptureSource] on a "client thread", and the planner thread doing exactly what
 * `TrajectoryPlanner.planAsync` does before the trajectory search -- knowledge wait,
 * coarse repair, route resolution. Far goals at negative coordinates, the case the
 * bedrock corpus (all near the origin, all pre-captured) never exercises.
 */
class ColdStartRouteTest {

    /** Bedrock-like relief: 0..2 blocks above the floor, hashed per column. */
    private fun relief(x: Int, z: Int): Int {
        var h = x * 374761393 + z * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return (h ushr 28) % 3
    }

    private class Rig(
        val start: Stance,
        val goal: Stance,
        loadedRadiusChunks: Int,
        viewDistance: Int,
        rough: ((Int, Int) -> Int)? = null,
    ) {
        val source = FakeCaptureSource(
            groundY = start.y,
            playerChunkX = start.x shr 4,
            playerChunkZ = start.z shr 4,
            viewDistance = viewDistance,
        ).apply {
            for (dx in -loadedRadiusChunks..loadedRadiusChunks) for (dz in -loadedRadiusChunks..loadedRadiusChunks) {
                load(playerChunkX + dx, playerChunkZ + dz)
            }
            if (rough != null) {
                val r = (loadedRadiusChunks + 1) * 16
                for (x in start.x - r..start.x + r) for (z in start.z - r..start.z + r) {
                    for (dy in 0 until rough(x, z)) set(x, start.y + dy, z, com.lambda.pathing.world.snapshot.SnapshotBlockPhysics.FULL_CUBE)
                }
            }
        }
        val world = PathingWorld(SimulationSnapshotBounds(Int.MIN_VALUE, -64, Int.MIN_VALUE, Int.MAX_VALUE, 319, Int.MAX_VALUE), source)
        val running = AtomicBoolean(true)

        /** The client thread: body interest and capture every "tick". */
        val client = thread(name = "fake-client", isDaemon = true) {
            source.onClientThread = true
            while (running.get()) {
                InterestPrimer.primeBody(world, BlockPos(start.x, start.y, start.z))
                world.advance(15.0)
                Thread.sleep(10)
            }
        }

        fun stop() {
            running.set(false)
            client.join()
        }
    }

    private fun resolveColdStart(rig: Rig, horizon: Int = 4): Pair<com.lambda.pathing.coarse.CoarseRoutePlan?, String> {
        val world = rig.world
        val start = rig.start
        val goal = rig.goal
        InterestPrimer.primeJourney(world, start, goal)
        val state = CoarsePlanningState(
            world.snapshot, SimpleMoveOptions(), start, goal,
            horizonChunks = horizon, capturable = world::chunkCapturable,
        )

        // awaitStartKnowledge, as planAsync does it.
        val bodyDeadline = System.nanoTime() + 1_500_000_000L
        while (System.nanoTime() < bodyDeadline && !world.snapshot.isKnown(start.x, start.y - 1, start.z)) {
            if (!world.awaitEvents(world.revision, 50)) break
        }
        val interestDeadline = System.nanoTime() + 1_500_000_000L
        while (System.nanoTime() < interestDeadline && world.pendingDemand > 0) {
            if (!world.awaitEvents(world.revision, 50)) break
        }

        val batch = world.drainEvents()
        state.repairFrom(start, emptySet(), emptySet())
        state.applyEvents(batch)
        val planner = state.planner
        val coarseStarted = System.nanoTime()
        val coarse = planner.repair(Duration.INFINITE, maxExpansions = 200_000)
        val coarseMillis = (System.nanoTime() - coarseStarted) / 1_000_000
        val sections = world.snapshot.storageStats().sections
        println("[cold] coarse: ${coarse.processedNodes} expansions, ${planner.graphSize} nodes, $sections sections, $coarseMillis ms, converged=${coarse.converged}")

        val resolution = RouteResolution(state)
        val routeStarted = System.nanoTime()
        val route = resolution.resolve(start, world.revision, 200_000, world)
        val routeMillis = (System.nanoTime() - routeStarted) / 1_000_000
        val report = "route=${route?.nodes?.size} nodes to ${route?.goal} in $routeMillis ms; ${resolution.lastResolveReport}; " +
            "sections=${world.snapshot.storageStats().sections} capture=${world.captureLedger()}; " +
            "failure=${if (route == null) planner.routeFailureReport() else "-"}"
        println("[cold] $report")
        if (route == null) {
            val view = planner.view
            val moves = planner.moves
            println(
                "[cold] no route: startIsStance=${moves.isStance(view, start)} startEdges=${moves.edgesFrom(view, start).size} " +
                    "anchors=${planner.optimisticAnchors.size} pendingInterest=${world.pendingInterest} pendingDemand=${world.pendingDemand}",
            )
        }
        return route to report
    }

    @Test
    fun `a goal 112 blocks away inside the loaded area routes on a flat floor`() {
        val rig = Rig(Stance(-82, 4, -91), Stance(-164, 4, -13), loadedRadiusChunks = 10, viewDistance = 8)
        try {
            val (route, report) = resolveColdStart(rig)
            assertNotNull(route, "flat floor, everything loaded: $report")
        } finally {
            rig.stop()
        }
    }

    @Test
    fun `a goal beyond the ring with everything capturable anchors at the ring edge`() {
        val blocks = HashMap<BlockPos, com.lambda.pathing.world.snapshot.SnapshotBlockPhysics>()
        for (x in -180..-60) for (z in -110..0) for (y in 0..3) {
            blocks[BlockPos(x, y, z)] = com.lambda.pathing.world.snapshot.SnapshotBlockPhysics.FULL_CUBE
        }
        val environment = com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment.synthetic(
            bounds = SimulationSnapshotBounds(-190, -8, -120, -50, 40, 10), blocks = blocks,
        )
        val start = Stance(-82, 4, -91)
        val goal = Stance(-164, 4, -13)
        val state = CoarsePlanningState(
            environment, SimpleMoveOptions(), start, goal, horizonChunks = 4, capturable = { _, _ -> true },
        )
        state.repairFrom(start, emptySet(), emptySet())
        state.planner.repair(Duration.INFINITE, maxExpansions = 1_000_000)
        val route = RouteResolution(state).resolve(start, 1L, 1_000_000, world = null)
        println("[ring] route=${route?.nodes?.size} to ${route?.goal}")
        assertNotNull(route, "the ring edge must anchor: ${state.planner.routeFailureReport()}")
    }

    @Test
    fun `the 112 block case with no planning horizon`() {
        val rig = Rig(Stance(-82, 4, -91), Stance(-164, 4, -13), loadedRadiusChunks = 10, viewDistance = 8)
        try {
            val (route, report) = resolveColdStart(rig, horizon = 0)
            assertNotNull(route, "flat floor, no horizon: $report")
        } finally {
            rig.stop()
        }
    }

    @Test
    fun `a goal 280 blocks away beyond render distance resolves in bounded time`() {
        val rig = Rig(Stance(18, 4, -229), Stance(-164, 4, -13), loadedRadiusChunks = 8, viewDistance = 8)
        try {
            val (route, report) = resolveColdStart(rig)
            assertNotNull(route, "flat floor beyond render distance: $report")
        } finally {
            rig.stop()
        }
    }

    @Test
    fun `a goal 280 blocks away over rough bedrock resolves in bounded time`() {
        val start = Stance(36, 4 + relief(36, -235), -235)
        val rig = Rig(start, Stance(-164, 5, -13), loadedRadiusChunks = 8, viewDistance = 8, rough = ::relief)
        try {
            val (route, report) = resolveColdStart(rig)
            assertNotNull(route, "rough floor beyond render distance: $report")
        } finally {
            rig.stop()
        }
    }

    @Test
    fun `a goal 200 blocks away beyond the loaded area routes to an anchor`() {
        val rig = Rig(Stance(27, 4, -54), Stance(-164, 4, -13), loadedRadiusChunks = 6, viewDistance = 8)
        try {
            val (route, report) = resolveColdStart(rig)
            assertNotNull(route, "flat floor, goal unloaded: $report")
        } finally {
            rig.stop()
        }
    }
}
