package com.lambda.pathing.launch

import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Footing
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
		bounces: Int = 1,
		reboundHeadroom: Double = Double.POSITIVE_INFINITY,
		levels: IntArray? = null,
	): Probed<BounceSolution>? {
		val length = hypot(dx.toDouble(), dz.toDouble())
		if (length <= 0.0) return null
		return probed { reads -> BounceLine(view, from, dx, dz, length, drop, rise, launchHeight, levels?.size ?: bounces, reboundHeadroom, reads, levels).solve() }
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
		private val bounces: Int,
		private val reboundHeadroom: Double,
		private val reads: LongOpenHashSet?,
		private val levels: IntArray? = null,
	) {
		private val unitX = dx / length
		private val unitZ = dz / length
		private val padY = from.y - drop - 1
		private val launchOffset = launchHeight - from.y

		private fun cellX(along: Double) = floor(from.x + 0.5 + unitX * along).toInt()

		private fun cellZ(along: Double) = floor(from.z + 0.5 + unitZ * along).toInt()

		fun solve(): BounceSolution? {
			levels?.let { return solveLevels(it) }
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
						contactPenalty = { _, reach -> contactPenalty(reach) },
						headroom = headroom,
						launchReach = launchReach,
						bounces = bounces,
						reboundHeadroom = reboundHeadroom,
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
						bounces = bounces,
						reboundHeadroom = reboundHeadroom,
					)
				}?.takeIf { admissible(it, arcs) }?.let { return it }
			}
			return climb(headroom, launchReach, arcs, seed)
		}

		private fun solveLevels(levels: IntArray): BounceSolution? {
			for (level in levels.distinct()) if (!crossesPad(level)) return null

			reads?.add(BlockPos.asLong(from.x + dx, from.y + rise - 1, from.z + dz))
			val landingOffset = view.surfaceOffset(from.x + dx, from.y + rise - 1, from.z + dz)
			val riseHeight = rise + landingOffset - launchOffset
			val headroom = headroom()
			val launchReach = launchReach()
			val arcs = ArcSweep(view, from, dx, dz, reads)
			val seeds = DoubleArray(levels.size) { seedContactOffset(levels[it]) }
			fun admissible(solution: BounceSolution): Boolean {
				val distances = solution.contactDistances
				return distances.size == levels.size &&
						distances.indices.all { contactPenalty(solution.launchOffset + distances[it], levels[it]).isFinite() } &&
						arcs.sweep(solution.arc, solution.launchOffset, launchHeight) == Sweep.Clear
			}

			for ((jump, sprint) in STANDING_STYLES) {
				refinedLevels(levels, seeds) { depths ->
					BounceSolver.solveStanding(
						horizontalDistance = length,
						jump = jump, sprint = sprint,
						contactDepth = depths.max(), riseHeight = riseHeight,
						contactPenalty = { contact, reach -> if (contact < levels.size) contactPenalty(reach, levels[contact]) else Double.POSITIVE_INFINITY },
						headroom = headroom,
						launchReach = launchReach,
						reboundHeadroom = reboundHeadroom,
						contactDepths = depths,
					)
				}?.takeIf(::admissible)?.let { return it }
			}

			for ((jump, sprint, holdForward, holdTicks) in COMBOS) {
				refinedLevels(levels, seeds) { depths ->
					BounceSolver.solve(
						horizontalDistance = length,
						sprint = sprint, holdForward = holdForward,
						jump = jump, holdTicks = holdTicks,
						contactDepth = depths.max(), riseHeight = riseHeight,
						headroom = headroom,
						launchReach = launchReach,
						reboundHeadroom = reboundHeadroom,
						contactDepths = depths,
					)
				}?.takeIf(::admissible)?.let { return it }
			}
			return null
		}

		private inline fun refinedLevels(levels: IntArray, seeds: DoubleArray, solveAtDepths: (DoubleArray) -> BounceSolution?): BounceSolution? {
			val offsets = seeds.copyOf()
			repeat(CONTACT_REFINEMENTS) {
				val depths = DoubleArray(levels.size) { launchHeight - (levels[it] + 1 + offsets[it]) }
				if (depths.any { it <= 0.0 }) return null
				val solution = solveAtDepths(depths) ?: return null
				val distances = solution.contactDistances
				if (distances.size != levels.size) return null
				var converged = true
				for (contact in levels.indices) {
					val observed = padSurfaceOffset(solution.launchOffset + distances[contact], levels[contact])
					if (abs(observed - offsets[contact]) >= CONTACT_CONVERGENCE) converged = false
					offsets[contact] = observed
				}
				if (converged) return solution
			}
			return null
		}

		private fun climb(headroom: Double, launchReach: Double, arcs: ArcSweep, seed: Double): BounceSolution? {
			val ledge = lowerLedge() ?: return null
			val wall = ledge.along - launchReach - LEDGE_STEP
			if (wall <= 0.0) return null
			for ((jump, sprint) in STANDING_STYLES) {
				refined(seed) { contactDepth ->
					BounceSolver.solvePressing(
						horizontalDistance = ledge.along + WALL_PRESS_BLOCKS,
						contactDepth = contactDepth, riseHeight = ledge.height - launchHeight,
						sprint = sprint, jump = jump,
						headroom = headroom,
						launchReach = launchReach,
						bounces = bounces,
						reboundHeadroom = reboundHeadroom,
					)
				}?.pressedAgainst(wall, ledge.along - launchReach)?.takeIf { admissible(it, arcs) }?.let { return it }
			}
			return null
		}

		private fun lowerLedge(): Ledge? {
			val target = Stance(from.x + dx, from.y + rise, from.z + dz)
			if (!Footing.involvesPartial(view, target.x, target.y, target.z)) return null
			Footing.forEachRead(target) { x, y, z -> reads?.add(BlockPos.asLong(x, y, z)) }
			val holds = Footing.holds(view, target)
			if (holds.isEmpty) return null
			val surface = target.y + view.surfaceOffset(target.x, target.y - 1, target.z)
			var along = length - LEDGE_SEARCH_BLOCKS
			while (along <= length) {
				val height = holds.heightAt(from.x + 0.5 + unitX * along, from.z + 0.5 + unitZ * along)
				if (!height.isNaN()) return if (height < surface - LEDGE_EPSILON) Ledge(along, height) else null
				along += LEDGE_STEP
			}
			return null
		}

		private fun BounceSolution.pressedAgainst(wall: Double, landing: Double): BounceSolution {
			val distances = DoubleArray(arc.distances.size) { minOf(arc.distances[it], wall) }
			distances[distances.size - 1] = landing
			return copy(arc = ArcSample(arc.heights, distances, arc.exitSpeed, arc.contacts), pressesWall = true)
		}

		private inline fun refined(seed: Double, solveAtDepth: (Double) -> BounceSolution?): BounceSolution? {
			var contactOffset = seed
			repeat(CONTACT_REFINEMENTS) {
				val solution = solveAtDepth(drop + launchOffset - contactOffset) ?: return null
				val observed = padSurfaceOffset(solution.launchOffset + solution.contactDistance)
				if (abs(observed - contactOffset) < CONTACT_CONVERGENCE) {
					val level = solution.contactDistances.all { abs(padSurfaceOffset(solution.launchOffset + it) - observed) < CONTACT_CONVERGENCE }
					return solution.takeIf { level }
				}
				contactOffset = observed
			}
			return null
		}

		private fun admissible(solution: BounceSolution, arcs: ArcSweep): Boolean =
			solution.contactDistances.all { contactPenalty(solution.launchOffset + it).isFinite() } &&
					arcs.sweep(solution.arc, solution.launchOffset, launchHeight) == Sweep.Clear

		private fun crossesPad(level: Int = padY): Boolean {
			var along = 1.0
			while (along < length + 0.5) {
				if (bouncyCell(cellX(along), cellZ(along), level)) return true
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

		private fun seedContactOffset(level: Int = padY): Double {
			var along = 1.0
			while (along < length) {
				val x = cellX(along)
				val z = cellZ(along)
				reads?.add(BlockPos.asLong(x, level, z))
				if (view.voxel(x, level, z).standingSurface != null) return view.surfaceOffset(x, level, z)
				along += 1.0
			}
			return 0.0
		}

		private fun padSurfaceOffset(along: Double, level: Int = padY): Double {
			val x = cellX(along)
			val z = cellZ(along)
			reads?.add(BlockPos.asLong(x, level, z))
			return view.surfaceOffset(x, level, z)
		}

		private fun contactPenalty(reach: Double, level: Int = padY): Double {
			val px = from.x + 0.5 + unitX * reach
			val pz = from.z + 0.5 + unitZ * reach
			val x = floor(px).toInt()
			val z = floor(pz).toInt()
			if (!bouncyCell(x, z, level)) return Double.POSITIVE_INFINITY
			return hypot(px - (x + 0.5), pz - (z + 0.5))
		}

		private fun bouncyCell(x: Int, z: Int, level: Int = padY): Boolean {
			reads?.add(BlockPos.asLong(x, level, z))
			val pad = view.voxel(x, level, z)
			if (pad.bouncy) return true
			val surface = pad.standingSurface
			if (surface != null && surface <= THIN_COVER_SURFACE) {
				reads?.add(BlockPos.asLong(x, level - 1, z))
				if (view.voxel(x, level - 1, z).bouncy) return true
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

	private class Ledge(val along: Double, val height: Double)

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

	private const val LEDGE_SEARCH_BLOCKS = 1.0

	private const val LEDGE_STEP = 1.0 / 16.0

	private const val LEDGE_EPSILON = 1.0E-6

	private const val WALL_PRESS_BLOCKS = 0.5
}
