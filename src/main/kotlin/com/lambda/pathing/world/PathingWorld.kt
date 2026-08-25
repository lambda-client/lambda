/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

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

/** How urgently a declared region of interest should be captured. */
enum class InterestTier {
    /** A rollout or repair is blocked on it right now. */
    DEMAND,

    /** The ring around the body; the search roots here. */
    BODY,

    /** Route corridor and goal neighborhood; the search is heading there. */
    CORRIDOR,
}

/** One drained batch of world knowledge changes, coalesced since the previous drain. */
class WorldEventBatch(
    val revision: Long,
    val sections: Set<PathingSection>,
    val chunks: Set<PathingChunk>,
    /** The subset of [sections] (plus chunk refreshes) that were world MUTATIONS, not captures. */
    val mutations: Set<PathingSection>,
) {
    val isEmpty: Boolean get() = sections.isEmpty() && chunks.isEmpty()
}

/**
 * The single source of planner-visible world knowledge.
 *
 * One immutable-section snapshot, one monotonic revision, one coalesced pending-set of
 * changes, one waiting mechanism. Knowledge is *pulled* by interest declarations and
 * captured on the client tick within a budget; nothing the planner does ever blocks on
 * the client thread, and nothing the client does ever swaps a section a rollout might
 * be mid-read on -- replacements of already-readable sections are staged and applied by
 * the worker between rollouts.
 *
 * Unknown terrain is simply *absent*: no placeholder sections, no parallel state sets.
 * A section is either present (authoritative) or missing (unknown). Everything that
 * used to be five coordinated section-state sets is now the event stream.
 */
class PathingWorld(
    val bounds: SimulationSnapshotBounds,
    private val world: World,
    private val player: ClientPlayerEntity,
) {
    private val sections = ConcurrentHashMap<Long, ImmutableSnapshotSection>()
    private val sectionCoordinates = ConcurrentHashMap<Long, Triple<Int, Int, Int>>()

    /** Replacements of already-readable sections, applied by the worker at drain. */
    private val pendingReplacements = ConcurrentHashMap<Long, ImmutableSnapshotSection>()
    private val pendingRemovals = ConcurrentHashMap.newKeySet<Long>()

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()

    // All guarded by [lock].
    private var revisionCounter = 0L
    private var closed = false
    private val pendingSections = HashSet<PathingSection>()
    private val pendingChunks = HashSet<PathingChunk>()
    private val pendingMutations = HashSet<PathingSection>()
    private val sectionRevisions = HashMap<PathingSection, Long>()
    private val chunkRevisions = HashMap<PathingChunk, Long>()

    // Interest: three FIFO tiers plus a dedupe set. Client thread consumes; both
    // threads may enqueue (worker via exact-miss demand), so access is under [lock].
    private val interestQueues = Array(InterestTier.entries.size) { ArrayDeque<Long>() }
    private val interested = HashSet<Long>()

    /** Sections wanted but sitting in chunks the client cannot trust yet. */
    private val deferred = LinkedHashSet<Long>()

    private val mutablePos = BlockPos.Mutable()
    private val shapeContext = net.minecraft.block.ShapeContext.of(player)

    /** Client-tick snapshot of trusted loaded chunk keys; the worker reads it only. */
    @Volatile private var trustedChunks: Set<Long> = emptySet()
    private var trustedRefreshTick = 0

    // Capture cursor for the section currently being copied.
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

    /**
     * Capturable interest not yet fulfilled. Deferred sections (untrusted chunks) do
     * not count: they cannot be captured until the world streams them, and waiting on
     * them would stall every walk toward unloaded terrain.
     */
    val pendingInterest: Int
        get() = lock.withLock {
            interestQueues.sumOf { it.size } + (if (activeKey != null) 1 else 0)
        }

    /**
     * Urgent capture still in flight: something a search read (or a re-capture after
     * invalidation) that has not landed yet. This -- not [pendingInterest] -- is what
     * readiness waits must watch: the BODY/CORRIDOR tiers stream ahead of a moving
     * body and never drain, so waiting on them stalls every leg on a live server.
     */
    val pendingDemand: Int
        get() = lock.withLock { interestQueues[InterestTier.DEMAND.ordinal].size }

    /**
     * Whether a chunk could be captured right now if asked (loaded and trusted).
     * Answered from a client-tick snapshot set: the worker must never touch the live
     * chunk array -- a racy read here classifies capturable terrain as unstreamed and
     * silently strips the search of its hazard lessons.
     */
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

    // ------------------------------------------------------------------ client thread

    /**
     * Captures declared interest within [budgetMillis] and publishes change events.
     *
     * At least one cell always advances so a pathological budget cannot starve capture.
     */
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

    /** A block changed inside authoritative terrain. */
    fun onBlockChanged(pos: BlockPos) {
        val key = ChunkSectionPos.asLong(pos.x shr 4, pos.y shr 4, pos.z shr 4)
        invalidateSection(key, mutation = true)
    }

    /** A chunk was (re)sent by the server. */
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
        // A freshly arrived chunk may hold sections that were deferred as untrusted;
        // the next advance() promotes them.
    }

    // ------------------------------------------------------------------ any thread

    /** Declares that knowledge of [sectionKeys] is wanted at [tier] urgency. */
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

    /** Interest over a block-space box, clamped to bounds, section-quantized. */
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

    /** The executor's certified-tape gate: did anything a frame depends on mutate? */
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

    // ------------------------------------------------------------------ worker thread

    /**
     * Applies staged replacements and returns everything that changed since the last
     * drain. Section swaps happen here -- between rollouts -- so a frame can never read
     * half-old, half-new content.
     */
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

    /**
     * Blocks the worker until knowledge changes past [sinceRevision], [timeoutMillis]
     * elapses, or the world closes. The one blocking call in the pathing world; it
     * waits on *events*, never on a particular capture.
     */
    fun awaitEvents(sinceRevision: Long, timeoutMillis: Long): Boolean = lock.withLock {
        var remaining = timeoutMillis * 1_000_000L
        while (!closed && revisionCounter <= sinceRevision && remaining > 0) {
            remaining = changed.awaitNanos(remaining)
        }
        revisionCounter > sinceRevision
    }

    // ------------------------------------------------------------------ internals

    /** Exact simulation read hit unknown terrain: queue it (and its halo) urgently. */
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
            // Stage removal and re-capture: the worker drops the stale content at its
            // next drain; the fresh copy arrives through the ordinary capture path.
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

    /** True after the section's last cell. */
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
            // The old content is still readable; the worker swaps at its next drain.
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

    /**
     * @see net.minecraft.client.world.ClientChunkManager
     * Vanilla keeps a cache margin of chunks past the server's watched area which may
     * contain only void air; only chunks inside the watch filter are authoritative.
     */
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
