/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.physics.SnapshotSectionUnavailableException
import com.lambda.pathing.physics.WaterPhysics
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.streaming.InterestTier
import com.lambda.pathing.world.streaming.PathingWorld
import com.lambda.pathing.world.streaming.WorldMutation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The world coordinator driven by a [FakeCaptureSource]: a flat world with ground at y=64,
 * the player in chunk (0, 0), chunks within radius 2 loaded, everything else unloaded.
 */
class PathingWorldTest {
    private val bounds = SimulationSnapshotBounds(-64, 0, -64, 63, 127, 63)
    private val source = FakeCaptureSource(groundY = 64, viewDistance = 8).apply { loadSquare(2) }
    private val world = PathingWorld(bounds, source)
    private val events = world.subscribe()

    private val key = ChunkSectionPos.asLong(0, 4, 0)
    private val section = PathingSection(0, 4, 0)

    @Test
    fun `interest is captured, installed at once and bumps the revision`() {
        source.set(3, 64, 3, SnapshotBlockPhysics.FULL_CUBE)
        world.interest(listOf(key), InterestTier.BODY)
        assertEquals(1, world.pendingInterest)
        assertFalse(world.snapshot.hasSection(key))

        world.advance(1_000.0)

        assertTrue(world.snapshot.hasSection(key))
        assertEquals(0, world.pendingInterest)
        assertEquals(1L, world.revision)
        assertFalse(world.snapshot.collisionShape(3, 64, 3).isEmpty)
        assertTrue(world.snapshot.collisionShape(3, 65, 3).isEmpty)
        assertTrue(world.captureLedger().startsWith("1 sections/4096 cells"))

        val batch = events.drain()
        assertEquals(1L, batch.revision)
        assertEquals(setOf(section), batch.sections)
        assertTrue(batch.mutations.isEmpty() && batch.chunks.isEmpty())
        assertTrue(events.drain().isEmpty)
    }

    @Test
    fun `a tiny budget still writes one cell per call and completes eventually`() {
        world.interest(listOf(key), InterestTier.CORRIDOR)
        world.advance(0.0)
        assertEquals(1, world.pendingInterest, "the active section counts as pending")
        assertFalse(world.snapshot.hasSection(key))
        assertEquals(1L, source.reads)
        repeat(4095) { world.advance(0.0) }
        assertTrue(world.snapshot.hasSection(key))
        assertEquals(4096L, source.reads)
    }

    @Test
    fun `a block change patches its cell and installs the patch only on drain`() {
        world.interest(listOf(key), InterestTier.BODY)
        world.advance(1_000.0)
        events.drain()

        source.set(5, 70, 5, SnapshotBlockPhysics.FULL_CUBE)
        world.onBlockChanged(BlockPos(5, 70, 5))
        assertEquals(2L, world.revision, "the mutation is a revision on its own")
        assertEquals(0, world.pendingDemand, "the cell is patched, nothing is re-captured")
        assertTrue(world.snapshot.collisionShape(5, 70, 5).isEmpty, "the patch is parked until drain")

        val batch = events.drain()
        assertFalse(world.snapshot.collisionShape(5, 70, 5).isEmpty)
        assertTrue(world.snapshot.collisionShape(6, 70, 5).isEmpty)
        assertTrue(batch.sections.isEmpty())
        assertEquals(setOf(VoxelPos(5, 70, 5)), batch.mutations)
        assertEquals(listOf(WorldMutation.Cell(VoxelPos(5, 70, 5), 2L)), world.mutationsSince(1L))
        assertTrue(world.mutationsSince(2L).isEmpty())
    }

    @Test
    fun `a block change that keeps the recorded physics is not a mutation`() {
        world.interest(listOf(key), InterestTier.BODY)
        world.advance(1_000.0)
        events.drain()

        source.set(5, 70, 5, SnapshotBlockPhysics(VoxelShapes.empty(), coarseVoxel = CoarseVoxel.AIR))
        world.onBlockChanged(BlockPos(5, 70, 5))
        assertEquals(1L, world.revision)
        assertEquals(0, world.pendingDemand, "nothing to re-capture")
        assertTrue(events.drain().isEmpty)
    }

    @Test
    fun `a block change next to water re-samples the current of the water it feeds`() {
        val still = SnapshotBlockPhysics(VoxelShapes.empty(), coarseVoxel = CoarseVoxel.HAZARD, water = WaterPhysics(0.8888889f, Vec3d.ZERO))
        source.set(5, 70, 5, still)
        world.interest(listOf(key), InterestTier.BODY)
        world.advance(1_000.0)
        events.drain()

        val flowing = SnapshotBlockPhysics(VoxelShapes.empty(), coarseVoxel = CoarseVoxel.HAZARD, water = WaterPhysics(0.8888889f, Vec3d(1.0, 0.0, 0.0)))
        source.set(5, 70, 5, flowing)
        source.set(6, 70, 5, SnapshotBlockPhysics.FULL_CUBE)
        world.onBlockChanged(BlockPos(6, 70, 5))

        assertEquals(setOf(VoxelPos(6, 70, 5), VoxelPos(5, 70, 5)), events.drain().mutations)
        assertEquals(Vec3d(1.0, 0.0, 0.0), world.snapshot.water(BlockPos(5, 70, 5))?.flow)
    }

    @Test
    fun `a block change in an uncaptured section is ignored`() {
        source.set(5, 70, 5, SnapshotBlockPhysics.FULL_CUBE)
        world.onBlockChanged(BlockPos(5, 70, 5))
        assertEquals(0L, world.revision)
        assertEquals(0, world.pendingDemand, "nothing to re-capture")
        assertTrue(events.drain().isEmpty)
    }

    @Test
    fun `a block change during a first capture patches the cells the capture already wrote`() {
        world.interest(listOf(key), InterestTier.BODY)
        world.advance(0.0)
        source.set(1, 65, 1, SnapshotBlockPhysics.FULL_CUBE)
        world.onBlockChanged(BlockPos(1, 65, 1))
        source.set(0, 64, 0, SnapshotBlockPhysics.FULL_CUBE)
        world.onBlockChanged(BlockPos(0, 64, 0))
        assertEquals(1, world.pendingInterest, "the capture carries on")
        assertEquals(0, world.pendingDemand)
        world.advance(1_000.0)
        assertEquals(1L, world.revision)
        assertFalse(world.snapshot.collisionShape(0, 64, 0).isEmpty, "the written cell was patched")
        assertFalse(world.snapshot.collisionShape(1, 65, 1).isEmpty, "the unwritten cell was captured fresh")
        val batch = events.drain()
        assertEquals(setOf(section), batch.sections)
        assertTrue(batch.mutations.isEmpty(), "nothing depended on a section that was never installed")
    }

    @Test
    fun `a block change in a dropped section patches its re-capture instead of recording a mutation`() {
        world.interest(listOf(key), InterestTier.BODY)
        world.advance(1_000.0)
        events.drain()
        world.onChunkEvent(0, 0)
        world.advance(0.0)
        assertEquals(1, world.pendingInterest, "re-capture in flight")
        source.set(0, 64, 0, SnapshotBlockPhysics.FULL_CUBE)
        world.onBlockChanged(BlockPos(0, 64, 0))
        assertEquals(2L, world.revision, "the chunk event already covers the section")
        world.advance(1_000.0)
        assertEquals(3L, world.revision)
        val batch = events.drain()
        assertFalse(world.snapshot.collisionShape(0, 64, 0).isEmpty)
        assertTrue(batch.mutations.isEmpty())
    }

    @Test
    fun `a chunk event bumps only when the chunk had captured content`() {
        world.onChunkEvent(0, 0)
        assertEquals(0L, world.revision)
        assertTrue(events.drain().isEmpty)

        world.interest(listOf(key), InterestTier.BODY)
        world.advance(1_000.0)
        events.drain()

        world.onChunkEvent(0, 0)
        assertEquals(2L, world.revision)
        assertEquals(1, world.pendingDemand, "content sections are re-queued at demand tier")
        val batch = events.drain()
        assertEquals(setOf(PathingChunk(0, 0)), batch.chunks)
        assertTrue(batch.mutations.isEmpty(), "chunk events are not block mutations")
        assertFalse(world.snapshot.hasSection(key), "drain dropped the stale section")
        assertEquals(listOf(WorldMutation.Chunk(PathingChunk(0, 0), 2L)), world.mutationsSince(1L))
    }

    @Test
    fun `an exact read of an uncaptured section queues its 27-neighbourhood at demand tier`() {
        assertFailsWith<SnapshotSectionUnavailableException> { world.snapshot.slipperiness(BlockPos(3, 70, 3)) }
        assertEquals(27, world.pendingDemand)
        world.advance(1_000.0)
        assertEquals(27L, world.revision)
        for (dy in -1..1) for (dz in -1..1) for (dx in -1..1) {
            assertTrue(world.snapshot.hasSection(ChunkSectionPos.asLong(dx, 4 + dy, dz)))
        }
    }

    @Test
    fun `a miss at the vertical edge clips the neighbourhood to the bounds`() {
        assertFailsWith<SnapshotSectionUnavailableException> { world.snapshot.slipperiness(BlockPos(3, 5, 3)) }
        assertEquals(18, world.pendingDemand)
    }

    @Test
    fun `interest in an unloaded chunk waits and is captured once the chunk loads`() {
        val far = ChunkSectionPos.asLong(3, 4, 0)
        world.interest(listOf(far, key), InterestTier.BODY)
        world.advance(1_000.0)
        assertTrue(world.snapshot.hasSection(key))
        assertFalse(world.snapshot.hasSection(far))
        assertEquals(0, world.pendingInterest, "deferred keys are not pending interest")
        assertTrue(world.chunkCapturable(0, 0))
        assertFalse(world.chunkCapturable(3, 0))

        source.load(3, 0)
        world.advance(1_000.0)
        assertTrue(world.snapshot.hasSection(far))
        assertFalse(world.chunkCapturable(3, 0), "the published set refreshes every fourth tick")
        repeat(3) { world.advance(1_000.0) }
        assertTrue(world.chunkCapturable(3, 0))
    }

    @Test
    fun `interestBlocks covers every section touching the box within bounds`() {
        world.interestBlocks(-1, -20, 0, 16, 20, 15, InterestTier.CORRIDOR)
        assertEquals(3 * 2 * 1, world.pendingInterest, "x sections -1..1, y clipped to sections 0..1, z section 0")
    }

    @Test
    fun `capture insists on the client thread`() {
        source.onClientThread = false
        assertFailsWith<IllegalStateException> { world.advance(1.0) }
    }

    @Test
    fun `awaitEvents observes a capture and close`() {
        world.interest(listOf(key), InterestTier.BODY)
        assertFalse(world.awaitEvents(0L, 1L))
        world.advance(1_000.0)
        assertTrue(world.awaitEvents(0L, 0L))
        world.close()
        assertFalse(world.awaitEvents(1L, 10_000L))
    }
}
