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
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.core.Stance
import com.lambda.pathing.session.CoarseService
import com.lambda.pathing.session.InterestPrimer
import com.lambda.pathing.world.streaming.PathingWorld
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import net.minecraft.util.math.BlockPos
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * The in-game cold start, end to end below the session layer: a [PathingWorld] fed by a
 * [FakeCaptureSource] on a "client thread", and the journey's [CoarseService] thread
 * serving the first route the way a planning job waits for it. Far goals at negative
 * coordinates, the case the bedrock corpus (all near the origin, all pre-captured) never
 * exercises.
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

    private fun resolveColdStart(rig: Rig): Pair<com.lambda.pathing.coarse.CoarseRoutePlan?, String> {
        val world = rig.world
        val start = rig.start
        val goal = rig.goal
        InterestPrimer.primeJourney(world, start, goal)
        val state = CoarsePlanningState(
            world.snapshot, SimpleMoveOptions(), start, goal,
        )
        val coarse = CoarseService(world, listOf(state), listOf(start), expansionBudget = 200_000)
        try {
            coarse.start()
            coarse.follow(0, start, SpeedClass.STOPPED)
            val began = System.nanoTime()
            val route = coarse.awaitRoute(0, start, cancelled = { false })?.route
            val report = "route=${route?.nodes?.size} nodes to ${route?.goal} in ${(System.nanoTime() - began) / 1_000_000} ms; " +
                "sections=${world.snapshot.sectionCount} capture=${world.captureLedger()}; failure=${coarse.failure(0, start) ?: "-"}"
            println("[cold] $report")
            return route to report
        } finally {
            coarse.close()
        }
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
