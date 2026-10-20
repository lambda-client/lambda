package com.lambda.pathing.world.edit

import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.physics.FallRule
import com.lambda.pathing.world.CellClass
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import com.lambda.pathing.world.SECTION_CELL_COUNT
import com.lambda.pathing.world.TerrainJournal
import com.lambda.pathing.world.snapshot.DoorToggle
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShape

internal class FlippedView(private val base: CoarseVoxelView, private val flipped: Long2ObjectMap<SnapshotBlockPhysics>) : CoarseVoxelView by base {
	private fun at(x: Int, y: Int, z: Int): SnapshotBlockPhysics? = flipped.get(BlockPos.asLong(x, y, z))

	override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel =
		at(x, y, z)?.let { if (it.unsupportedPhysics == null) it.coarseVoxel else CoarseVoxel.HAZARD } ?: base.voxel(x, y, z)

	override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape? = at(x, y, z)?.coarseShape ?: base.collisionShape(x, y, z)

	override fun collisionBoxes(x: Int, y: Int, z: Int): DoubleArray? = at(x, y, z)?.coarseBoxes ?: base.collisionBoxes(x, y, z)

	override fun collisionClass(x: Int, y: Int, z: Int): CollisionClass = at(x, y, z)?.collisionClass ?: base.collisionClass(x, y, z)

	override fun medium(x: Int, y: Int, z: Int) = voxel(x, y, z).medium

	override fun bubbles(x: Int, y: Int, z: Int): Boolean = at(x, y, z)?.let { it.water?.bubbles != null } ?: base.bubbles(x, y, z)

	override fun honey(x: Int, y: Int, z: Int): Boolean = at(x, y, z)?.honey ?: base.honey(x, y, z)

	override fun fallRule(x: Int, y: Int, z: Int): FallRule = at(x, y, z)?.fallRule ?: base.fallRule(x, y, z)

	override fun toggleAt(x: Int, y: Int, z: Int): DoorToggle? = at(x, y, z)?.toggle ?: base.toggleAt(x, y, z)

	override fun standingSurface(x: Int, y: Int, z: Int): Double? {
		voxel(x, y, z).standingSurface?.let { return it }
		return voxel(x, y - 1, z).intrusionHeight.takeIf { it > 0.0 && it < 1.0 }
	}

	override fun surfaceOffset(x: Int, y: Int, z: Int): Double = (standingSurface(x, y, z) ?: 1.0) - 1.0

	override fun cellClass(x: Int, y: Int, z: Int): CellClass = at(x, y, z)?.cellClass ?: base.cellClass(x, y, z)

	override fun classifySection(sectionX: Int, sectionY: Int, sectionZ: Int, planes: Array<LongArray>) {
		val baseX = sectionX shl 4
		val baseY = sectionY shl 4
		val baseZ = sectionZ shl 4
		for (index in 0 until SECTION_CELL_COUNT) cellClass(baseX + (index and 15), baseY + (index ushr 8), baseZ + ((index ushr 4) and 15)).mark(planes, index)
	}

	override fun cursor(): CoarseVoxelView = this

	override val terrain: Any get() = this

	override val revision: Any? get() = null

	override val journal: TerrainJournal? get() = null
}

internal object Flips {
	private const val MAX_UNITS = 6

	fun units(view: CoarseVoxelView, originX: Int, originY: Int, originZ: Int, offsets: List<VoxelPos>, bounds: IntArray): List<BlockPos> {
		if (!view.mayToggleWithin(originX + bounds[0], originY + bounds[1], originZ + bounds[2], originX + bounds[3], originY + bounds[4], originZ + bounds[5])) return emptyList()
		val units = LinkedHashSet<BlockPos>()
		for (offset in offsets) {
			val x = originX + offset.x
			val y = originY + offset.y
			val z = originZ + offset.z
			val toggle = view.toggleAt(x, y, z) ?: continue
			units += BlockPos(x, if (toggle.pairedY < 0) y + toggle.pairedY else y, z)
		}
		return units.sortedWith(compareBy<BlockPos>({ it.y }, { it.x }, { it.z })).take(MAX_UNITS)
	}

	class Flipped<T>(val result: T, val states: Map<VoxelPos, Boolean>, val units: Int)

	fun <T : Any> search(view: CoarseVoxelView, units: List<BlockPos>, pairs: Boolean = true, evaluate: (CoarseVoxelView) -> T?): Flipped<T>? {
		for (unit in units) attempt(view, listOf(unit), evaluate)?.let { return it }
		if (units.size < 2 || !pairs) return null
		for (first in units.indices) for (second in first + 1 until units.size) {
			attempt(view, listOf(units[first], units[second]), evaluate)?.let { return it }
		}
		return if (units.size > 2) attempt(view, units, evaluate) else null
	}

	fun units(view: CoarseVoxelView, cells: Iterable<BlockPos>): List<BlockPos> {
		val units = LinkedHashSet<BlockPos>()
		for (cell in cells) {
			val toggle = view.toggleAt(cell.x, cell.y, cell.z) ?: continue
			units += if (toggle.pairedY < 0) cell.up(toggle.pairedY) else cell
		}
		return units.toList()
	}

	private fun <T : Any> attempt(view: CoarseVoxelView, units: List<BlockPos>, evaluate: (CoarseVoxelView) -> T?): Flipped<T>? {
		val flipped = Long2ObjectOpenHashMap<SnapshotBlockPhysics>()
		val required = LinkedHashMap<VoxelPos, Boolean>()
		for (unit in units) {
			val toggle = view.toggleAt(unit.x, unit.y, unit.z) ?: continue
			flipped.put(unit.asLong(), toggle.toggled)
			required[VoxelPos(unit.x, unit.y, unit.z)] = !toggle.open
			if (toggle.pairedY != 0) {
				val y = unit.y + toggle.pairedY
				view.toggleAt(unit.x, y, unit.z)?.let { paired ->
					flipped.put(BlockPos.asLong(unit.x, y, unit.z), paired.toggled)
					required[VoxelPos(unit.x, y, unit.z)] = !paired.open
				}
			}
		}
		if (flipped.isEmpty()) return null
		val result = evaluate(FlippedView(view, flipped)) ?: return null
		return Flipped(result, required, units.size)
	}
}
