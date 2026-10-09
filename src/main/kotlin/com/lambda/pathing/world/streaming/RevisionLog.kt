package com.lambda.pathing.world.streaming

import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.VoxelPos
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class RevisionLog(private val lock: ReentrantLock = ReentrantLock()) {
	private val changed = lock.newCondition()

	private var revisionCounter = 0L
	private var closed = false
	private val subscribers = ArrayList<Pending>()
	private val cellMutations = ArrayList<WorldMutation.Cell>()
	private val chunkRevisions = HashMap<PathingChunk, Long>()

	private class Pending {
		val sections = HashSet<PathingSection>()
		val chunks = HashSet<PathingChunk>()
		val mutations = HashSet<VoxelPos>()
	}

	inner class Subscription internal constructor() {
		private val pending = Pending().also { subscribers += it }

		fun drain(): WorldEventBatch = lock.withLock {
			val batch = WorldEventBatch(revisionCounter, HashSet(pending.sections), HashSet(pending.chunks), HashSet(pending.mutations))
			pending.sections.clear()
			pending.chunks.clear()
			pending.mutations.clear()
			batch
		}

		fun close() = lock.withLock { subscribers -= pending }
	}

	val revision: Long get() = lock.withLock { revisionCounter }

	fun subscribe(): Subscription = lock.withLock { Subscription() }

	fun recordCaptured(section: PathingSection) = lock.withLock {
		revisionCounter++
		for (pending in subscribers) pending.sections += section
		changed.signalAll()
	}

	fun recordMutation(cell: VoxelPos) = lock.withLock {
		cellMutations += WorldMutation.Cell(cell, ++revisionCounter)
		for (pending in subscribers) pending.mutations += cell
		changed.signalAll()
	}

	fun recordChunkChanged(chunk: PathingChunk) = lock.withLock {
		val next = ++revisionCounter
		chunkRevisions[chunk] = next
		for (pending in subscribers) pending.chunks += chunk
		changed.signalAll()
	}

	fun signal() = lock.withLock { changed.signalAll() }

	fun mutationsSince(snapshotRevision: Long): List<WorldMutation> = lock.withLock {
		if (revisionCounter <= snapshotRevision) return emptyList()
		val since = ArrayList<WorldMutation>()
		for (index in cellMutations.indices.reversed()) {
			val mutation = cellMutations[index]
			if (mutation.revision <= snapshotRevision) break
			since += mutation
		}
		for ((chunk, at) in chunkRevisions) if (at > snapshotRevision) since += WorldMutation.Chunk(chunk, at)
		since
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
}

class WorldEventBatch(
	val revision: Long,
	val sections: Set<PathingSection>,
	val chunks: Set<PathingChunk>,
	val mutations: Set<VoxelPos>,
) {
	val isEmpty: Boolean get() = sections.isEmpty() && chunks.isEmpty() && mutations.isEmpty()

	val changed: List<CellBox> by lazy {
		chunks.map(CellBox::column) + mutations.filter { PathingChunk.containing(it) !in chunks }
			.groupBy(PathingSection::containing).values.map(CellBox::bounding)
	}

	val arrived: List<CellBox> by lazy {
		sections.filter { PathingChunk(it.x, it.z) !in chunks }.groupBy { PathingChunk(it.x, it.z) }
			.map { (chunk, arrivals) -> CellBox.sections(chunk, arrivals.minOf(PathingSection::y), arrivals.maxOf(PathingSection::y)) }
	}
}
