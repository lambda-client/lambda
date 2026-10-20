package com.lambda.pathing.world.store

import com.lambda.pathing.world.snapshot.BlockPhysicsTable
import com.lambda.pathing.world.snapshot.HazardRules
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SectionDerivation
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
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

	fun physics(hazards: HazardRules): Map<Long, ImmutableSnapshotSection> {
		val table = BlockPhysicsTable.of(hazards)
		val physics = HashMap<Long, ImmutableSnapshotSection>(sections.size)
		for (entry in sections.long2ObjectEntrySet()) {
			val key = entry.longKey
			val baseX = ChunkSectionPos.unpackX(key) shl 4
			val baseY = ChunkSectionPos.unpackY(key) shl 4
			val baseZ = ChunkSectionPos.unpackZ(key) shl 4
			physics[key] = SectionDerivation.derive(entry.value, table, baseX, baseY, baseZ, this)
		}
		return physics
	}

	companion object {
		fun load(directory: Path, sectionKeys: LongArray, at: Long, minY: Int, maxY: Int): StoredTerrain =
			StoredTerrain(Long2ObjectOpenHashMap(TerrainStore.load(directory, sectionKeys, at)), minY, maxY)
	}
}
