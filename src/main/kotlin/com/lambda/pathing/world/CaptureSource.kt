package com.lambda.pathing.world

import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics

/**
 * The client-side reads world capture needs, so [PathingWorld] can be driven without a
 * Minecraft client. All members are called on the client thread only.
 */
interface CaptureSource {
    fun isChunkLoaded(chunkX: Int, chunkZ: Int): Boolean
    val playerChunkX: Int
    val playerChunkZ: Int
    val viewDistance: Int
    fun physicsAt(x: Int, y: Int, z: Int): SnapshotBlockPhysics
    fun isOnClientThread(): Boolean
}
