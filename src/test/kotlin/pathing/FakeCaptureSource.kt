/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.CaptureSource
import net.minecraft.util.math.BlockPos

/**
 * A flat world: full cubes below [groundY], air at and above, with per-block overrides in
 * [blocks]. Chunks are loaded when their packed position is in [loaded].
 */
class FakeCaptureSource(
    private val groundY: Int = 64,
    override var playerChunkX: Int = 0,
    override var playerChunkZ: Int = 0,
    override var viewDistance: Int = 8,
    var onClientThread: Boolean = true,
) : CaptureSource {
    val loaded = HashSet<Long>()
    val blocks = HashMap<Long, SnapshotBlockPhysics>()
    var reads = 0L

    fun load(chunkX: Int, chunkZ: Int) = loaded.add(chunkKey(chunkX, chunkZ))

    fun unload(chunkX: Int, chunkZ: Int) = loaded.remove(chunkKey(chunkX, chunkZ))

    fun loadSquare(radius: Int) {
        for (cx in -radius..radius) for (cz in -radius..radius) load(cx, cz)
    }

    fun set(x: Int, y: Int, z: Int, physics: SnapshotBlockPhysics) {
        blocks[BlockPos.asLong(x, y, z)] = physics
    }

    override fun isChunkLoaded(chunkX: Int, chunkZ: Int): Boolean = chunkKey(chunkX, chunkZ) in loaded

    override fun physicsAt(x: Int, y: Int, z: Int): SnapshotBlockPhysics {
        reads++
        return blocks[BlockPos.asLong(x, y, z)]
            ?: if (y < groundY) SnapshotBlockPhysics.FULL_CUBE else SnapshotBlockPhysics.AIR
    }

    override fun isOnClientThread(): Boolean = onClientThread

    private fun chunkKey(chunkX: Int, chunkZ: Int): Long =
        (chunkX.toLong() and 0xFFFFFFFFL) or ((chunkZ.toLong() and 0xFFFFFFFFL) shl 32)
}
