package com.lambda.pathing.launch

import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

object BounceArcProbe {

	fun probe(
		view: CoarseVoxelView,
		from: Stance,
		dx: Int,
		dz: Int,
		drop: Int,
		rise: Int,
		launchHeight: Double,
	): Probed<BounceSolution>? {
		val length = hypot(dx.toDouble(), dz.toDouble())
		if (length <= 0.0) return null
		return probed { reads -> BounceLine(view, from, dx, dz, length, drop, rise, launchHeight, reads).solve() }
	}

	fun padColumns(dx: Int, dz: Int): List<Pair<Int, Int>> {
		val length = hypot(dx.toDouble(), dz.toDouble())
		if (length <= 0.0) return emptyList()
		val columns = ArrayList<Pair<Int, Int>>()
		var along = 1.0
		while (along < length + 0.5) {
			columns += floor(0.5 + dx / length * along).toInt() to floor(0.5 + dz / length * along).toInt()
			along += 1.0
		}
		return columns
	}

	fun isPad(view: CoarseVoxelView, x: Int, y: Int, z: Int): Boolean {
		val pad = view.voxel(x, y, z)
		if (pad.bouncy) return true
		val surface = pad.standingSurface
		return surface != null && surface <= THIN_COVER_SURFACE && view.voxel(x, y - 1, z).bouncy
	}

	private class BounceLine(
		private val view: CoarseVoxelView,
		private val from: Stance,
		private val dx: Int,
		private val dz: Int,
		private val length: Double,
		private val drop: Int,
		private val rise: Int,
		private val launchHeight: Double,
		private val reads: LongOpenHashSet?,
	) {
		private val unitX = dx / length
		private val unitZ = dz / length
		private val padY = from.y - drop - 1
		private val launchOffset = launchHeight - from.y

		private fun cellX(along: Double) = floor(from.x + 0.5 + unitX * along).toInt()

		private fun cellZ(along: Double) = floor(from.z + 0.5 + unitZ * along).toInt()

		fun solve(): BounceSolution? {
			if (!crossesPad()) return null

			reads?.add(BlockPos.asLong(from.x + dx, from.y + rise - 1, from.z + dz))
			val landingOffset = view.surfaceOffset(from.x + dx, from.y + rise - 1, from.z + dz)
			val riseHeight = rise + landingOffset - launchOffset
			val headroom = headroom()
			val launchReach = launchReach()
			val arcs = ArcSweep(view, from, dx, dz, reads)
			val seed = seedContactOffset()

			for ((jump, sprint) in STANDING_STYLES) {
				refined(seed) { contactDepth ->
					BounceSolver.solveStanding(
						horizontalDistance = length,
						jump = jump, sprint = sprint,
						contactDepth = contactDepth, riseHeight = riseHeight,
						contactPenalty = ::contactPenalty,
						headroom = headroom,
						launchReach = launchReach,
					)
				}?.takeIf { admissible(it, arcs) }?.let { return it }
			}

			for ((jump, sprint, holdForward, holdTicks) in COMBOS) {
				refined(seed) { contactDepth ->
					BounceSolver.solve(
						horizontalDistance = length,
						sprint = sprint, holdForward = holdForward,
						jump = jump, holdTicks = holdTicks,
						contactDepth = contactDepth, riseHeight = riseHeight,
						headroom = headroom,
						launchReach = launchReach,
					)
				}?.takeIf { admissible(it, arcs) }?.let { return it }
			}
			return null
		}

		private inline fun refined(seed: Double, solveAtDepth: (Double) -> BounceSolution?): BounceSolution? {
			var contactOffset = seed
			repeat(CONTACT_REFINEMENTS) {
				val solution = solveAtDepth(drop + launchOffset - contactOffset) ?: return null
				val observed = padSurfaceOffset(solution.launchOffset + solution.contactDistance)
				if (abs(observed - contactOffset) < CONTACT_CONVERGENCE) return solution
				contactOffset = observed
			}
			return null
		}

		private fun admissible(solution: BounceSolution, arcs: ArcSweep): Boolean =
			contactPenalty(solution.launchOffset + solution.contactDistance).isFinite() &&
					arcs.sweep(solution.arc, solution.launchOffset, launchHeight) == Sweep.Clear

		private fun crossesPad(): Boolean {
			var along = 1.0
			while (along < length + 0.5) {
				if (bouncyCell(cellX(along), cellZ(along))) return true
				along += 1.0
			}
			return false
		}

		private fun headroom(): Double {
			val feetCell = floor(launchHeight).toInt()
			val reach = minOf(length, LAUNCH_ASCENT_REACH)
			var ceilingBottom = Double.POSITIVE_INFINITY
			var along = 0.0
			while (along <= reach) {
				val x = cellX(along)
				val z = cellZ(along)
				for (y in feetCell + 2..feetCell + 4) {
					reads?.add(BlockPos.asLong(x, y, z))
					if (!view.voxel(x, y, z).fullyPassable) {
						val bottom = y + (view.collisionShape(x, y, z)?.getMin(Direction.Axis.Y) ?: 0.0)
						if (bottom < ceilingBottom) ceilingBottom = bottom
						break
					}
				}
				along += 1.0
			}
			return ceilingBottom - BODY_HEIGHT - launchHeight
		}

		private fun seedContactOffset(): Double {
			var along = 1.0
			while (along < length) {
				val x = cellX(along)
				val z = cellZ(along)
				reads?.add(BlockPos.asLong(x, padY, z))
				if (view.voxel(x, padY, z).standingSurface != null) return view.surfaceOffset(x, padY, z)
				along += 1.0
			}
			return 0.0
		}

		private fun padSurfaceOffset(along: Double): Double {
			val x = cellX(along)
			val z = cellZ(along)
			reads?.add(BlockPos.asLong(x, padY, z))
			return view.surfaceOffset(x, padY, z)
		}

		private fun contactPenalty(reach: Double): Double {
			val px = from.x + 0.5 + unitX * reach
			val pz = from.z + 0.5 + unitZ * reach
			val x = floor(px).toInt()
			val z = floor(pz).toInt()
			if (!bouncyCell(x, z)) return Double.POSITIVE_INFINITY
			return hypot(px - (x + 0.5), pz - (z + 0.5))
		}

		private fun bouncyCell(x: Int, z: Int): Boolean {
			reads?.add(BlockPos.asLong(x, padY, z))
			val pad = view.voxel(x, padY, z)
			if (pad.bouncy) return true
			val surface = pad.standingSurface
			if (surface != null && surface <= THIN_COVER_SURFACE) {
				reads?.add(BlockPos.asLong(x, padY - 1, z))
				if (view.voxel(x, padY - 1, z).bouncy) return true
			}
			return false
		}

		private fun launchReach(): Double {
			val direct = view.voxel(from.x, from.y - 1, from.z).standingSurface != null
			val supportY = if (direct) from.y - 1 else from.y - 2
			reads?.add(BlockPos.asLong(from.x, supportY, from.z))
			val boxes = view.collisionBoxes(from.x, supportY, from.z)
				?: return BounceSolver.LAUNCH_OFFSET
			val surfaceLocal = launchHeight - supportY
			var extent = Double.NEGATIVE_INFINITY
			for (box in 0 until boxes.size / 6) {
				val b = 6 * box
				if (boxes[b + 4] < surfaceLocal - SUPPORT_SURFACE_EPSILON) continue
				val alongX = maxOf((boxes[b] - 0.5) * unitX, (boxes[b + 3] - 0.5) * unitX)
				val alongZ = maxOf((boxes[b + 2] - 0.5) * unitZ, (boxes[b + 5] - 0.5) * unitZ)
				extent = maxOf(extent, alongX + alongZ)
			}
			if (extent == Double.NEGATIVE_INFINITY) return BounceSolver.LAUNCH_OFFSET
			return (extent + BODY_HALF_WIDTH).coerceIn(BODY_HALF_WIDTH, BounceSolver.LAUNCH_OFFSET)
		}
	}

	private data class LaunchCombo(
		val jump: Boolean,
		val sprint: Boolean,
		val holdForward: Boolean,
		val holdTicks: Int = Int.MAX_VALUE,
	)

	private val STANDING_STYLES = listOf(
		true to false,
		false to false,
		true to true,
		false to true,
	)

	private val PARTIAL_HOLDS = intArrayOf(20, 16, 12, 8, 5, 3, 2)

	private val COMBOS = buildList {
		for (jump in listOf(true, false)) {
			add(LaunchCombo(jump, sprint = true, holdForward = true))
			add(LaunchCombo(jump, sprint = false, holdForward = true))
			for (holdTicks in PARTIAL_HOLDS) {
				add(LaunchCombo(jump, sprint = true, holdForward = true, holdTicks = holdTicks))
				add(LaunchCombo(jump, sprint = false, holdForward = true, holdTicks = holdTicks))
			}
			add(LaunchCombo(jump, sprint = false, holdForward = false))
		}
	}

	private const val SUPPORT_SURFACE_EPSILON = 1.0E-4

	private const val THIN_COVER_SURFACE = 0.2

	private const val LAUNCH_ASCENT_REACH = 3.5

	private const val CONTACT_REFINEMENTS = 3

	private const val CONTACT_CONVERGENCE = 1.0E-9
}
