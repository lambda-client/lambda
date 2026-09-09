package com.lambda.pathing.world

import com.lambda.pathing.world.snapshot.BlockPhysicsInterner
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import net.minecraft.block.ShapeContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class MinecraftCaptureSource(
	private val world: World,
	private val player: ClientPlayerEntity,
) : CaptureSource {
	private val mutablePos = BlockPos.Mutable()
	private val physicsInterner = BlockPhysicsInterner(ShapeContext.of(player))

	override fun isChunkLoaded(chunkX: Int, chunkZ: Int): Boolean =
		world.chunkManager.isChunkLoaded(chunkX, chunkZ)

	override val playerChunkX: Int get() = player.chunkPos.x

	override val playerChunkZ: Int get() = player.chunkPos.z

	override val viewDistance: Int get() = MinecraftClient.getInstance().options.clampedViewDistance

	override fun physicsAt(x: Int, y: Int, z: Int): SnapshotBlockPhysics {
		val pos = mutablePos.set(x, y, z)
		return physicsInterner.capture(world, pos, world.getBlockState(pos))
	}

	override fun isOnClientThread(): Boolean = MinecraftClient.getInstance().isOnThread
}
