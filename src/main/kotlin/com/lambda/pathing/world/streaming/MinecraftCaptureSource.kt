package com.lambda.pathing.world.streaming

import com.lambda.pathing.world.snapshot.BlockPhysicsSampler
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import net.minecraft.block.ShapeContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.world.chunk.ChunkStatus
import net.minecraft.world.World

class MinecraftCaptureSource(
	private val world: World,
	private val player: ClientPlayerEntity,
	ignoreHazards: Boolean,
) : CaptureSource {
	private val sampler = BlockPhysicsSampler(world, ShapeContext.of(player), ignoreHazards)

	override fun isChunkLoaded(chunkX: Int, chunkZ: Int): Boolean =
		world.chunkManager.isChunkLoaded(chunkX, chunkZ)

	override val playerChunkX: Int get() = player.chunkPos.x

	override val playerChunkZ: Int get() = player.chunkPos.z

	override val viewDistance: Int get() = MinecraftClient.getInstance().options.clampedViewDistance

	override fun physicsAt(x: Int, y: Int, z: Int): SnapshotBlockPhysics = sampler.physicsAt(x, y, z)

	override fun uniformPhysics(sectionKey: Long): SnapshotBlockPhysics? {
		val sectionX = ChunkSectionPos.unpackX(sectionKey)
		val sectionY = ChunkSectionPos.unpackY(sectionKey)
		val sectionZ = ChunkSectionPos.unpackZ(sectionKey)
		val index = world.sectionCoordToIndex(sectionY)
		if (index !in 0 until world.countVerticalSections()) return null
		val chunk = world.chunkManager.getChunk(sectionX, sectionZ, ChunkStatus.FULL, false) ?: return null
		if (!chunk.getSection(index).isEmpty) return null
		return sampler.physicsAt(sectionX shl 4, sectionY shl 4, sectionZ shl 4)
	}

	override fun isOnClientThread(): Boolean = MinecraftClient.getInstance().isOnThread
}
