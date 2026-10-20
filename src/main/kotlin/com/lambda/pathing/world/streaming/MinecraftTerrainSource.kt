package com.lambda.pathing.world.streaming

import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.world.snapshot.BlockPhysicsTable
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SectionDerivation
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.MathHelper
import net.minecraft.world.BlockView
import net.minecraft.world.chunk.ChunkSection
import net.minecraft.world.chunk.ChunkStatus
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

internal class MinecraftTerrainSource(
	private val world: ClientWorld,
	private val player: ClientPlayerEntity,
	private val avoidMobs: Boolean = false,
	mobLingerTicks: Long = EntityObstacles.LINGER_TICKS,
	private val detachedCapacity: Int = DETACHED_CHUNKS,
	private val searchDistance: Int = Int.MAX_VALUE,
) : TerrainSource {
	private val chunkManager = world.chunkManager
	private val bottomSection = world.bottomSectionCoord
	private val view = LiveBlockView()
	private val obstacles = EntityObstacles(mobLingerTicks)
	private val occupied = LongOpenHashSet()
	private val changed = LongArrayList()

	@Volatile
	private var detached = Long2ObjectOpenHashMap<Array<ChunkSection>>()
	private val detachOrder = LongLinkedOpenHashSet()
	private val admitted: MutableSet<Long> = ConcurrentHashMap.newKeySet()
	private var admittedX = Int.MIN_VALUE
	private var admittedZ = Int.MIN_VALUE
	private var admittedRadius = Int.MIN_VALUE

	init {
		forEachLiveChunk { chunkX, chunkZ -> if (mayLoad(chunkX, chunkZ)) admitted.add(PathingChunk.key(chunkX, chunkZ)) }
	}

	private fun sections(chunkX: Int, chunkZ: Int): Array<ChunkSection>? {
		val key = PathingChunk.key(chunkX, chunkZ)
		if (key in admitted) chunkManager.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false)?.sectionArray?.let { return it }
		return detached.get(key)
	}

	override fun isReadable(chunkX: Int, chunkZ: Int): Boolean = sections(chunkX, chunkZ) != null

	override fun mayLoad(chunkX: Int, chunkZ: Int): Boolean {
		val radius = minOf(searchDistance, viewDistance)
		val dx = maxOf(0, abs(chunkX - playerChunkX) - Vanilla.CHUNK_FILTER_EDGE_MARGIN).toLong()
		val dz = maxOf(0, abs(chunkZ - playerChunkZ) - Vanilla.CHUNK_FILTER_EDGE_MARGIN).toLong()
		return dx * dx + dz * dz < radius.toLong() * radius
	}

	override fun admits(chunkX: Int, chunkZ: Int): Boolean = PathingChunk.key(chunkX, chunkZ) in admitted || mayLoad(chunkX, chunkZ)

	override fun admissions(admit: (chunkX: Int, chunkZ: Int) -> Unit) {
		val radius = minOf(searchDistance, viewDistance)
		val centerX = playerChunkX
		val centerZ = playerChunkZ
		if (centerX == admittedX && centerZ == admittedZ && radius == admittedRadius) return
		admittedX = centerX
		admittedZ = centerZ
		admittedRadius = radius
		val reach = radius + Vanilla.CHUNK_FILTER_EDGE_MARGIN
		for (chunkX in centerX - reach..centerX + reach) for (chunkZ in centerZ - reach..centerZ + reach) {
			if (PathingChunk.key(chunkX, chunkZ) in admitted || !mayLoad(chunkX, chunkZ)) continue
			if (chunkManager.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null) admit(chunkX, chunkZ)
		}
	}

	override fun derive(sectionKey: Long, table: BlockPhysicsTable): ImmutableSnapshotSection? {
		val chunkX = ChunkSectionPos.unpackX(sectionKey)
		val chunkZ = ChunkSectionPos.unpackZ(sectionKey)
		val sections = sections(chunkX, chunkZ) ?: return null
		val sectionY = ChunkSectionPos.unpackY(sectionKey)
		val section = sections.getOrNull(sectionY - bottomSection) ?: return VOID
		return SectionDerivation.derive(section.blockStateContainer, table, chunkX shl 4, sectionY shl 4, chunkZ shl 4, view)
	}

	override fun physicsAt(x: Int, y: Int, z: Int, table: BlockPhysicsTable): SnapshotBlockPhysics? {
		if (!isReadable(x shr 4, z shr 4)) return null
		val pos = BlockPos(x, y, z)
		return table.physics(view.getBlockState(pos), view, pos)
	}

	override fun chunkFlags(chunkX: Int, chunkZ: Int, table: BlockPhysicsTable): Int {
		val sections = sections(chunkX, chunkZ) ?: return 0
		var flags = 0
		try {
			for (section in sections) {
				val palette = section.blockStateContainer.data.palette()
				if (palette.size > PALETTE_SCAN_LIMIT) return BlockPhysicsTable.ALL
				for (id in 0 until palette.size) flags = flags or table.entry(palette.get(id)).flags
			}
		} catch (_: RuntimeException) {
			return BlockPhysicsTable.ALL
		}
		return flags
	}

	override fun forEachReadableChunk(action: (chunkX: Int, chunkZ: Int) -> Unit) {
		forEachLiveChunk { chunkX, chunkZ -> if (PathingChunk.key(chunkX, chunkZ) in admitted) action(chunkX, chunkZ) }
		val keys = detached.keys.iterator()
		while (keys.hasNext()) {
			val key = keys.nextLong()
			action(key.toInt(), (key ushr 32).toInt())
		}
	}

	private inline fun forEachLiveChunk(action: (chunkX: Int, chunkZ: Int) -> Unit) {
		val live = chunkManager.chunks.chunks
		for (index in 0 until live.length()) live.get(index)?.pos?.let { action(it.x, it.z) }
	}

	override fun attach(chunkX: Int, chunkZ: Int): Boolean {
		val key = PathingChunk.key(chunkX, chunkZ)
		if (!admitted.add(key)) return true
		if (!detachOrder.remove(key)) return false
		detached = detached.clone().apply { remove(key) }
		return true
	}

	override fun detach(chunkX: Int, chunkZ: Int, sections: Array<ChunkSection>?): LongArray {
		val key = PathingChunk.key(chunkX, chunkZ)
		if (!admitted.remove(key)) return LongArray(0)
		if (sections == null) return longArrayOf(key)
		val next = detached.clone()
		next.put(key, sections)
		detachOrder.addAndMoveToLast(key)
		val evicted = LongArrayList()
		while (detachOrder.size > detachedCapacity) {
			val oldest = detachOrder.removeFirstLong()
			next.remove(oldest)
			evicted.add(oldest)
		}
		detached = next
		return evicted.toLongArray()
	}

	override val playerChunkX: Int get() = player.chunkPos.x

	override val playerChunkZ: Int get() = player.chunkPos.z

	override val viewDistance: Int get() = MinecraftClient.getInstance().options.clampedViewDistance

	override fun isOnClientThread(): Boolean = MinecraftClient.getInstance().isOnThread

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

	private inner class LiveBlockView : BlockView {
		override fun getBlockState(pos: BlockPos): BlockState {
			val sections = sections(pos.x shr 4, pos.z shr 4) ?: return Blocks.AIR.defaultState
			val section = sections.getOrNull((pos.y shr 4) - bottomSection) ?: return Blocks.VOID_AIR.defaultState
			return section.getBlockState(pos.x and 15, pos.y and 15, pos.z and 15)
		}

		override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState

		override fun getBlockEntity(pos: BlockPos): BlockEntity? = try {
			chunkManager.getChunk(pos.x shr 4, pos.z shr 4, ChunkStatus.FULL, false)?.blockEntities?.get(pos)
		} catch (_: RuntimeException) {
			null
		}

		override fun getHeight(): Int = world.height

		override fun getBottomY(): Int = world.bottomY
	}

	companion object {
		const val DETACHED_CHUNKS = 1024
		private const val PALETTE_SCAN_LIMIT = 256
		private const val TRACKING_RADIUS = 24.0
		private const val PLAYER_CLEARANCE = 0.3
		private const val SUPPORT_PROBE = 0.2
		private val VOID = ImmutableSnapshotSection.uniform(SnapshotBlockPhysics.AIR)
	}
}
