package com.lambda.pathing.world

import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics

interface CaptureSource {
	fun isChunkLoaded(chunkX: Int, chunkZ: Int): Boolean
	val playerChunkX: Int
	val playerChunkZ: Int
	val viewDistance: Int
	fun physicsAt(x: Int, y: Int, z: Int): SnapshotBlockPhysics
	fun isOnClientThread(): Boolean
}
