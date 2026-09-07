/*
 * Copyright 2026 Lambda
 */
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SectionStore
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.CollisionClass
import it.unimi.dsi.fastutil.longs.LongArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos

class SnapshotOverlayTest {
    private val bounds = SimulationSnapshotBounds(-16, 0, -16, 31, 31, 31)

    private fun floor(): SnapshotSimulationEnvironment = SnapshotSimulationEnvironment.synthetic(
        bounds = bounds,
        blocks = buildMap {
            for (x in -16..31) for (z in -16..31) put(BlockPos(x, 10, z), SnapshotBlockPhysics.FULL_CUBE)
        },
    )

    @Test
    fun `edits overlay the shared sections without touching the base`() {
        val base = floor()
        val placed = BlockPos(3, 11, 3)
        val removed = BlockPos(5, 10, 5)
        val edited = base.withEdits(
            mapOf(placed to SnapshotBlockPhysics.FULL_CUBE, removed to SnapshotBlockPhysics.AIR),
        )

        assertEquals(CollisionClass.FULL, edited.collisionClass(3, 11, 3))
        assertEquals(CollisionClass.EMPTY, edited.collisionClass(5, 10, 5))
        assertTrue(edited.voxel(3, 11, 3).standable)
        assertFalse(edited.voxel(5, 10, 5).standable)
        assertEquals(1.0, edited.slipperiness(placed) / SnapshotBlockPhysics.DEFAULT_SLIPPERINESS)

        assertEquals(CollisionClass.EMPTY, base.collisionClass(3, 11, 3))
        assertEquals(CollisionClass.FULL, base.collisionClass(5, 10, 5))
        assertNull(base.findSupportingBlockPos(net.minecraft.util.math.Box(3.2, 11.0, 3.2, 3.8, 12.8, 3.8), net.minecraft.util.math.Vec3d(3.5, 11.0, 3.5)))
    }

    @Test
    fun `the section store copies on write and reads without boxing the key`() {
        val key = ChunkSectionPos.asLong(1, 2, 3)
        val section = ImmutableSnapshotSection.Builder().apply { fill(SnapshotBlockPhysics.FULL_CUBE) }.build()
        val empty = SectionStore.EMPTY
        val one = empty.with(key, section)
        assertEquals(0, empty.size)
        assertEquals(1, one.size)
        assertTrue(one.contains(key))
        assertEquals(section, one[key])
        val none = one.without(LongArrayList.of(key))
        assertEquals(1, one.size)
        assertEquals(0, none.size)
        var visited = 0
        one.forEach { k, s -> visited++; assertEquals(key, k); assertEquals(section, s) }
        assertEquals(1, visited)
    }
}
