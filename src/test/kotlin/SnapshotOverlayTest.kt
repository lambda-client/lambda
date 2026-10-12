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

    @Test
    fun `the section store keeps every key across region boundaries and negative coordinates`() {
        val section = ImmutableSnapshotSection.Builder().apply { fill(SnapshotBlockPhysics.FULL_CUBE) }.build()
        val keys = buildList {
            for (x in -5..4) for (y in -9..8 step 3) for (z in -6..5 step 2) add(ChunkSectionPos.asLong(x, y, z))
            add(ChunkSectionPos.asLong(-2_000_000, -500_000, 1_999_999))
            add(ChunkSectionPos.asLong(1_999_999, 500_000, -2_000_000))
        }
        val store = keys.fold(SectionStore.EMPTY) { store, key -> store.with(key, section) }
        assertEquals(keys.size, store.size)
        assertTrue(keys.all { store[it] === section })
        assertEquals(null, store[ChunkSectionPos.asLong(-5, -8, -6)])
        val visited = HashSet<Long>()
        store.forEach { key, _ -> assertTrue(visited.add(key), "key $key visited twice") }
        assertEquals(keys.toSet(), visited)
        val removed = keys.filterIndexed { index, _ -> index % 3 == 0 }
        val trimmed = store.without(LongArrayList(removed))
        assertEquals(keys.size - removed.size, trimmed.size)
        assertTrue(removed.none { trimmed.contains(it) })
        assertTrue(keys.filterIndexed { index, _ -> index % 3 != 0 }.all { trimmed[it] === section })
        assertTrue(keys.all { store[it] === section }, "removal must not touch the store it was derived from")
    }
}
