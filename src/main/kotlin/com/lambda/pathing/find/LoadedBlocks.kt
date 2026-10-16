package com.lambda.pathing.find

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import net.minecraft.world.chunk.ChunkStatus

internal object LoadedBlocks {
	fun scan(world: World, origin: Vec3d, radius: Int, query: BlockQuery, nearest: NearestBlocks) {
		val centreX = MathHelper.floor(origin.x) shr 4
		val centreZ = MathHelper.floor(origin.z) shr 4
		val bottom = world.bottomY
		val top = world.bottomY + world.height - 1
		val probe = BlockPos.Mutable()
		val exposed = { x: Int, y: Int, z: Int ->
			!world.isChunkLoaded(x shr 4, z shr 4) || !world.getBlockState(probe.set(x, y, z)).isFullCube(world, probe)
		}
		val columns = ArrayList<Pair<Long, Double>>()
		for (chunkX in centreX - radius..centreX + radius) for (chunkZ in centreZ - radius..centreZ + radius) {
			if (!world.chunkManager.isChunkLoaded(chunkX, chunkZ)) continue
			val distance = nearest.boxDistance(chunkX shl 4, bottom, chunkZ shl 4, (chunkX shl 4) + 15, top, (chunkZ shl 4) + 15)
			columns += ChunkSectionPos.asLong(chunkX, 0, chunkZ) to distance
		}
		columns.sortBy { it.second }
		for ((column, distance) in columns) {
			if (distance >= nearest.farthest) break
			val chunkX = ChunkSectionPos.unpackX(column)
			val chunkZ = ChunkSectionPos.unpackZ(column)
			val chunk = world.chunkManager.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) ?: continue
			for ((index, section) in chunk.sectionArray.withIndex()) {
				nearest.scan(ChunkSectionPos.asLong(chunkX, world.sectionIndexToCoord(index), chunkZ), section.blockStateContainer, query, BlockSource.LOADED, exposed)
			}
		}
	}
}
