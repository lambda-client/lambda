package com.lambda.pathing.world

import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.PathingSection
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * The world's revision counter and the per-section / per-chunk change record behind it.
 * Writers (capture, block and chunk events) run on the client thread; [changedSince],
 * [awaitEvents] and [drain] on the planner thread. All state is guarded by [lock].
 */
class RevisionLog(val lock: ReentrantLock = ReentrantLock()) {
    private val changed = lock.newCondition()

    private var revisionCounter = 0L
    private var closed = false
    private val pendingSections = HashSet<PathingSection>()
    private val pendingChunks = HashSet<PathingChunk>()
    private val pendingMutations = HashSet<PathingSection>()
    private val sectionRevisions = HashMap<PathingSection, Long>()
    private val chunkRevisions = HashMap<PathingChunk, Long>()

    val revision: Long get() = lock.withLock { revisionCounter }

    /** A section was captured (or re-captured): bumps the revision without recording a mutation. */
    fun recordCaptured(section: PathingSection) = lock.withLock {
        revisionCounter++
        pendingSections += section
        changed.signalAll()
    }

    /** A block inside [section] changed. */
    fun recordMutation(section: PathingSection) = lock.withLock {
        val next = ++revisionCounter
        sectionRevisions[section] = next
        pendingSections += section
        pendingMutations += section
        changed.signalAll()
    }

    /** A chunk with captured content was (re)loaded or unloaded. */
    fun recordChunkChanged(chunk: PathingChunk) = lock.withLock {
        val next = ++revisionCounter
        chunkRevisions[chunk] = next
        pendingChunks += chunk
        changed.signalAll()
    }

    /** Wakes waiters without changing the revision. */
    fun signal() = lock.withLock { changed.signalAll() }

    fun changedSince(
        snapshotRevision: Long,
        dependedSections: Set<PathingSection>,
        dependedChunks: Set<PathingChunk>,
    ): WorldMutation? = lock.withLock {
        if (revisionCounter <= snapshotRevision) return null
        dependedSections.forEach { section ->
            val at = sectionRevisions[section] ?: return@forEach
            if (at > snapshotRevision) return WorldMutation.Section(section, at)
        }
        dependedChunks.forEach { chunk ->
            val at = chunkRevisions[chunk] ?: return@forEach
            if (at > snapshotRevision) return WorldMutation.Chunk(chunk, at)
        }
        null
    }

    fun awaitEvents(sinceRevision: Long, timeoutMillis: Long): Boolean = lock.withLock {
        var remaining = timeoutMillis * 1_000_000L
        while (!closed && revisionCounter <= sinceRevision && remaining > 0) {
            remaining = changed.awaitNanos(remaining)
        }
        revisionCounter > sinceRevision
    }

    fun close() = lock.withLock {
        closed = true
        changed.signalAll()
    }

    /** Hands the accumulated changes to the planner and clears them. */
    fun drain(): WorldEventBatch = lock.withLock {
        val batch = WorldEventBatch(
            revisionCounter, HashSet(pendingSections), HashSet(pendingChunks), HashSet(pendingMutations),
        )
        pendingSections.clear()
        pendingChunks.clear()
        pendingMutations.clear()
        batch
    }
}
