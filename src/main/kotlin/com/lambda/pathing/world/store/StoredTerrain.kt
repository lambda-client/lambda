package com.lambda.pathing.world.store

import com.lambda.pathing.world.snapshot.BlockPhysicsSampler
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ShapeContext
import net.minecraft.block.entity.BlockEntity
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.world.BlockView
import net.minecraft.world.chunk.PalettedContainer
import java.nio.file.Path

internal class StoredTerrain(
	private val sections: Long2ObjectOpenHashMap<PalettedContainer<BlockState>>,
	private val minY: Int,
	private val maxY: Int,
) : BlockView {
	override fun getBlockState(pos: BlockPos): BlockState =
		sections.get(ChunkSectionPos.toLong(pos))?.get(pos.x and 15, pos.y and 15, pos.z and 15) ?: Blocks.AIR.defaultState

	override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState

	override fun getBlockEntity(pos: BlockPos): BlockEntity? = null

	override fun getHeight(): Int = maxY - minY + 1

	override fun getBottomY(): Int = minY

	fun physics(ignoreHazards: Boolean): Map<Long, ImmutableSnapshotSection> {
		val sampler = BlockPhysicsSampler(this, ShapeContext.absent(), ignoreHazards)
		val physics = HashMap<Long, ImmutableSnapshotSection>(sections.size)
		for (key in sections.keys) {
			val baseX = ChunkSectionPos.unpackX(key) shl 4
			val baseY = ChunkSectionPos.unpackY(key) shl 4
			val baseZ = ChunkSectionPos.unpackZ(key) shl 4
			val builder = ImmutableSnapshotSection.Builder()
			for (y in baseY until baseY + 16) for (z in baseZ until baseZ + 16) for (x in baseX until baseX + 16) {
				builder.set(x, y, z, sampler.physicsAt(x, y, z))
			}
			physics[key] = builder.build()
		}
		return physics
	}

	companion object {
		fun load(directory: Path, sectionKeys: LongArray, at: Long, minY: Int, maxY: Int): StoredTerrain =
			StoredTerrain(Long2ObjectOpenHashMap(TerrainStore.load(directory, sectionKeys, at)), minY, maxY)
	}
}
