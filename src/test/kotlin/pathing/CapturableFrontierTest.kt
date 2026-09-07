/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.FrontierAnchors
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.ChunkSectionPos
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import pathing.ProbeScenarios.moveLibrary

/**
 * Capture lag is not a frontier.
 *
 * Unknown cells whose chunk the client could capture right now mean the snapshot has
 * not caught up -- the correct response is waiting, never an optimistic anchor. The
 * field bug this pins down: at a cold start the body's own surroundings are still
 * being captured, every neighbouring cell "borders unknown", and the planner
 * manufactured two-block anchor routes in arbitrary directions -- each retired by the
 * next capture batch. The body walked randomly around until the real graph connected.
 */
class CapturableFrontierTest {
    private val allCapturable: (Int, Int) -> Boolean = { _, _ -> true }

    private fun streamedIsland(): SnapshotSimulationEnvironment {
        val unavailableKeys = ConcurrentHashMap.newKeySet<Long>()
        val unavailableSection = ImmutableSnapshotSection.Builder().apply {
            fill(SnapshotBlockPhysics.UNAVAILABLE)
        }.build(expectedWrites = 4096)
        val floor = ImmutableSnapshotSection.Builder().apply {
            for (x in 0..15) for (z in 0..15) for (y in 0..15) {
                set(x, y, z, if (y <= 4) SnapshotBlockPhysics.FULL_CUBE else SnapshotBlockPhysics.AIR)
            }
        }.build(expectedWrites = 4096)
        return SnapshotSimulationEnvironment(
            bounds = SimulationSnapshotBounds(0, 0, 0, 255, 15, 255),
            sections = mapOf(ChunkSectionPos.asLong(0, 0, 0) to floor),
            defaultBlock = null,
            missingSection = { sectionX, sectionY, sectionZ, exact ->
                check(!exact) { "exact simulation must never consume unstreamed terrain" }
                unavailableKeys += ChunkSectionPos.asLong(sectionX, sectionY, sectionZ)
                unavailableSection
            },
            unavailableSectionKeys = unavailableKeys,
        )
    }

    private fun moves() = moveLibrary()

    @Test
    fun `capturable unknowns produce no probe anchors`() {
        val snapshot = streamedIsland()
        val moves = moves()
        val start = Stance(1, 5, 1)
        val goal = Stance(200, 5, 1)

        assertTrue(
            FrontierAnchors.sweep(snapshot, moves, start, goal).isNotEmpty(),
            "a true frontier must still anchor",
        )
        assertTrue(
            FrontierAnchors.sweep(snapshot, moves, start, goal, capturable = allCapturable).isEmpty(),
            "capture lag must not anchor",
        )
    }

    @Test
    fun `capturable unknowns produce no sweep anchors and no route`() {
        val snapshot = streamedIsland()
        val moves = moves()
        val start = Stance(1, 5, 1)
        val goal = Stance(200, 5, 1)
        val planner = CoarsePlanner(snapshot, moves, start, goal, capturable = allCapturable)

        assertFalse(planner.discoverReachableFrontier(), "capture lag must not sweep anchors")
        planner.repair(Duration.INFINITE, maxExpansions = 100_000)
        assertTrue(planner.optimisticAnchors.isEmpty())
        assertNull(
            planner.routePlan(snapshotRevision = 1L),
            "no optimistic route may exist while the surroundings are merely uncaptured",
        )
    }

    /**
     * The arrival regression this pins down: as the body approaches, the goal area's
     * chunks enter render distance and turn capturable moments before capture lands.
     * Retiring anchors on capturability withdrew the route's optimistic edge before
     * knowledge replaced it -- the terminal regressed and the body circled the goal
     * for the capture-lag window. An anchor retires when its ground becomes KNOWN,
     * never merely capturable.
     */
    @Test
    fun `an anchor survives capture lag and retires only once its unknown is known`() {
        var knownMaxX = 15
        var lagging = false
        val view = object : CoarseVoxelView {
            override val simulableStanceY = 0..15

            override fun isKnown(x: Int, y: Int, z: Int): Boolean = x <= knownMaxX

            override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel = when {
                x > knownMaxX -> CoarseVoxel.UNKNOWN
                y < 3 -> CoarseVoxel.FULL_BLOCK
                else -> CoarseVoxel.AIR
            }
        }
        val moves = moves()
        val start = Stance(1, 3, 1)
        val goal = Stance(200, 3, 1)
        val planner = CoarsePlanner(
            view, moves, start, goal,
            capturable = { _, _ -> lagging },
        )

        // Seeded at a true frontier: the unknown beyond x=15 is beyond render distance.
        planner.advanceFrontier(FrontierAnchors.sweep(view, moves, start, goal))
        val seeded = planner.optimisticAnchors
        assertTrue(seeded.isNotEmpty(), "a true frontier must seed anchors")

        // The body approaches: the same unknown is now capturable but not yet captured.
        // The anchor must hold the route steady through the lag.
        lagging = true
        planner.advanceFrontier(emptyMap())
        assertTrue(
            planner.optimisticAnchors.isNotEmpty(),
            "capture lag must not retire anchors -- that regresses the route terminal",
        )

        // Capture lands: the anchor's surroundings are known, and it retires normally.
        knownMaxX = 40
        planner.advanceFrontier(emptyMap())
        assertTrue(planner.optimisticAnchors.isEmpty(), "known ground must retire anchors")
    }

    /**
     * Suppression must come with a demand: the suppressed anchor was also the
     * knowledge pull that routed capture interest toward the lip. The probe and the
     * sweep report every capturable-unknown cell they refused so the caller can prime
     * DEMAND capture there -- otherwise a lagging column that blocks the only
     * crossing (wide diagonal jump corridors are the first casualties) is never
     * captured and the graph refuses to advance indefinitely.
     */
    @Test
    fun `suppressed anchors report the lagging cells`() {
        val snapshot = streamedIsland()
        val moves = moves()
        val start = Stance(1, 5, 1)
        val goal = Stance(200, 5, 1)

        val sweepLag = HashSet<Triple<Int, Int, Int>>()
        FrontierAnchors.sweep(
            snapshot, moves, start, goal,
            capturable = allCapturable,
            onCaptureLag = { x, y, z -> sweepLag += Triple(x, y, z) },
        )
        assertTrue(sweepLag.isNotEmpty(), "the sweep must name the cells it refused to anchor on")
        assertTrue(sweepLag.all { !snapshot.isKnown(it.first, it.second, it.third) })
    }

    @Test
    fun `bordersUnknown distinguishes lag from frontier`() {
        val snapshot = streamedIsland()
        val edge = Stance(15, 5, 1)
        assertTrue(FrontierAnchors.bordersUnknown(snapshot, edge))
        assertFalse(FrontierAnchors.bordersUnknown(snapshot, edge, allCapturable))
    }
}
