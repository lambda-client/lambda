package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.MotionTemplateId
import com.lambda.pathing.actions.families.DiveMovement
import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Medium
import com.lambda.pathing.world.TerrainWatch
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import net.minecraft.util.math.BlockPos
import kotlin.math.abs

internal fun interface ShaftVisitor {
	fun visit(dy: Int, ticks: Double)
}

internal class WaterShafts(
	private val view: CoarseVoxelView,
	private val standable: StanceMemo,
	private val ticksPerBlock: Double,
) {
	private val profiles = Long2ObjectLinkedOpenHashMap<BooleanArray>()
	private val watch = TerrainWatch(view)

	fun forEachFrom(origin: Stance, visitor: ShaftVisitor) {
		if (view.medium(origin.x, origin.y, origin.z) != Medium.WATER) return
		val top = surface(origin.x, origin.y, origin.z) ?: return
		if (top - origin.y > MAX_DEPTH) return
		val open = profile(origin.x, origin.z, top)
		below(origin, top, open)?.let { visitor.visit(it - origin.y, ticks(it - origin.y)) }
		above(origin, top, open)?.let { visitor.visit(it - origin.y, ticks(it - origin.y)) }
	}

	fun edgesFrom(origin: Stance): List<CoarseEdge> {
		var edges: ArrayList<CoarseEdge>? = null
		forEachFrom(origin) { dy, ticks -> edges = append(edges, origin, dy, ticks) }
		return edges ?: emptyList()
	}

	fun edgesFromOpenWater(origin: Stance): List<CoarseEdge> {
		val top = surface(origin.x, origin.y, origin.z) ?: return emptyList()
		if (top - origin.y > MAX_DEPTH) return emptyList()
		val open = profile(origin.x, origin.z, top)
		var edges: ArrayList<CoarseEdge>? = null
		var y = origin.y + 1
		while (y < top && open[top - y] && !standable.inGoalColumn(Stance(origin.x, y, origin.z))) y++
		if (standable.isStance(Stance(origin.x, y, origin.z))) edges = append(edges, origin, y - origin.y, ticks(y - origin.y))
		y = origin.y - 1
		while (top - y < open.size && open[top - y] && !standable.inGoalColumn(Stance(origin.x, y, origin.z))) y--
		if (top - y < open.size && standable.isStance(Stance(origin.x, y, origin.z))) edges = append(edges, origin, y - origin.y, ticks(y - origin.y))
		return edges ?: emptyList()
	}

	private fun append(edges: ArrayList<CoarseEdge>?, origin: Stance, dy: Int, ticks: Double): ArrayList<CoarseEdge> {
		val target = origin.offset(0, dy, 0)
		val reads = (minOf(origin.y, target.y)..maxOf(origin.y, target.y)).mapTo(HashSet()) { VoxelPos(origin.x, it, origin.z) }
		return (edges ?: ArrayList(2)).also { it += CoarseEdge(TEMPLATE, origin, target, MovementId.DIVE, ticks, reads) }
	}

	fun clear() {
		profiles.clear()
	}

	private fun ticks(dy: Int): Double = abs(dy) * ticksPerBlock

	private fun below(origin: Stance, top: Int, open: BooleanArray): Int? {
		var y = origin.y - 1
		while (top - y < open.size && open[top - y] && !standable.inGoalColumn(Stance(origin.x, y, origin.z))) y--
		if (y == origin.y - 1 || top - y >= open.size) return null
		return y.takeIf { standable.isStance(Stance(origin.x, y, origin.z)) }
	}

	private fun above(origin: Stance, top: Int, open: BooleanArray): Int? {
		if (origin.y >= top) return null
		var y = origin.y + 1
		while (y < top && open[top - y] && !standable.inGoalColumn(Stance(origin.x, y, origin.z))) y++
		if (y == origin.y + 1) return null
		return y.takeIf { standable.isStance(Stance(origin.x, y, origin.z)) }
	}

	private fun surface(x: Int, y: Int, z: Int): Int? {
		var top = y
		while (view.medium(x, top + 1, z) == Medium.WATER) top++
		return if (view.voxel(x, top + 1, z).fullyPassable) top else null
	}

	private fun forget(changed: CellBox) {
		val keys = profiles.keys.iterator()
		while (keys.hasNext()) {
			val key = keys.nextLong()
			val x = BlockPos.unpackLongX(key)
			val z = BlockPos.unpackLongZ(key)
			if (x in changed.xs.first - 1..changed.xs.last + 1 && z in changed.zs.first - 1..changed.zs.last + 1) keys.remove()
		}
	}

	private fun profile(x: Int, z: Int, top: Int): BooleanArray {
		watch.poll(::clear, ::forget)
		val key = BlockPos.asLong(x, top, z)
		profiles.getAndMoveToLast(key)?.let { return it }
		val cover = BooleanArray(SIDES)
		val roof = IntArray(SIDES)
		for (side in 0 until SIDES) {
			var y = top + 1
			while (view.medium(x + SIDE_X[side], y, z + SIDE_Z[side]) == Medium.WATER) y++
			cover[side] = view.voxel(x + SIDE_X[side], y, z + SIDE_Z[side]).fullyPassable
			roof[side] = y
		}
		var bottom = top
		while (top - bottom < MAX_DEPTH && view.medium(x, bottom - 1, z) == Medium.WATER) bottom--
		val open = BooleanArray(top - bottom + 1)
		val hanging = DiveMovement.hangsOverAir(view, x, bottom, z)
		for (y in top downTo bottom) {
			if (y < top) open[top - y] = !(hanging && y == bottom) && (0 until SIDES).none { side ->
				val voxel = view.voxel(x + SIDE_X[side], y, z + SIDE_Z[side])
				if (voxel.medium == Medium.WATER) !cover[side] || roof[side] == y + 1 else DiveMovement.exit(voxel)
			}
			for (side in 0 until SIDES) {
				val voxel = view.voxel(x + SIDE_X[side], y, z + SIDE_Z[side])
				if (voxel.medium != Medium.WATER) {
					cover[side] = voxel.fullyPassable
					roof[side] = y
				}
			}
		}
		profiles.putAndMoveToLast(key, open)
		if (profiles.size > MAX_PROFILES) profiles.removeFirst()
		return open
	}

	companion object {
		fun readers(changed: CellBox): CellBox = changed.grown(-1, 1, -MAX_DEPTH - 1, MAX_DEPTH + 1, -1, 1)

		private val TEMPLATE = MotionTemplateId(Int.MAX_VALUE - 1)

		private const val MAX_DEPTH = DiveMovement.AIR_REACH_BLOCKS + 1

		private const val MAX_PROFILES = 8192

		private const val SIDES = 4

		private val SIDE_X = intArrayOf(1, -1, 0, 0)
		private val SIDE_Z = intArrayOf(0, 0, 1, -1)
	}
}
