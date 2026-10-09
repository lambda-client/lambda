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
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.world.streaming.RevisionLog
import com.lambda.pathing.world.streaming.WorldMutation
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RevisionLogTest {
    private val section = PathingSection(1, 4, 2)
    private val cell = VoxelPos(21, 70, 37)
    private val chunk = PathingChunk(1, 2)

    @Test
    fun `every record bumps the revision by one and signal does not`() {
        val log = RevisionLog()
        assertEquals(0L, log.revision)
        log.recordCaptured(section)
        assertEquals(1L, log.revision)
        log.recordMutation(cell)
        assertEquals(2L, log.revision)
        log.recordChunkChanged(chunk)
        assertEquals(3L, log.revision)
        log.signal()
        assertEquals(3L, log.revision)
    }

    @Test
    fun `mutationsSince lists the cells and chunks that changed after a revision, newest cell first`() {
        val log = RevisionLog()
        log.recordCaptured(PathingSection(9, 9, 9))
        assertTrue(log.mutationsSince(0L).isEmpty(), "captures are not mutations")

        val other = VoxelPos(20, 70, 36)
        log.recordMutation(cell)
        log.recordMutation(other)
        assertEquals(listOf(WorldMutation.Cell(other, 3L), WorldMutation.Cell(cell, 2L)), log.mutationsSince(1L))
        assertEquals(listOf(WorldMutation.Cell(other, 3L)), log.mutationsSince(2L))

        log.recordChunkChanged(chunk)
        assertEquals(listOf(WorldMutation.Chunk(chunk, 4L)), log.mutationsSince(3L))
        assertTrue(log.mutationsSince(4L).isEmpty())
    }

    @Test
    fun `drain hands over pending changes once`() {
        val log = RevisionLog()
        val subscription = log.subscribe()
        log.recordCaptured(section)
        log.recordMutation(cell)
        log.recordChunkChanged(chunk)
        val batch = subscription.drain()
        assertEquals(3L, batch.revision)
        assertEquals(setOf(section), batch.sections)
        assertEquals(setOf(cell), batch.mutations)
        assertEquals(setOf(chunk), batch.chunks)
        assertTrue(subscription.drain().isEmpty)
    }

    @Test
    fun `mutated cells invalidate their bounding box per section, a chunk event its column and arrivals the rest`() {
        val log = RevisionLog()
        val subscription = log.subscribe()
        log.recordMutation(VoxelPos(3, 70, 5))
        log.recordMutation(VoxelPos(9, 66, 2))
        log.recordMutation(VoxelPos(20, 70, 3))
        log.recordChunkChanged(PathingChunk(1, 0))
        log.recordCaptured(PathingSection(0, 6, 0))
        log.recordCaptured(PathingSection(1, 5, 0))
        log.recordCaptured(PathingSection(2, 3, 0))
        val batch = subscription.drain()
        assertEquals(listOf(CellBox.column(PathingChunk(1, 0)), CellBox(3..9, 66..70, 2..5)), batch.changed)
        assertEquals(
            setOf(CellBox.sections(PathingChunk(0, 0), 6, 6), CellBox.sections(PathingChunk(2, 0), 3, 3)),
            batch.arrived.toSet(),
        )
    }

    @Test
    fun `every subscriber drains its own copy of the changes recorded after it subscribed`() {
        val log = RevisionLog()
        val early = log.subscribe()
        log.recordCaptured(section)
        val late = log.subscribe()
        log.recordChunkChanged(chunk)
        val earlyBatch = early.drain()
        assertEquals(setOf(section), earlyBatch.sections)
        assertEquals(setOf(chunk), earlyBatch.chunks)
        val lateBatch = late.drain()
        assertTrue(lateBatch.sections.isEmpty(), "the capture happened before it subscribed")
        assertEquals(setOf(chunk), lateBatch.chunks)
        late.close()
        log.recordCaptured(PathingSection(7, 7, 7))
        assertTrue(late.drain().isEmpty, "a closed subscription receives nothing")
        assertEquals(setOf(PathingSection(7, 7, 7)), early.drain().sections)
    }

    @Test
    fun `awaitEvents returns immediately when the revision already moved`() {
        val log = RevisionLog()
        log.recordCaptured(section)
        assertTrue(log.awaitEvents(0L, 0L))
        assertFalse(log.awaitEvents(1L, 1L))
    }

    @Test
    fun `awaitEvents wakes on a record`() {
        val log = RevisionLog()
        val waiting = CountDownLatch(1)
        var woke = false
        val waiter = thread {
            waiting.countDown()
            woke = log.awaitEvents(0L, 10_000L)
        }
        waiting.await(5, TimeUnit.SECONDS)
        log.recordCaptured(section)
        waiter.join(5_000)
        assertFalse(waiter.isAlive)
        assertTrue(woke)
    }

    @Test
    fun `awaitEvents wakes on close and reports no change`() {
        val log = RevisionLog()
        val waiting = CountDownLatch(1)
        var woke = true
        val waiter = thread {
            waiting.countDown()
            woke = log.awaitEvents(0L, 10_000L)
        }
        waiting.await(5, TimeUnit.SECONDS)
        log.close()
        waiter.join(5_000)
        assertFalse(waiter.isAlive)
        assertFalse(woke)
        assertFalse(log.awaitEvents(0L, 1_000L), "a closed log never waits")
    }
}
