package com.lambda.pathing.world.streaming

import com.lambda.pathing.physics.WaterPhysics
import com.lambda.pathing.world.snapshot.BlockPhysicsCapture
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.registry.tag.FluidTags
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.IdentityHashMap

class MinecraftCaptureSource(
	private val world: World,
	private val player: ClientPlayerEntity,
) : CaptureSource {
	private val mutablePos = BlockPos.Mutable()
	private val shapeContext = ShapeContext.of(player)
	private val physicsByState = IdentityHashMap<BlockState, SnapshotBlockPhysics>()
	private val physicsByWater = HashMap<Pair<BlockState, WaterPhysics>, SnapshotBlockPhysics>()

	override fun isChunkLoaded(chunkX: Int, chunkZ: Int): Boolean =
		world.chunkManager.isChunkLoaded(chunkX, chunkZ)

	override val playerChunkX: Int get() = player.chunkPos.x

	override val playerChunkZ: Int get() = player.chunkPos.z

	override val viewDistance: Int get() = MinecraftClient.getInstance().options.clampedViewDistance

	override fun physicsAt(x: Int, y: Int, z: Int): SnapshotBlockPhysics {
		val pos = mutablePos.set(x, y, z)
		val state = world.getBlockState(pos)
		if (state.block.hasDynamicBounds()) return BlockPhysicsCapture.capture(state, world, pos, shapeContext)
		val fluid = state.fluidState
		if (!fluid.isIn(FluidTags.WATER)) return physicsByState.getOrPut(state) { BlockPhysicsCapture.capture(state, world, pos, shapeContext) }
		val water = WaterPhysics(fluid.getHeight(world, pos), fluid.getVelocity(world, pos))
		return physicsByWater.getOrPut(state to water) { BlockPhysicsCapture.capture(state, world, pos, shapeContext) }
	}

	override fun isOnClientThread(): Boolean = MinecraftClient.getInstance().isOnThread
}
