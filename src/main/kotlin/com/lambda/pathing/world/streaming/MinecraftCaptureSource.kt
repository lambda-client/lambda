package com.lambda.pathing.world.streaming

import com.lambda.pathing.world.snapshot.BlockPhysicsSampler
import com.lambda.pathing.world.snapshot.HazardRules
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.block.ShapeContext
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.MathHelper
import net.minecraft.world.chunk.ChunkStatus
import net.minecraft.world.World

class MinecraftCaptureSource(
	private val world: World,
	private val player: ClientPlayerEntity,
	hazards: HazardRules,
	private val avoidMobs: Boolean = false,
	mobLingerTicks: Long = EntityObstacles.LINGER_TICKS,
) : CaptureSource {
	private val sampler = BlockPhysicsSampler(world, ShapeContext.of(player), hazards)
	private val obstacles = EntityObstacles(mobLingerTicks)
	private val occupied = LongOpenHashSet()
	private val changed = LongArrayList()

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

	override fun trackObstacles(): LongArray {
		if (!avoidMobs) return obstacles.cells
		occupied.clear()
		val clearance = player.boundingBox.expand(PLAYER_CLEARANCE)
		for (entity in world.getOtherEntities(player, player.boundingBox.expand(TRACKING_RADIUS), ::pushes)) {
			val box = entity.boundingBox
			for (x in MathHelper.floor(box.minX) until MathHelper.ceil(box.maxX)) {
				for (y in MathHelper.floor(box.minY) until MathHelper.ceil(box.maxY)) {
					for (z in MathHelper.floor(box.minZ) until MathHelper.ceil(box.maxZ)) {
						if (clearance.intersects(x.toDouble(), y.toDouble(), z.toDouble(), x + 1.0, y + 1.0, z + 1.0)) continue
						occupied.add(BlockPos.asLong(x, y, z))
					}
				}
			}
		}
		val body = player.boundingBox
		obstacles.update(occupied, changed) { cell ->
			val x = BlockPos.unpackLongX(cell).toDouble()
			val y = BlockPos.unpackLongY(cell).toDouble()
			val z = BlockPos.unpackLongZ(cell).toDouble()
			body.intersects(x, y, z, x + 1.0, y + 1.0, z + 1.0)
		}
		changed.clear()
		return obstacles.cells
	}

	private fun pushes(entity: Entity): Boolean =
		entity.isAlive && entity.isPushable && !entity.isTouchingWater && !entity.isSpectator && supported(entity) &&
			!entity.isConnectedThroughVehicle(player)

	private fun supported(entity: Entity): Boolean {
		val below = BlockPos.ofFloored(entity.x, entity.y - SUPPORT_PROBE, entity.z)
		return !world.getBlockState(below).getCollisionShape(world, below).isEmpty
	}

	override fun isOnClientThread(): Boolean = MinecraftClient.getInstance().isOnThread

	private companion object {
		const val TRACKING_RADIUS = 24.0
		const val PLAYER_CLEARANCE = 0.3
		const val SUPPORT_PROBE = 0.2
	}
}
