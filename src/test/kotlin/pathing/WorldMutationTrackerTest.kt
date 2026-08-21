/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.world.PathingChunk
import com.lambda.pathing.world.PathingSection
import com.lambda.pathing.world.WorldMutation
import com.lambda.pathing.world.WorldMutationTracker
import net.minecraft.util.math.BlockPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WorldMutationTrackerTest {
    @Test
    fun `mutations before snapshot do not invalidate it`() {
        val tracker = WorldMutationTracker()
        tracker.markBlock(BlockPos(1, 64, 1))
        val snapshotRevision = tracker.currentRevision()

        assertNull(tracker.changeSince(snapshotRevision, setOf(SECTION), setOf(CHUNK)))
    }

    @Test
    fun `a later block mutation invalidates its entire dependency section`() {
        val tracker = WorldMutationTracker()
        val snapshotRevision = tracker.currentRevision()

        val revision = tracker.markBlock(BlockPos(15, 79, 15))

        val mutation = assertIs<WorldMutation.Section>(
            tracker.changeSince(snapshotRevision, setOf(SECTION), emptySet())
        )
        assertEquals(SECTION, mutation.section)
        assertEquals(revision, mutation.revision)
    }

    @Test
    fun `an unrelated section mutation does not invalidate dependencies`() {
        val tracker = WorldMutationTracker()
        val snapshotRevision = tracker.currentRevision()

        tracker.markBlock(BlockPos(16, 64, 0))

        assertNull(tracker.changeSince(snapshotRevision, setOf(SECTION), setOf(CHUNK)))
    }

    @Test
    fun `a later load or unload invalidates a dependency chunk`() {
        val tracker = WorldMutationTracker()
        val snapshotRevision = tracker.currentRevision()

        val revision = tracker.markChunk(CHUNK.x, CHUNK.z)

        val mutation = assertIs<WorldMutation.Chunk>(
            tracker.changeSince(snapshotRevision, emptySet(), setOf(CHUNK))
        )
        assertEquals(CHUNK, mutation.chunk)
        assertEquals(revision, mutation.revision)
    }

    @Test
    fun `reset starts a fresh revision domain`() {
        val tracker = WorldMutationTracker()
        tracker.markBlock(BlockPos(1, 64, 1))

        tracker.reset()

        assertEquals(0L, tracker.currentRevision())
        assertNull(tracker.changeSince(0L, setOf(SECTION), setOf(CHUNK)))
    }

    private companion object {
        val SECTION = PathingSection(0, 4, 0)
        val CHUNK = PathingChunk(0, 0)
    }
}
