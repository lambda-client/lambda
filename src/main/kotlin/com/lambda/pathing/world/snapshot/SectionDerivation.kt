package com.lambda.pathing.world.snapshot

import com.lambda.pathing.world.SECTION_CELL_COUNT
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.BlockPos
import net.minecraft.world.BlockView
import net.minecraft.world.chunk.Palette
import net.minecraft.world.chunk.PalettedContainer

internal object SectionDerivation {
	private const val UNMAPPED = -1
	private const val POSITIONAL = -2
	private const val ARRAY_MAPPED = 256

	fun derive(container: PalettedContainer<BlockState>, table: BlockPhysicsTable, baseX: Int, baseY: Int, baseZ: Int, world: BlockView): ImmutableSnapshotSection {
		val data = container.data
		val palette = data.palette()
		if (palette.size == 1) table.entry(palette.get(0)).physics?.let { return ImmutableSnapshotSection.uniform(it) }
		val ids = IntArray(SECTION_CELL_COUNT)
		data.storage().writePaletteIndices(ids)
		val narrow = if (palette.size <= ARRAY_MAPPED) IntArray(palette.size).also { it.fill(UNMAPPED) } else null
		val wide = if (narrow == null) Int2IntOpenHashMap().apply { defaultReturnValue(UNMAPPED) } else null
		val physics = ArrayList<SnapshotBlockPhysics>()
		val slots = Reference2IntOpenHashMap<SnapshotBlockPhysics>().apply { defaultReturnValue(UNMAPPED) }
		fun slotOf(entry: SnapshotBlockPhysics): Int = slots.getInt(entry).takeIf { it != UNMAPPED }
			?: physics.size.also { slots.put(entry, it); physics += entry }
		val cells = IntArray(SECTION_CELL_COUNT)
		var positional = false
		for (index in 0 until SECTION_CELL_COUNT) {
			val id = ids[index]
			var mapped = if (narrow != null) narrow[id] else wide!!.get(id)
			if (mapped == UNMAPPED) {
				mapped = table.entry(palette.get(id)).physics?.let(::slotOf) ?: POSITIONAL
				if (narrow != null) narrow[id] = mapped else wide!!.put(id, mapped)
			}
			if (mapped == POSITIONAL) positional = true
			cells[index] = mapped
		}
		if (positional) {
			val view = SectionView(palette, ids, baseX, baseY, baseZ, world)
			val pos = BlockPos.Mutable()
			for (index in 0 until SECTION_CELL_COUNT) {
				if (cells[index] != POSITIONAL) continue
				pos.set(baseX + (index and 15), baseY + (index ushr 8), baseZ + ((index ushr 4) and 15))
				cells[index] = slotOf(table.physics(palette.get(ids[index]), view, pos))
			}
		}
		return ImmutableSnapshotSection.indexed(physics.toTypedArray(), cells)
	}

	private class SectionView(
		private val palette: Palette<BlockState>,
		private val ids: IntArray,
		private val baseX: Int,
		private val baseY: Int,
		private val baseZ: Int,
		private val world: BlockView,
	) : BlockView {
		override fun getBlockState(pos: BlockPos): BlockState {
			val x = pos.x - baseX
			val y = pos.y - baseY
			val z = pos.z - baseZ
			if (x !in 0..15 || y !in 0..15 || z !in 0..15) return world.getBlockState(pos)
			return palette.get(ids[(y shl 8) or (z shl 4) or x])
		}

		override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState

		override fun getBlockEntity(pos: BlockPos): BlockEntity? = world.getBlockEntity(pos)

		override fun getHeight(): Int = world.height

		override fun getBottomY(): Int = world.bottomY
	}
}
