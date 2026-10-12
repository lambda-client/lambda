package com.lambda.pathing.world.streaming

import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PathingWorld(
	val bounds: SimulationSnapshotBounds,
	private val source: CaptureSource,
	private val recorder: SectionRecorder? = null,
) {
	private val pendingReplacements = ConcurrentHashMap<Long, ImmutableSnapshotSection>()
	private val pendingRemovals = ConcurrentHashMap.newKeySet<Long>()

	private val lock = ReentrantLock()
	private val revisions = RevisionLog(lock)
	private val interestQueue = InterestQueue(present = { key -> snapshot.hasSection(key) && key !in pendingRemovals })
	private val trusted = TrustedChunks(source)
	private val capture = SectionCapture()

	@Volatile
	private var capturedSections = 0L
	@Volatile
	private var capturedCells = 0L
	@Volatile
	private var captureNanos = 0L

	private val reloads = AtomicLong()
	private val dropped = AtomicLong()
	private val misses = AtomicLong()

	val sectionsCaptured: Long get() = capturedSections

	val chunkReloads: Long get() = reloads.get()

	val sectionsDropped: Long get() = dropped.get()

	val exactMisses: Long get() = misses.get()

	val awaitingLoad: Int
		get() = lock.withLock { interestQueue.deferredSize }

	fun captureLedger(): String =
		"%d sections/%d cells in %d ms".format(capturedSections, capturedCells, captureNanos / 1_000_000L)

	val snapshot: SnapshotSimulationEnvironment = SnapshotSimulationEnvironment.streaming(bounds, ::demandMiss)

	val revision: Long get() = revisions.revision

	val pendingInterest: Int
		get() = lock.withLock { interestQueue.size + (if (capture.activeKey != null) 1 else 0) }

	val pendingDemand: Int
		get() = lock.withLock { interestQueue.demandSize }

	fun chunkCapturable(chunkX: Int, chunkZ: Int): Boolean = trusted.contains(chunkX, chunkZ)

	fun advance(budgetMillis: Double) {
		check(source.isOnClientThread()) { "PathingWorld capture must advance on the client thread" }
		val started = System.nanoTime()
		val deadline = started + (budgetMillis * 1_000_000.0).toLong()
		trusted.tick()
		lock.withLock { interestQueue.promoteDeferred(::trustedKey) }
		var written = 0
		var completed = 0
		val finished = Long2ObjectLinkedOpenHashMap<ImmutableSnapshotSection>()
		while (written == 0 || System.nanoTime() < deadline) {
			val key = capture.activeKey ?: nextCapturable() ?: break
			val x = (ChunkSectionPos.unpackX(key) shl 4) + capture.localX
			val y = (ChunkSectionPos.unpackY(key) shl 4) + capture.localY
			val z = (ChunkSectionPos.unpackZ(key) shl 4) + capture.localZ
			written++
			if (capture.writeNext(source.physicsAt(x, y, z))) {
				finished.put(key, capture.build())
				completed++
			}
		}
		if (!finished.isEmpty()) completeSections(finished)
		if (written > 0) {
			capturedCells += written
			capturedSections += completed
			captureNanos += System.nanoTime() - started
		}
	}

	fun onBlockChanged(pos: BlockPos) {
		resample(pos.x, pos.y, pos.z)
		resampleWater(pos.x, pos.y - 1, pos.z)
		for (dy in -1..1) {
			resampleWater(pos.x + 1, pos.y + dy, pos.z)
			resampleWater(pos.x - 1, pos.y + dy, pos.z)
			resampleWater(pos.x, pos.y + dy, pos.z + 1)
			resampleWater(pos.x, pos.y + dy, pos.z - 1)
		}
	}

	private fun resample(x: Int, y: Int, z: Int) {
		val key = ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
		val capturing = capture.activeKey == key
		val recorded = recordedSection(key)
		if (!capturing && recorded == null) return
		val physics = source.physicsAt(x, y, z)
		if (capturing) capture.patch(x, y, z, physics)
		if (recorded == null || recorded[x, y, z] == physics) return
		pendingReplacements[key] = recorded.with(x, y, z, physics)
		recorder?.record(key)
		revisions.recordMutation(VoxelPos(x, y, z))
	}

	private fun resampleWater(x: Int, y: Int, z: Int) {
		val key = ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
		if (capture.activeKey == key || recordedSection(key)?.get(x, y, z)?.water != null) resample(x, y, z)
	}

	private fun recordedSection(key: Long): ImmutableSnapshotSection? =
		if (key in pendingRemovals) null else pendingReplacements[key] ?: snapshot.section(key)

	fun onChunkEvent(chunkX: Int, chunkZ: Int) {
		val inChunk = LongArrayList()
		snapshot.forEachSectionKey { key ->
			if (ChunkSectionPos.unpackX(key) == chunkX && ChunkSectionPos.unpackZ(key) == chunkZ) inChunk.add(key)
		}
		if (inChunk.isEmpty) {
			revisions.signal()
			return
		}
		reloads.incrementAndGet()
		revisions.recordChunkChanged(PathingChunk(chunkX, chunkZ))
		val keys = inChunk.iterator()
		while (keys.hasNext()) dropSection(keys.nextLong())
	}

	fun interest(sectionKeys: Iterable<Long>, tier: InterestTier) {
		lock.withLock { interestQueue.add(sectionKeys, tier) }
	}

	fun interestBlocks(minX: Int, minY: Int, minZ: Int, maxX: Int, maxY: Int, maxZ: Int, tier: InterestTier) {
		val loY = maxOf(minY, bounds.minY) shr 4
		val hiY = minOf(maxY, bounds.maxY) shr 4
		val keys = ArrayList<Long>()
		for (sx in (minX shr 4)..(maxX shr 4)) {
			for (sz in (minZ shr 4)..(maxZ shr 4)) {
				for (sy in loY..hiY) keys += ChunkSectionPos.asLong(sx, sy, sz)
			}
		}
		interest(keys, tier)
	}

	fun mutationsSince(snapshotRevision: Long): List<WorldMutation> = revisions.mutationsSince(snapshotRevision)

	fun close() = revisions.close()

	fun subscribe(): WorldEvents = WorldEvents(revisions.subscribe())

	inner class WorldEvents internal constructor(private val subscription: RevisionLog.Subscription) : AutoCloseable {
		fun drain(): WorldEventBatch {
			applyPending()
			return subscription.drain()
		}

		override fun close() = subscription.close()
	}

	private fun applyPending() {
		val removed = LongArrayList()
		for (key in pendingRemovals.toList()) {
			if (pendingRemovals.remove(key)) removed.add(key)
		}
		if (!removed.isEmpty) snapshot.removeSections(removed)
		val replaced = Long2ObjectOpenHashMap<ImmutableSnapshotSection>()
		for ((key, section) in pendingReplacements) {
			if (pendingReplacements.remove(key, section)) replaced.put(key, section)
		}
		if (!replaced.isEmpty()) snapshot.install(replaced)
	}

	fun awaitEvents(sinceRevision: Long, timeoutMillis: Long): Boolean =
		revisions.awaitEvents(sinceRevision, timeoutMillis)

	private fun demandMiss(key: Long) {
		misses.incrementAndGet()
		val sx = ChunkSectionPos.unpackX(key)
		val sy = ChunkSectionPos.unpackY(key)
		val sz = ChunkSectionPos.unpackZ(key)
		val keys = ArrayList<Long>(27)
		for (dy in -1..1) for (dz in -1..1) for (dx in -1..1) {
			val y = sy + dy
			if ((y shl 4) + 15 < bounds.minY || (y shl 4) > bounds.maxY) continue
			keys += ChunkSectionPos.asLong(sx + dx, y, sz + dz)
		}
		interest(keys, InterestTier.DEMAND)
	}

	private fun dropSection(key: Long) {
		dropped.incrementAndGet()
		pendingReplacements.remove(key)
		if (capture.activeKey == key) capture.abandon()
		pendingRemovals += key
		interest(listOf(key), InterestTier.DEMAND)
	}

	private fun trustedKey(key: Long): Boolean =
		trusted.isTrusted(ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackZ(key))

	private fun nextCapturable(): Long? = lock.withLock {
		interestQueue.nextCapturable(::trustedKey)?.also(capture::begin)
	}

	private fun completeSections(finished: Long2ObjectLinkedOpenHashMap<ImmutableSnapshotSection>) {
		val installed = Long2ObjectOpenHashMap<ImmutableSnapshotSection>(finished.size)
		for (entry in finished.long2ObjectEntrySet()) {
			if (pendingRemovals.remove(entry.longKey)) pendingReplacements[entry.longKey] = entry.value else installed.put(entry.longKey, entry.value)
		}
		if (!installed.isEmpty()) snapshot.install(installed)
		for (key in finished.keys) {
			recorder?.record(key)
			revisions.recordCaptured(sectionOf(key))
		}
	}

	private fun sectionOf(key: Long) =
		PathingSection(ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackY(key), ChunkSectionPos.unpackZ(key))
}
