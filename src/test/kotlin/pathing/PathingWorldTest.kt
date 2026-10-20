/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.physics.SnapshotSectionUnavailableException
import com.lambda.pathing.physics.WaterPhysics
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.Footing
import com.lambda.pathing.world.TerrainWatch
import com.lambda.pathing.world.snapshot.HazardRules
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The live world over a [FakeTerrainSource]: a flat world with ground at y=64, the player in chunk
 * (0, 0), chunks within radius 2 loaded, everything else unloaded.
 */
class PathingWorldTest {
    private val bounds = SimulationSnapshotBounds(-64, 0, -64, 63, 127, 63)
    private val source = FakeTerrainSource(groundY = 64, viewDistance = 8, detachedCapacity = 2).apply { loadSquare(2) }
    private val world = PathingWorld(bounds, source, HazardRules.AVOID_ALL)
    private val events = world.subscribe()

    private val key = ChunkSectionPos.asLong(0, 4, 0)

    @Test
    fun `chunks beyond the search distance stay unknown until the player comes closer`() {
        val near = FakeTerrainSource(groundY = 64, viewDistance = 8).apply {
            searchDistance = 1
            loadSquare(3)
        }
        val limited = PathingWorld(bounds, near, HazardRules.AVOID_ALL)
        val arrivals = limited.subscribe()
        limited.advance()
        assertTrue(limited.snapshot.isKnown(40, 70, 8), "chunk (2, 0) is within the search distance")
        assertFalse(limited.snapshot.isKnown(56, 70, 8), "chunk (3, 0) is loaded but beyond the search distance")
        assertFalse(limited.loadable(5, 0))
        arrivals.drain()

        near.playerChunkX = 1
        limited.advance()

        assertTrue(limited.snapshot.isKnown(56, 70, 8), "moving closer admits it")
        assertTrue(arrivals.drain().arrived.any { it.contains(56, 70, 8) }, "an admitted chunk arrives like a loaded one")
    }

    @Test
    fun `loaded terrain is known at once and derived by its first read`() {
        assertTrue(world.snapshot.isKnown(3, 70, 3))
        assertFalse(world.isDerived(key), "knowing a section does not derive it")
        assertTrue(world.snapshot.collisionShape(3, 64, 3).isEmpty)
        assertFalse(world.snapshot.collisionShape(3, 63, 3).isEmpty)
        assertTrue(world.isDerived(key))
        assertEquals(0L, world.revision, "deriving is no terrain change")
        assertTrue(events.drain().isEmpty)
    }

    @Test
    fun `a block change patches a derived section at once`() {
        assertTrue(world.snapshot.collisionShape(5, 70, 5).isEmpty)
        val version = world.snapshot.journal!!.version

        source.set(5, 70, 5, SnapshotBlockPhysics.FULL_CUBE)
        world.onBlockChanged(BlockPos(5, 70, 5))

        assertEquals(1L, world.revision)
        assertFalse(world.snapshot.collisionShape(5, 70, 5).isEmpty)
        assertTrue(world.snapshot.collisionShape(6, 70, 5).isEmpty)
        assertEquals(version + 1, world.snapshot.journal!!.version)
        val batch = events.drain()
        assertEquals(setOf(VoxelPos(5, 70, 5)), batch.mutations)
        assertTrue(batch.sections.isEmpty())
        assertEquals(listOf(WorldMutation.Cell(VoxelPos(5, 70, 5), 1L)), world.mutationsSince(0L))
        assertTrue(world.mutationsSince(1L).isEmpty())
    }

    @Test
    fun `a block change that keeps the physics is not a mutation`() {
        world.snapshot.collisionShape(5, 70, 5)
        source.set(5, 70, 5, SnapshotBlockPhysics(VoxelShapes.empty(), coarseVoxel = CoarseVoxel.AIR))
        world.onBlockChanged(BlockPos(5, 70, 5))
        assertEquals(0L, world.revision)
        assertTrue(events.drain().isEmpty)
    }

    @Test
    fun `a block change next to water re-derives the current of the water it feeds`() {
        val still = SnapshotBlockPhysics(VoxelShapes.empty(), coarseVoxel = CoarseVoxel.HAZARD, water = WaterPhysics(0.8888889f, Vec3d.ZERO))
        source.set(5, 70, 5, still)
        assertEquals(Vec3d.ZERO, world.snapshot.water(BlockPos(5, 70, 5))?.flow)

        val flowing = SnapshotBlockPhysics(VoxelShapes.empty(), coarseVoxel = CoarseVoxel.HAZARD, water = WaterPhysics(0.8888889f, Vec3d(1.0, 0.0, 0.0)))
        source.set(5, 70, 5, flowing)
        source.set(6, 70, 5, SnapshotBlockPhysics.FULL_CUBE)
        world.onBlockChanged(BlockPos(6, 70, 5))

        assertEquals(setOf(VoxelPos(6, 70, 5), VoxelPos(5, 70, 5)), events.drain().mutations)
        assertEquals(Vec3d(1.0, 0.0, 0.0), world.snapshot.water(BlockPos(5, 70, 5))?.flow)
    }

    @Test
    fun `a block change nobody has read is no event and the first read sees it`() {
        source.set(5, 70, 5, SnapshotBlockPhysics.FULL_CUBE)
        world.onBlockChanged(BlockPos(5, 70, 5))
        assertEquals(0L, world.revision)
        assertTrue(events.drain().isEmpty)
        assertFalse(world.snapshot.collisionShape(5, 70, 5).isEmpty)
    }

    @Test
    fun `a derivation that raced a block change is redone instead of installing the old cell`() {
        source.duringDerive = {
            source.duringDerive = null
            source.set(5, 70, 5, SnapshotBlockPhysics.FULL_CUBE)
            world.onBlockChanged(BlockPos(5, 70, 5))
        }
        assertFalse(world.snapshot.collisionShape(5, 70, 5).isEmpty)
        assertEquals(2L, source.derived.get(), "the first derivation read the old cell and was discarded")
        assertEquals(0L, world.revision, "nobody had read the section yet")
    }

    @Test
    fun `an unloaded chunk is unknown until it loads, then it arrives`() {
        assertFalse(world.snapshot.isKnown(50, 70, 3))
        assertFailsWith<SnapshotSectionUnavailableException> { world.snapshot.slipperiness(BlockPos(50, 70, 3)) }
        assertEquals(1L, world.exactMisses)
        assertFalse(world.loadable(40, 0))
        assertTrue(world.loadable(3, 0))

        world.onChunkLoaded(3, 0)

        val batch = events.drain()
        assertEquals((0..7).map { PathingSection(3, it, 0) }.toSet(), batch.sections)
        assertEquals(listOf(CellBox(48..63, 0..127, 0..15)), batch.arrived)
        assertTrue(world.snapshot.isKnown(50, 70, 3))
        assertEquals(SnapshotBlockPhysics.DEFAULT_SLIPPERINESS, world.snapshot.slipperiness(BlockPos(50, 63, 3)))
    }

    @Test
    fun `a resent chunk reports exactly the cells that changed under derived sections`() {
        world.snapshot.collisionShape(3, 70, 5)
        source.set(3, 70, 5, SnapshotBlockPhysics.FULL_CUBE)
        source.set(3, 100, 5, SnapshotBlockPhysics.FULL_CUBE)

        world.onChunkLoaded(0, 0)

        val batch = events.drain()
        assertEquals(setOf(VoxelPos(3, 70, 5)), batch.mutations, "the section above was never read")
        assertTrue(batch.sections.isEmpty(), "a resend is no arrival")
        assertTrue(batch.chunks.isEmpty())
        assertFalse(world.snapshot.collisionShape(3, 70, 5).isEmpty)
        assertEquals(1L, world.chunkReloads)
        assertEquals(1L, world.reloadsChanged)
    }

    @Test
    fun `unloaded chunks stay readable until they are evicted, then they are forgotten`() {
        world.snapshot.collisionShape(3, 70, 5)
        world.onChunkUnloaded(0, 0, null)
        world.onChunkUnloaded(1, 0, null)
        assertTrue(world.snapshot.isKnown(20, 30, 3), "a detached chunk keeps its frozen sections")
        assertTrue(events.drain().isEmpty)

        world.onChunkUnloaded(2, 0, null)

        assertTrue(events.drain().isEmpty, "forgetting sections nobody derived changes nothing a planner read")
        assertEquals(1L, world.chunksForgotten)
        assertTrue(world.snapshot.isKnown(3, 70, 5), "a derived section outlives its chunk")
        assertFalse(world.snapshot.isKnown(3, 30, 5), "an underived one is forgotten")
        assertTrue(world.snapshot.isKnown(20, 30, 3))
    }

    @Test
    fun `a watch reports the boxes a change touched and resets when the journal overflows`() {
        val watch = TerrainWatch(world.snapshot)
        world.snapshot.collisionShape(5, 70, 5)
        assertEquals(emptyList(), watch.changes())

        source.set(5, 70, 5, SnapshotBlockPhysics.FULL_CUBE)
        world.onBlockChanged(BlockPos(5, 70, 5))
        assertEquals(listOf(CellBox(5..5, 70..70, 5..5)), watch.changes())

        repeat((1 shl 16) + 1000) { index ->
            val physics = if (index % 2 == 0) SnapshotBlockPhysics.AIR else SnapshotBlockPhysics.FULL_CUBE
            source.set(5, 70, 5, physics)
            world.onBlockChanged(BlockPos(5, 70, 5))
        }
        assertNull(watch.changes(), "entries were overwritten")
        assertEquals(emptyList(), watch.changes())
    }

    @Test
    fun `cached footholds follow a block change through the journal`() {
        val stance = Stance(5, 64, 5)
        assertFalse(Footing.holds(world.snapshot, stance).isEmpty)
        source.set(5, 63, 5, SnapshotBlockPhysics.AIR)
        world.onBlockChanged(BlockPos(5, 63, 5))
        assertTrue(Footing.holds(world.snapshot, stance).isEmpty, "the cached holds must drop with the floor")
    }

    @Test
    fun `advancing insists on the client thread`() {
        source.onClientThread = false
        assertFailsWith<IllegalStateException> { world.advance() }
    }

    @Test
    fun `awaitEvents observes a change and close`() {
        world.snapshot.collisionShape(5, 70, 5)
        assertFalse(world.awaitEvents(0L, 1L))
        source.set(5, 70, 5, SnapshotBlockPhysics.FULL_CUBE)
        world.onBlockChanged(BlockPos(5, 70, 5))
        assertTrue(world.awaitEvents(0L, 0L))
        world.close()
        assertFalse(world.awaitEvents(1L, 10_000L))
    }
}
