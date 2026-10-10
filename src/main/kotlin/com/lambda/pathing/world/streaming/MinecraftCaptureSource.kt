package com.lambda.pathing.world.streaming

import com.lambda.pathing.world.snapshot.BlockPhysicsSampler
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import net.minecraft.block.ShapeContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.world.World

class MinecraftCaptureSource(
	private val world: World,
	private val player: ClientPlayerEntity,
) : CaptureSource {
	private val sampler = BlockPhysicsSampler(world, ShapeContext.of(player))

	override fun isChunkLoaded(chunkX: Int, chunkZ: Int): Boolean =
		world.chunkManager.isChunkLoaded(chunkX, chunkZ)

	override val playerChunkX: Int get() = player.chunkPos.x

	override val playerChunkZ: Int get() = player.chunkPos.z

	override val viewDistance: Int get() = MinecraftClient.getInstance().options.clampedViewDistance

	override fun physicsAt(x: Int, y: Int, z: Int): SnapshotBlockPhysics = sampler.physicsAt(x, y, z)

	override fun isOnClientThread(): Boolean = MinecraftClient.getInstance().isOnThread
}
