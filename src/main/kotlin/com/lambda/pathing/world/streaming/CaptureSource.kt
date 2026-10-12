package com.lambda.pathing.world.streaming

import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import it.unimi.dsi.fastutil.longs.LongArrayList

interface CaptureSource {
	fun isChunkLoaded(chunkX: Int, chunkZ: Int): Boolean
	val playerChunkX: Int
	val playerChunkZ: Int
	val viewDistance: Int
	fun physicsAt(x: Int, y: Int, z: Int): SnapshotBlockPhysics
	fun uniformPhysics(sectionKey: Long): SnapshotBlockPhysics? = null
	fun isOnClientThread(): Boolean
	fun obstacleChanges(changed: LongArrayList) {}
	val obstacleCells: LongArray get() = NO_CELLS

	companion object {
		val NO_CELLS = LongArray(0)
	}
}
