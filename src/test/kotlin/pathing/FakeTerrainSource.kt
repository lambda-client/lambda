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
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.world.snapshot.BlockPhysicsTable
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.streaming.TerrainSource
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.world.chunk.ChunkSection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * A flat world read live: full cubes below [groundY], air at and above, with per-block overrides in
 * [blocks]. Chunks in [loaded] are readable; unloading one detaches it (still readable, like vanilla's
 * frozen section arrays) until more than [detachedCapacity] chunks are detached.
 */
internal class FakeTerrainSource(
    private val groundY: Int = 64,
    override var playerChunkX: Int = 0,
    override var playerChunkZ: Int = 0,
    override var viewDistance: Int = 8,
    var onClientThread: Boolean = true,
    private val detachedCapacity: Int = 1024,
) : TerrainSource {
    val loaded: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val detached: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val detachOrder = ArrayDeque<Long>()
    val blocks = ConcurrentHashMap<Long, SnapshotBlockPhysics>()
    private val overridden: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private val flags = ConcurrentHashMap<Long, Int>()
    val derived = AtomicLong()
    var duringDerive: (() -> Unit)? = null
    var searchDistance = Int.MAX_VALUE
    private val admitted: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    fun load(chunkX: Int, chunkZ: Int) = loaded.add(PathingChunk.key(chunkX, chunkZ))

    fun loadSquare(radius: Int) {
        for (chunkX in -radius..radius) for (chunkZ in -radius..radius) load(playerChunkX + chunkX, playerChunkZ + chunkZ)
    }

    fun set(x: Int, y: Int, z: Int, physics: SnapshotBlockPhysics) {
        blocks[BlockPos.asLong(x, y, z)] = physics
        overridden += ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
        flags.merge(PathingChunk.key(x shr 4, z shr 4), BlockPhysicsTable.flagsOf(physics), Int::or)
    }

    private fun cell(x: Int, y: Int, z: Int): SnapshotBlockPhysics =
        blocks[BlockPos.asLong(x, y, z)] ?: if (y < groundY) SnapshotBlockPhysics.FULL_CUBE else SnapshotBlockPhysics.AIR

    private val limited: Boolean get() = searchDistance != Int.MAX_VALUE

    override fun isReadable(chunkX: Int, chunkZ: Int): Boolean =
        PathingChunk.key(chunkX, chunkZ).let { it in loaded && (!limited || it in admitted) || it in detached }

    override fun mayLoad(chunkX: Int, chunkZ: Int): Boolean {
        val radius = minOf(searchDistance, viewDistance).toLong()
        val dx = maxOf(0, abs(chunkX - playerChunkX) - Vanilla.CHUNK_FILTER_EDGE_MARGIN).toLong()
        val dz = maxOf(0, abs(chunkZ - playerChunkZ) - Vanilla.CHUNK_FILTER_EDGE_MARGIN).toLong()
        return dx * dx + dz * dz < radius * radius
    }

    override fun admits(chunkX: Int, chunkZ: Int): Boolean =
        !limited || PathingChunk.key(chunkX, chunkZ) in admitted || mayLoad(chunkX, chunkZ)

    override fun admissions(admit: (chunkX: Int, chunkZ: Int) -> Unit) {
        if (!limited) return
        for (key in loaded.toList()) {
            val chunkX = key.toInt()
            val chunkZ = (key ushr 32).toInt()
            if (key !in admitted && mayLoad(chunkX, chunkZ)) admit(chunkX, chunkZ)
        }
    }

    override fun derive(sectionKey: Long, table: BlockPhysicsTable): ImmutableSnapshotSection? {
        if (!isReadable(ChunkSectionPos.unpackX(sectionKey), ChunkSectionPos.unpackZ(sectionKey))) return null
        derived.incrementAndGet()
        val baseX = ChunkSectionPos.unpackX(sectionKey) shl 4
        val baseY = ChunkSectionPos.unpackY(sectionKey) shl 4
        val baseZ = ChunkSectionPos.unpackZ(sectionKey) shl 4
        if (duringDerive == null && sectionKey !in overridden) {
            if (baseY >= groundY) return ImmutableSnapshotSection.uniform(SnapshotBlockPhysics.AIR)
            if (baseY + 15 < groundY) return ImmutableSnapshotSection.uniform(SnapshotBlockPhysics.FULL_CUBE)
        }
        val builder = ImmutableSnapshotSection.Builder()
        for (y in baseY until baseY + 16) for (z in baseZ until baseZ + 16) for (x in baseX until baseX + 16) {
            if (x == baseX + 8 && y == baseY + 8 && z == baseZ + 8) duringDerive?.invoke()
            builder.set(x, y, z, cell(x, y, z))
        }
        return builder.build()
    }

    override fun physicsAt(x: Int, y: Int, z: Int, table: BlockPhysicsTable): SnapshotBlockPhysics? =
        if (isReadable(x shr 4, z shr 4)) cell(x, y, z) else null

    override fun chunkFlags(chunkX: Int, chunkZ: Int, table: BlockPhysicsTable): Int =
        if (isReadable(chunkX, chunkZ)) flags[PathingChunk.key(chunkX, chunkZ)] ?: 0 else 0

    override fun forEachReadableChunk(action: (chunkX: Int, chunkZ: Int) -> Unit) {
        for (key in loaded + detached) action(key.toInt(), (key ushr 32).toInt())
    }

    override fun attach(chunkX: Int, chunkZ: Int): Boolean {
        val key = PathingChunk.key(chunkX, chunkZ)
        if (limited) {
            loaded.add(key)
            if (!admitted.add(key)) return true
            if (!detached.remove(key)) return false
            detachOrder.remove(key)
            return true
        }
        if (!loaded.add(key)) return true
        if (!detached.remove(key)) return false
        detachOrder.remove(key)
        return true
    }

    override fun detach(chunkX: Int, chunkZ: Int, sections: Array<ChunkSection>?): LongArray {
        val key = PathingChunk.key(chunkX, chunkZ)
        if (!loaded.remove(key) || limited && !admitted.remove(key)) return LongArray(0)
        detached += key
        detachOrder.addLast(key)
        val evicted = ArrayList<Long>()
        while (detachOrder.size > detachedCapacity) {
            val oldest = detachOrder.removeFirst()
            detached.remove(oldest)
            evicted += oldest
        }
        return evicted.toLongArray()
    }

    override fun isOnClientThread(): Boolean = onClientThread
}
