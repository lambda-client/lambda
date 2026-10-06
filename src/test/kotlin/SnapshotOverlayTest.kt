/*
 * Copyright 2026 Lambda
 */
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SectionStore
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import it.unimi.dsi.fastutil.longs.LongArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.minecraft.util.math.ChunkSectionPos

class SnapshotOverlayTest {
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
