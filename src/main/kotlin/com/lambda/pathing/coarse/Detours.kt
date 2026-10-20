package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.Vanilla
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos

internal class Detours private constructor(private val penalties: Long2DoubleOpenHashMap) {
	fun penalty(stance: Stance): Double = penalties.get(BlockPos.asLong(stance.x, stance.y, stance.z))

	companion object {
		private const val RADIUS = 5
		private const val BELOW = 2
		private const val ABOVE = 1
		private const val MAX_SWEEPS = 64
		private const val LOW_EDGE = 0.5 - Vanilla.PLAYER_HALF_WIDTH
		private const val HIGH_EDGE = 0.5 + Vanilla.PLAYER_HALF_WIDTH

		private fun floor(value: Double): Int = kotlin.math.floor(value).toInt()

		fun of(cells: LongArray, label: (Stance) -> Double, edges: (Stance) -> List<CoarseEdge>): Detours? {
			if (cells.isEmpty()) return null
			val occupied = LongOpenHashSet(cells)
			fun blocked(stance: Stance): Boolean =
				occupied.contains(BlockPos.asLong(stance.x, stance.y, stance.z)) || occupied.contains(BlockPos.asLong(stance.x, stance.y + 1, stance.z))
			fun crosses(from: Stance, to: Stance): Boolean {
				for (x in floor(minOf(from.x, to.x) + LOW_EDGE)..floor(maxOf(from.x, to.x) + HIGH_EDGE)) {
					for (z in floor(minOf(from.z, to.z) + LOW_EDGE)..floor(maxOf(from.z, to.z) + HIGH_EDGE)) {
						for (y in minOf(from.y, to.y)..maxOf(from.y, to.y) + 1) if (occupied.contains(BlockPos.asLong(x, y, z))) return true
					}
				}
				return false
			}
			val index = Long2IntOpenHashMap().apply { defaultReturnValue(-1) }
			val stances = ArrayList<Stance>()
			for (cell in cells) {
				val x = BlockPos.unpackLongX(cell)
				val y = BlockPos.unpackLongY(cell)
				val z = BlockPos.unpackLongZ(cell)
				for (dy in -BELOW..ABOVE) for (dz in -RADIUS..RADIUS) for (dx in -RADIUS..RADIUS) {
					val key = BlockPos.asLong(x + dx, y + dy, z + dz)
					if (index.containsKey(key)) continue
					val stance = Stance(x + dx, y + dy, z + dz)
					if (!label(stance).isFinite()) continue
					index.put(key, stances.size)
					stances += stance
				}
			}
			if (stances.isEmpty()) return null
			val count = stances.size
			val outgoing = Array(count) { edges(stances[it]) }
			val stuck = BooleanArray(count) { blocked(stances[it]) }
			val free = DoubleArray(count) { if (label(stances[it]) == 0.0) 0.0 else Double.POSITIVE_INFINITY }
			val avoiding = DoubleArray(count) { if (label(stances[it]) == 0.0 && !stuck[it]) 0.0 else Double.POSITIVE_INFINITY }
			var sweeps = 0
			do {
				var changed = false
				for (at in 0 until count) {
					if (free[at] == 0.0) continue
					var viaFree = free[at]
					var viaAvoiding = avoiding[at]
					for (edge in outgoing[at]) {
						val to = edge.to
						val target = index.get(BlockPos.asLong(to.x, to.y, to.z))
						val outside = if (target < 0) label(to) else 0.0
						viaFree = minOf(viaFree, edge.lowerBoundTicks + if (target < 0) outside else free[target])
						if (!stuck[at] && !blocked(to) && !crosses(stances[at], to)) viaAvoiding = minOf(viaAvoiding, edge.lowerBoundTicks + if (target < 0) outside else avoiding[target])
					}
					if (viaFree < free[at]) {
						free[at] = viaFree
						changed = true
					}
					if (viaAvoiding < avoiding[at]) {
						avoiding[at] = viaAvoiding
						changed = true
					}
				}
			} while (changed && ++sweeps < MAX_SWEEPS)
			val penalties = Long2DoubleOpenHashMap().apply { defaultReturnValue(0.0) }
			for (at in 0 until count) {
				val stance = stances[at]
				val penalty = when {
					stuck[at] -> Double.POSITIVE_INFINITY
					!free[at].isFinite() -> 0.0
					else -> avoiding[at] - free[at]
				}
				if (penalty > 0.0) penalties.put(BlockPos.asLong(stance.x, stance.y, stance.z), penalty)
			}
			return Detours(penalties)
		}
	}
}
