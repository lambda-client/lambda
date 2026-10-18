package com.lambda.pathing.debug

import com.lambda.pathing.search.TrajectoryPlan
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.Vec3d
import kotlin.math.floor

/**
 * What one plan improvement changed: the stretches of the old tape it dropped and of the new tape it added.
 * Both tapes share their prefix up to the junction frame by frame; after it, a point counts as kept while the
 * other tape passes within [NEAR] blocks, so a suffix that rejoins the old line shows only the detour.
 */
class ImprovementDiff(val removed: List<List<Vec3d>>, val added: List<List<Vec3d>>, val gainedFrames: Int) {
	companion object {
		private const val NEAR = 0.3

		fun of(old: TrajectoryPlan, new: TrajectoryPlan): ImprovementDiff {
			val before = positions(old)
			val after = positions(new)
			var shared = 0
			while (shared < before.size && shared < after.size && before[shared] == after[shared]) shared++
			val from = (shared - 1).coerceAtLeast(0)
			return ImprovementDiff(
				removed = stretchesOff(before, from, cells(after, from)),
				added = stretchesOff(after, from, cells(before, from)),
				gainedFrames = old.frames.size - new.frames.size,
			)
		}

		private fun positions(plan: TrajectoryPlan): List<Vec3d> = listOf(plan.initialState.position) + plan.frames.map { it.state.position }

		private fun cells(points: List<Vec3d>, from: Int): LongOpenHashSet {
			val cells = LongOpenHashSet()
			for (i in from until points.size) cells.add(key(cell(points[i].x), cell(points[i].y), cell(points[i].z)))
			return cells
		}

		private fun near(cells: LongOpenHashSet, point: Vec3d): Boolean {
			val x = cell(point.x)
			val y = cell(point.y)
			val z = cell(point.z)
			for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) if (key(x + dx, y + dy, z + dz) in cells) return true
			return false
		}

		/** Runs of [points] after [from] that the other tape does not pass, each widened by one point to touch the kept line. */
		private fun stretchesOff(points: List<Vec3d>, from: Int, other: LongOpenHashSet): List<List<Vec3d>> {
			val stretches = ArrayList<List<Vec3d>>()
			var start = -1
			for (i in from..points.size) {
				val off = i < points.size && !near(other, points[i])
				if (off && start < 0) start = i
				if (!off && start >= 0) {
					stretches += points.subList((start - 1).coerceAtLeast(from), (i + 1).coerceAtMost(points.size)).toList()
					start = -1
				}
			}
			return stretches
		}

		private fun cell(value: Double): Int = floor(value / NEAR).toInt()

		private fun key(x: Int, y: Int, z: Int): Long = (x.toLong() and 0x1FFFFF shl 42) or (y.toLong() and 0x1FFFFF shl 21) or (z.toLong() and 0x1FFFFF)
	}
}
