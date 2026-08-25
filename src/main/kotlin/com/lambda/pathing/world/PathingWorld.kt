package com.lambda.pathing.world

import com.lambda.util.player.prediction.ImmutableSnapshotSection
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import net.minecraft.client.MinecraftClient
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

class PathingWorld(
    val bounds: SimulationSnapshotBounds,
    private val world: World,
    private val player: ClientPlayerEntity,
) {
    private val sections = ConcurrentHashMap<Long, ImmutableSnapshotSection>()
    private val sectionCoordinates = ConcurrentHashMap<Long, Triple<Int, Int, Int>>()

    private val pendingReplacements = ConcurrentHashMap<Long, ImmutableSnapshotSection>()
    private val pendingRemovals = ConcurrentHashMap.newKeySet<Long>()

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()

    private var revisionCounter = 0L
    private var closed = false
    private val pendingSections = HashSet<PathingSection>()
    private val pendingChunks = HashSet<PathingChunk>()
    private val pendingMutations = HashSet<PathingSection>()
    private val sectionRevisions = HashMap<PathingSection, Long>()
    private val chunkRevisions = HashMap<PathingChunk, Long>()

    private val interestQueues = Array(InterestTier.entries.size) { ArrayDeque<Long>() }
    private val interested = HashSet<Long>()

    private val deferred = LinkedHashSet<Long>()

    private val mutablePos = BlockPos.Mutable()
    private val shapeContext = net.minecraft.block.ShapeContext.of(player)

    @Volatile private var trustedChunks: Set<Long> = emptySet()
    private var trustedRefreshTick = 0

    private var activeKey: Long? = null
    private var builder = ImmutableSnapshotSection.Builder()
    private var cursorX = 0
    private var cursorY = 0
    private var cursorZ = 0

    val snapshot: SnapshotSimulationEnvironment = SnapshotSimulationEnvironment(
        bounds = bounds,
        sections = sections,
        defaultBlock = null,
        shareSections = true,
        missingSection = null,
        sparseSectionCoordinates = sectionCoordinates,
        unavailableSectionKeys = null,
        sectionUnavailable = { key -> !sections.containsKey(key) },
        onExactMiss = ::demandMiss,
    )

    val revision: Long get() = lock.withLock { revisionCounter }

    val pendingInterest: Int
        get() = lock.withLock {
            interestQueues.sumOf { it.size } + (if (activeKey != null) 1 else 0)
        }

    val pendingDemand: Int
        get() = lock.withLock { interestQueues[InterestTier.DEMAND.ordinal].size }

    fun chunkCapturable(chunkX: Int, chunkZ: Int): Boolean =
        net.minecraft.util.math.ChunkPos.toLong(chunkX, chunkZ) in trustedChunks

    private fun refreshTrustedChunks() {
        val center = player.chunkPos
        val view = MinecraftClient.getInstance().options.clampedViewDistance
        val fresh = HashSet<Long>()
        for (cx in (center.x - view - 2)..(center.x + view + 2)) {
            for (cz in (center.z - view - 2)..(center.z + view + 2)) {
                if (isTrustedLoadedChunk(cx, cz)) fresh += net.minecraft.util.math.ChunkPos.toLong(cx, cz)
            }
        }
        trustedChunks = fresh
    }

    fun advance(budgetMillis: Double) {
        check(MinecraftClient.getInstance().isOnThread) {
            "PathingWorld capture must advance on the client thread"
        }
        val deadline = System.nanoTime() + (budgetMillis * 1_000_000.0).toLong()
        if (trustedRefreshTick++ % TRUSTED_REFRESH_TICKS == 0) refreshTrustedChunks()
        promoteDeferred()
        var written = 0
        while (written == 0 || System.nanoTime() < deadline) {
            val key = activeKey ?: nextCapturable() ?: break
            val baseX = ChunkSectionPos.unpackX(key) shl 4
            val baseY = ChunkSectionPos.unpackY(key) shl 4
            val baseZ = ChunkSectionPos.unpackZ(key) shl 4
            val pos = mutablePos.set(baseX + cursorX, baseY + cursorY, baseZ + cursorZ)
            val physics = with(SnapshotSimulationEnvironment) {
                world.getBlockState(pos).capturePhysics(world, pos, shapeContext)
            }
            builder.set(cursorX, cursorY, cursorZ, physics)
            written++
            if (advanceCursor()) completeSection(key)
        }
    }

    fun onBlockChanged(pos: BlockPos) {
        val key = ChunkSectionPos.asLong(pos.x shr 4, pos.y shr 4, pos.z shr 4)
        invalidateSection(key, mutation = true)
    }

    fun onChunkEvent(chunkX: Int, chunkZ: Int) {
        val hadContent = sectionCoordinates.values.any { it.first == chunkX && it.third == chunkZ }
        lock.withLock {
            if (hadContent) {
                val next = ++revisionCounter
                chunkRevisions[PathingChunk(chunkX, chunkZ)] = next
                pendingChunks += PathingChunk(chunkX, chunkZ)
            }
            changed.signalAll()
        }
        if (hadContent) {
            sectionCoordinates.entries
                .filter { (_, coordinate) -> coordinate.first == chunkX && coordinate.third == chunkZ }
                .forEach { (key, _) -> invalidateSection(key, mutation = false) }
        }

    }

    fun interest(sectionKeys: Iterable<Long>, tier: InterestTier) {
        lock.withLock {
            val queue = interestQueues[tier.ordinal]
            for (key in sectionKeys) {
                if (sections.containsKey(key) && key !in pendingRemovals) continue
                if (!interested.add(key)) continue
                queue.addLast(key)
            }
        }
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

    fun close() {
        lock.withLock {
            closed = true
            changed.signalAll()
        }
    }

    fun drainEvents(): WorldEventBatch {
        for (key in pendingRemovals.toList()) {
            if (pendingRemovals.remove(key)) {
                sections.remove(key)
                sectionCoordinates.remove(key)
            }
        }
        for ((key, section) in pendingReplacements) {
            if (pendingReplacements.remove(key, section)) {
                sections[key] = section
                sectionCoordinates[key] = Triple(
                    ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackY(key), ChunkSectionPos.unpackZ(key),
                )
            }
        }
        return lock.withLock {
            val batch = WorldEventBatch(
                revisionCounter, HashSet(pendingSections), HashSet(pendingChunks), HashSet(pendingMutations),
            )
            pendingSections.clear()
            pendingChunks.clear()
            pendingMutations.clear()
            batch
        }
    }

    fun awaitEvents(sinceRevision: Long, timeoutMillis: Long): Boolean = lock.withLock {
        var remaining = timeoutMillis * 1_000_000L
        while (!closed && revisionCounter <= sinceRevision && remaining > 0) {
            remaining = changed.awaitNanos(remaining)
        }
        revisionCounter > sinceRevision
    }

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
        val present = sections.containsKey(key) || pendingReplacements.containsKey(key)
        lock.withLock {
            if (mutation) {
                val next = ++revisionCounter
                val section = PathingSection(
                    ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackY(key), ChunkSectionPos.unpackZ(key),
                )
                sectionRevisions[section] = next
                pendingSections += section
                pendingMutations += section
            }
            changed.signalAll()
        }
        if (present) {
            pendingReplacements.remove(key)
            if (activeKey == key) {
                activeKey = null
                builder = ImmutableSnapshotSection.Builder()
            }

            pendingRemovals += key
            interest(listOf(key), InterestTier.DEMAND)
        }
    }

    private fun promoteDeferred() {
        if (deferred.isEmpty()) return
        val promoted = deferred.filter { key ->
            isTrustedLoadedChunk(ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackZ(key))
        }
        promoted.forEach { key ->
            deferred.remove(key)
            lock.withLock {
                if (interested.add(key)) interestQueues[InterestTier.DEMAND.ordinal].addLast(key)
            }
        }
    }

    private fun nextCapturable(): Long? {
        lock.withLock {
            for (queue in interestQueues) {
                while (queue.isNotEmpty()) {
                    val key = queue.removeFirst()
                    interested.remove(key)
                    if (sections.containsKey(key) && key !in pendingRemovals) continue
                    if (!isTrustedLoadedChunk(ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackZ(key))) {
                        deferred += key
                        continue
                    }
                    activeKey = key
                    builder = ImmutableSnapshotSection.Builder()
                    cursorX = 0
                    cursorY = 0
                    cursorZ = 0
                    return key
                }
            }
        }
        return null
    }

    private fun advanceCursor(): Boolean {
        if (cursorX < 15) {
            cursorX++
            return false
        }
        cursorX = 0
        if (cursorZ < 15) {
            cursorZ++
            return false
        }
        cursorZ = 0
        if (cursorY < 15) {
            cursorY++
            return false
        }
        return true
    }

    private fun completeSection(key: Long) {
        val frozen = builder.build(expectedWrites = SECTION_CELLS)
        activeKey = null
        builder = ImmutableSnapshotSection.Builder()
        val replacing = key in pendingRemovals
        if (replacing) {

            pendingRemovals.remove(key)
            pendingReplacements[key] = frozen
        } else {
            sections[key] = frozen
            sectionCoordinates[key] = Triple(
                ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackY(key), ChunkSectionPos.unpackZ(key),
            )
        }
        lock.withLock {
            revisionCounter++
            pendingSections += PathingSection(
                ChunkSectionPos.unpackX(key), ChunkSectionPos.unpackY(key), ChunkSectionPos.unpackZ(key),
            )
            changed.signalAll()
        }
    }

    private fun isTrustedLoadedChunk(chunkX: Int, chunkZ: Int): Boolean {
        if (!world.chunkManager.isChunkLoaded(chunkX, chunkZ)) return false
        val center = player.chunkPos
        val viewDistance = MinecraftClient.getInstance().options.clampedViewDistance
        val dx = maxOf(0, Math.abs(chunkX - center.x) - CHUNK_FILTER_EDGE_MARGIN).toLong()
        val dz = maxOf(0, Math.abs(chunkZ - center.z) - CHUNK_FILTER_EDGE_MARGIN).toLong()
        return dx * dx + dz * dz < viewDistance.toLong() * viewDistance
    }

    private companion object {
        const val SECTION_CELLS = 16 * 16 * 16
        const val CHUNK_FILTER_EDGE_MARGIN = 2
        const val TRUSTED_REFRESH_TICKS = 4
    }
}
