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
import com.lambda.pathing.world.RevisionLog
import com.lambda.pathing.world.WorldMutation
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RevisionLogTest {
    private val section = PathingSection(1, 4, 2)
    private val chunk = PathingChunk(1, 2)

    @Test
    fun `every record bumps the revision by one and signal does not`() {
        val log = RevisionLog()
        assertEquals(0L, log.revision)
        log.recordCaptured(section)
        assertEquals(1L, log.revision)
        log.recordMutation(section)
        assertEquals(2L, log.revision)
        log.recordChunkChanged(chunk)
        assertEquals(3L, log.revision)
        log.signal()
        assertEquals(3L, log.revision)
    }

    @Test
    fun `changedSince reports the depended section or chunk that moved`() {
        val log = RevisionLog()
        log.recordCaptured(PathingSection(9, 9, 9))
        assertNull(log.changedSince(0L, setOf(section), setOf(chunk)), "captures are not mutations")

        log.recordMutation(section)
        assertEquals(WorldMutation.Section(section, 2L), log.changedSince(1L, setOf(section), setOf(chunk)))
        assertNull(log.changedSince(2L, setOf(section), setOf(chunk)))
        assertNull(log.changedSince(1L, setOf(PathingSection(0, 0, 0)), emptySet()), "undepended section")

        log.recordChunkChanged(chunk)
        assertEquals(WorldMutation.Chunk(chunk, 3L), log.changedSince(2L, emptySet(), setOf(chunk)))
        assertEquals(
            WorldMutation.Section(section, 2L),
            log.changedSince(1L, setOf(section), setOf(chunk)),
            "sections are checked before chunks",
        )
    }

    @Test
    fun `drain hands over pending changes once`() {
        val log = RevisionLog()
        log.recordCaptured(section)
        log.recordMutation(PathingSection(3, 3, 3))
        log.recordChunkChanged(chunk)
        val batch = log.drain()
        assertEquals(3L, batch.revision)
        assertEquals(setOf(section, PathingSection(3, 3, 3)), batch.sections)
        assertEquals(setOf(PathingSection(3, 3, 3)), batch.mutations)
        assertEquals(setOf(chunk), batch.chunks)
        assertTrue(log.drain().isEmpty)
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
