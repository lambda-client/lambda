package com.lambda.pathing.world

import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import it.unimi.dsi.fastutil.longs.LongArrayList
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.withLock
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.world.World

enum class InterestTier {
    DEMAND,
    BODY,
    CORRIDOR,
}

class WorldEventBatch(
    val revision: Long,
    val sections: Set<PathingSection>,
    val chunks: Set<PathingChunk>,
    val mutations: Set<PathingSection>,
) {
    val isEmpty: Boolean get() = sections.isEmpty() && chunks.isEmpty()
}

internal fun WorldEventBatch.changedChunkSet(): Set<PathingChunk> =
    chunks + sections.mapTo(HashSet()) { PathingChunk(it.x, it.z) }

/**
 * The streaming world the planner reads: owns the [snapshot], captures sections from a
 * [CaptureSource] on the client thread by interest tier, and publishes changes through a
 * [RevisionLog]. New sections install immediately; a re-captured section waits in
 * [pendingReplacements] until the planner's next [drainEvents].
 * See docs/decisions/world-capture.md.
 */
class PathingWorld(
    val bounds: SimulationSnapshotBounds,
    private val source: CaptureSource,
) {
    constructor(
        bounds: SimulationSnapshotBounds,
        world: World,
        player: ClientPlayerEntity,
    ) : this(bounds, MinecraftCaptureSource(world, player))

    private val pendingReplacements = ConcurrentHashMap<Long, ImmutableSnapshotSection>()
    private val pendingRemovals = ConcurrentHashMap.newKeySet<Long>()

    private val revisions = RevisionLog()
    private val lock = revisions.lock
    private val interestQueue = InterestQueue(present = { key -> snapshot.hasSection(key) && key !in pendingRemovals })
    private val trusted = TrustedChunks(source)
    private val capture = SectionCapture()

    // Capture throughput, for the planning-startup ledger: written on the client
    // thread, read from the planner thread.
    @Volatile private var capturedSections = 0L
    @Volatile private var capturedCells = 0L
    @Volatile private var captureNanos = 0L

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
        while (written == 0 || System.nanoTime() < deadline) {
            val key = capture.activeKey ?: nextCapturable() ?: break
            val x = (ChunkSectionPos.unpackX(key) shl 4) + capture.localX
            val y = (ChunkSectionPos.unpackY(key) shl 4) + capture.localY
            val z = (ChunkSectionPos.unpackZ(key) shl 4) + capture.localZ
            written++
            if (capture.writeNext(source.physicsAt(x, y, z))) {
                completeSection(key)
                completed++
            }
        }
        if (written > 0) {
            capturedCells += written
            capturedSections += completed
            captureNanos += System.nanoTime() - started
        }
    }

    fun onBlockChanged(pos: BlockPos) {
        invalidateSection(ChunkSectionPos.asLong(pos.x shr 4, pos.y shr 4, pos.z shr 4), mutation = true)
    }

    fun onChunkEvent(chunkX: Int, chunkZ: Int) {
        val inChunk = LongArrayList()
        snapshot.forEachSectionKey { key ->
            if (ChunkSectionPos.unpackX(key) == chunkX && ChunkSectionPos.unpackZ(key) == chunkZ) inChunk.add(key)
        }
        if (inChunk.isEmpty) {
            revisions.signal()
            return
        }
        revisions.recordChunkChanged(PathingChunk(chunkX, chunkZ))
        val keys = inChunk.iterator()
        while (keys.hasNext()) invalidateSection(keys.nextLong(), mutation = false)
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

    fun changedSince(
        snapshotRevision: Long,
        dependedSections: Set<PathingSection>,
        dependedChunks: Set<PathingChunk>,
    ): WorldMutation? = revisions.changedSince(snapshotRevision, dependedSections, dependedChunks)

    fun close() = revisions.close()

    fun drainEvents(): WorldEventBatch {
        val removed = LongArrayList()
        for (key in pendingRemovals.toList()) {
            if (pendingRemovals.remove(key)) removed.add(key)
        }
        if (!removed.isEmpty) snapshot.removeSections(removed)
        for ((key, section) in pendingReplacements) {
            if (pendingReplacements.remove(key, section)) snapshot.install(key, section)
        }
        return revisions.drain()
    }

    fun awaitEvents(sinceRevision: Long, timeoutMillis: Long): Boolean =
        revisions.awaitEvents(sinceRevision, timeoutMillis)

    private fun demandMiss(key: Long) {
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

    private fun invalidateSection(key: Long, mutation: Boolean) {
        val present = snapshot.hasSection(key) || pendingReplacements.containsKey(key)
        if (mutation) revisions.recordMutation(sectionOf(key)) else revisions.signal()
        if (present) {
            pendingReplacements.remove(key)
            if (capture.activeKey == key) capture.abandon()
            pendingRemovals += key
            interest(listOf(key), InterestTier.DEMAND)
        }
    }

    private fun trustedKey(key: Long): Boolean =
        trusted.isTrusted(ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackZ(key))

    private fun nextCapturable(): Long? = lock.withLock {
        interestQueue.nextCapturable(::trustedKey)?.also(capture::begin)
    }

    private fun completeSection(key: Long) {
        val frozen = capture.build()
        val replacing = key in pendingRemovals
        if (replacing) {
            pendingRemovals.remove(key)
            pendingReplacements[key] = frozen
        } else {
            snapshot.install(key, frozen)
        }
        revisions.recordCaptured(sectionOf(key))
    }

    private fun sectionOf(key: Long) =
        PathingSection(ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackY(key), ChunkSectionPos.unpackZ(key))
}
