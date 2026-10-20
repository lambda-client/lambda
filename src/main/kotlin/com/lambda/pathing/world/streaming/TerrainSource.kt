package com.lambda.pathing.world.streaming

import com.lambda.pathing.world.snapshot.BlockPhysicsTable
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import net.minecraft.world.chunk.ChunkSection

internal interface TerrainSource {
	fun isReadable(chunkX: Int, chunkZ: Int): Boolean

	fun mayLoad(chunkX: Int, chunkZ: Int): Boolean

	fun derive(sectionKey: Long, table: BlockPhysicsTable): ImmutableSnapshotSection?

	fun physicsAt(x: Int, y: Int, z: Int, table: BlockPhysicsTable): SnapshotBlockPhysics?

	fun chunkFlags(chunkX: Int, chunkZ: Int, table: BlockPhysicsTable): Int

	fun forEachReadableChunk(action: (chunkX: Int, chunkZ: Int) -> Unit)

	fun attach(chunkX: Int, chunkZ: Int): Boolean

	fun detach(chunkX: Int, chunkZ: Int, sections: Array<ChunkSection>?): LongArray

	val playerChunkX: Int

	val playerChunkZ: Int

	val viewDistance: Int

	fun isOnClientThread(): Boolean

	fun trackObstacles(): LongArray = NO_CELLS

	fun admits(chunkX: Int, chunkZ: Int): Boolean = true

	fun admissions(admit: (chunkX: Int, chunkZ: Int) -> Unit) {}

	companion object {
		val NO_CELLS = LongArray(0)
	}
}
