/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.worldview

import com.lambda.Lambda.LOG
import com.lambda.Lambda.mc
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.world.chunk.ChunkStatus
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Copy-on-read snapshot of the observed world, one planning session's base
 * view. Sections are copied to flat int arrays the first time the planner
 * touches them; observed block changes are written through by the owner so
 * the snapshot tracks the world exactly as long as every [WorldEvent] is
 * forwarded.
 *
 * Threading contract (WP1, hardened after the July 2026 async revert):
 * - Reads ([stateId]) are legal on any thread.
 * - **Chunk palettes are only ever decoded on the client thread.** Vanilla
 *   palette storage is not safe for concurrent reads (resizes swap the
 *   backing array), and torn section copies were a root cause of the
 *   field-unstable async attempt. A worker read of an uncopied section
 *   faults the copy onto the client thread ([mc]'s task queue services it
 *   between frames, pause screen included) and blocks the planner worker —
 *   never the game — until it lands. [prefetch] keeps such faults rare.
 * - Write-through ([applyObserved]) and eviction run on the client thread;
 *   overrides live in concurrent maps whose publication makes them visible
 *   to worker reads immediately.
 */
class SnapshotWorldView(private val world: ClientWorld) : WorldView {
    private val sections = ConcurrentHashMap<Long, IntArray>()

    /**
     * Observed block changes, kept apart from the immutable copied arrays so
     * a client-thread write-through is race-free against worker reads of the
     * same section. Consulted before the section array on every read.
     */
    private val observedOverrides = ConcurrentHashMap<Long, ConcurrentHashMap<Int, Int>>()

    /** Most planner reads stay in one 16³ section; bypass a CHM lookup on those reads. */
    private class SectionCursor {
        var generation = -1
        var key = Long.MIN_VALUE
        var section: IntArray? = null
    }

    private val sectionGeneration = AtomicInteger()
    private val sectionCursor = ThreadLocal.withInitial(::SectionCursor)

    @Volatile private var faultTimeouts = 0

    override fun stateId(x: Int, y: Int, z: Int): Int {
        if (world.isOutOfHeightLimit(y)) return BlockTraitRegistry.AIR_ID
        val key = ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
        val index = localIndex(x, y, z)
        // The override map is empty for virtually every planning session.
        // Avoid hashing the section key until a mutation has actually landed.
        if (observedOverrides.isNotEmpty()) observedOverrides[key]?.get(index)?.let { return it }
        val generation = sectionGeneration.get()
        val cursor = sectionCursor.get()
        val section = if (cursor.generation == generation && cursor.key == key) {
            cursor.section!!
        } else {
            (sections[key] ?: fetchSection(key, x shr 4, y shr 4, z shr 4)).also {
                cursor.generation = generation
                cursor.key = key
                cursor.section = it
            }
        }
        if (section === AIR_SECTION) return BlockTraitRegistry.AIR_ID
        // Unknown is deliberately conservative. Treating an unloaded chunk
        // as air lets the planner route through terrain it has never observed.
        if (section === UNKNOWN_SECTION) return UNKNOWN_STATE_ID
        return section[index]
    }

    private fun fetchSection(key: Long, sx: Int, sy: Int, sz: Int): IntArray {
        if (mc.isOnThread) {
            return sections.computeIfAbsent(key) { copySection(sx, sy, sz) }
        }

        // Planner-worker miss: copy the whole column in one client-thread
        // hop — the search wavefront that touched this section will touch
        // its vertical neighbours next, and each hop costs a frame of
        // worker latency regardless of how many sections it copies.
        val future = CompletableFuture.supplyAsync({
            copyColumn(sx, sz)
            sections[key] ?: UNKNOWN_SECTION
        }, mc)
        return try {
            future.get(SECTION_FAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            // Uncached: the next read retries, and the still-running task
            // will have materialized the copy by then. Conservative unknown
            // is the same answer an unloaded chunk gives.
            if (++faultTimeouts <= FAULT_TIMEOUT_LOG_LIMIT) {
                LOG.warn("[SnapshotWorldView] Section fault timed out at ($sx,$sy,$sz); planner reads unknown this pass")
            }
            UNKNOWN_SECTION
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            UNKNOWN_SECTION
        } catch (e: Exception) {
            LOG.error("[SnapshotWorldView] Section fault failed at ($sx,$sy,$sz)", e)
            UNKNOWN_SECTION
        }
    }

    /** Client thread only: snapshot every section of one chunk column. */
    private fun copyColumn(sx: Int, sz: Int) {
        val bottom = world.bottomSectionCoord
        for (sy in bottom until bottom + world.countVerticalSections()) {
            sections.computeIfAbsent(ChunkSectionPos.asLong(sx, sy, sz)) { copySection(sx, sy, sz) }
        }
    }

    /**
     * Client thread only, called once per traversal request: snapshot the
     * start→goal chunk corridor up front so the planner worker's initial
     * search rarely faults. Bounded by [MAX_PREFETCH_SECTIONS]; terrain
     * beyond the cap (very long goals) is served by per-column fault-ins.
     * Returns the number of sections copied.
     */
    fun prefetch(
        startX: Int, startY: Int, startZ: Int,
        goalX: Int, goalY: Int, goalZ: Int,
    ): Int {
        check(mc.isOnThread) { "prefetch is a client-thread operation" }
        val startedAt = System.nanoTime()
        val fromCx = startX shr 4
        val fromCz = startZ shr 4
        val toCx = goalX shr 4
        val toCz = goalZ shr 4

        val minSy = ((minOf(startY, goalY) - PREFETCH_MARGIN_Y) shr 4)
            .coerceAtLeast(world.bottomSectionCoord)
        val maxSy = ((maxOf(startY, goalY) + PREFETCH_MARGIN_Y) shr 4)
            .coerceAtMost(world.bottomSectionCoord + world.countVerticalSections() - 1)

        val before = sections.size
        // Walk the corridor from the start outward: if the cap cuts the pass
        // short, the retained sections are the ones the search needs first.
        val steps = maxOf(kotlin.math.abs(toCx - fromCx), kotlin.math.abs(toCz - fromCz))
        val visited = HashSet<Long>()
        for (i in 0..steps) {
            val t = if (steps == 0) 0.0 else i.toDouble() / steps
            val cx = Math.round(fromCx + (toCx - fromCx) * t).toInt()
            val cz = Math.round(fromCz + (toCz - fromCz) * t).toInt()
            for (dx in -PREFETCH_CORRIDOR_CHUNK_RADIUS..PREFETCH_CORRIDOR_CHUNK_RADIUS) {
                for (dz in -PREFETCH_CORRIDOR_CHUNK_RADIUS..PREFETCH_CORRIDOR_CHUNK_RADIUS) {
                    if (!visited.add(ChunkPos.toLong(cx + dx, cz + dz))) continue
                    for (sy in minSy..maxSy) {
                        if (sections.size - before >= MAX_PREFETCH_SECTIONS) {
                            return logPrefetch(sections.size - before, startedAt, capped = true)
                        }
                        val key = ChunkSectionPos.asLong(cx + dx, sy, cz + dz)
                        sections.computeIfAbsent(key) { copySection(cx + dx, sy, cz + dz) }
                    }
                }
            }
        }
        return logPrefetch(sections.size - before, startedAt, capped = false)
    }

    private fun logPrefetch(copied: Int, startedAt: Long, capped: Boolean): Int {
        LOG.info(
            "[SnapshotWorldView] Prefetched $copied sections in ${(System.nanoTime() - startedAt) / 1_000}µs" +
                if (capped) " (cap reached; remainder faults in per column)" else ""
        )
        return copied
    }

    /**
     * Write-through for an observed block change. Client thread. Overrides
     * remain sparse; sections the planner has never read stay uncopied.
     */
    fun applyObserved(pos: BlockPos, newState: BlockState) {
        if (world.isOutOfHeightLimit(pos.y)) return
        val key = ChunkSectionPos.asLong(pos.x shr 4, pos.y shr 4, pos.z shr 4)
        observedOverrides.computeIfAbsent(key) { ConcurrentHashMap() }[
            localIndex(pos.x, pos.y, pos.z)
        ] = BlockTraitRegistry.idOf(newState)
    }

    /**
     * Drops cached sections of one chunk so the next read re-copies. Called
     * on chunk load (previously-unknown terrain must refresh) and
     * unload (the next read becomes conservative unknown).
     */
    fun evictChunk(chunkPos: ChunkPos): Int {
        val bottom = world.bottomSectionCoord
        var removed = 0
        for (sy in bottom until bottom + world.countVerticalSections()) {
            val key = ChunkSectionPos.asLong(chunkPos.x, sy, chunkPos.z)
            observedOverrides.remove(key)
            if (sections.remove(key) != null) {
                removed++
            }
        }
        if (removed > 0) sectionGeneration.incrementAndGet()
        return removed
    }

    val cachedSectionCount: Int get() = sections.size

    /** Client thread only — decodes live chunk palettes. */
    private fun copySection(sx: Int, sy: Int, sz: Int): IntArray {
        val chunk = world.getChunk(sx, sz, ChunkStatus.FULL, false) ?: return UNKNOWN_SECTION
        val section = chunk.getSection(chunk.sectionCoordToIndex(sy))
        if (section.isEmpty) return AIR_SECTION

        val result = IntArray(SECTION_VOLUME)
        var i = 0
        for (y in 0..15) {
            for (z in 0..15) {
                for (x in 0..15) {
                    result[i++] = BlockTraitRegistry.idOf(section.getBlockState(x, y, z))
                }
            }
        }
        return result
    }

    private fun localIndex(x: Int, y: Int, z: Int): Int =
        ((y and 15) shl 8) or ((z and 15) shl 4) or (x and 15)

    companion object {
        private const val SECTION_VOLUME = 16 * 16 * 16
        private val AIR_SECTION = IntArray(0)
        private val UNKNOWN_SECTION = IntArray(0)
        private val UNKNOWN_STATE_ID by lazy { BlockTraitRegistry.idOf(Blocks.BEDROCK.defaultState) }

        /** One client-thread hop per miss; generous, since a miss is rare. */
        private const val SECTION_FAULT_TIMEOUT_MS = 250L

        private const val FAULT_TIMEOUT_LOG_LIMIT = 8

        // Corridor prefetch shape: ±3 chunks (48 blocks) around the
        // start→goal line, ±16 blocks vertically around the endpoint band.
        // 512 sections ≈ 8 MB of ints and low tens of milliseconds, once
        // per traversal request.
        private const val PREFETCH_CORRIDOR_CHUNK_RADIUS = 3
        private const val PREFETCH_MARGIN_Y = 16
        private const val MAX_PREFETCH_SECTIONS = 512
    }
}
