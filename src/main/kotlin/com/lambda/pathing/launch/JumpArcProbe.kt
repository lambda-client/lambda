package com.lambda.pathing.launch

import com.lambda.pathing.core.DEGENERATE_LENGTH
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.Via
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.hypot

object JumpArcProbe {

	private data class SolveKey(
		val dx: Int,
		val dz: Int,
		val riseBits: Long,
		val modes: List<LaunchMode>,
	)

	private class Candidate(
		val solution: LaunchSolution,
		private val dx: Int,
		private val dz: Int,
		private val modes: List<LaunchMode>,
		private val rise: Double,
	) {
		private val length = hypot(dx.toDouble(), dz.toDouble())
		private val unitX = dx / length
		private val unitZ = dz / length

		val halfWidth = if (abs(dx) == 1 && abs(dz) == 1) BODY_HALF_WIDTH else CORE_HALF_WIDTH

		val plan: SweepPlan by lazy(LazyThreadSafetyMode.PUBLICATION) {
			buildPlan(solution.arc, ArcPath(0.5 + unitX * solution.launchOffset, 0.5 + unitZ * solution.launchOffset, unitX, unitZ), halfWidth)
		}

		private val bends = ConcurrentHashMap<Long, List<Bend>>()

		fun bends(cellX: Int, cellZ: Int): List<Bend> =
			bends.computeIfAbsent(BlockPos.asLong(cellX, 0, cellZ)) { bendsAround(cellX, cellZ) }

		private fun bendsAround(cellX: Int, cellZ: Int): List<Bend> {
			val via = viaAround(cellX, cellZ) ?: return emptyList()
			val side = Math.signum(via.z * unitX - via.x * unitZ)
			return LANDING_SHIFTS.mapNotNull { fraction -> bendTo(via, side * fraction * solution.lateralSlack) }
		}

		private fun bendTo(via: Via, landingShift: Double): Bend? {
			val toX = 0.5 + dx - unitZ * landingShift
			val toZ = 0.5 + dz + unitX * landingShift
			val turnX = toX - 0.5 - via.x
			val turnZ = toZ - 0.5 - via.z
			if ((via.x * turnX + via.z * turnZ) / (hypot(via.x, via.z) * hypot(turnX, turnZ)) < MIN_TURN_COSINE) return null
			val solution = LaunchSolver.solve(Stance(0, 0, 0), Stance(dx, 0, dz), modes, rise = rise, via = via, landingShift = landingShift)
				.firstOrNull() ?: return null
			val path = ArcPath.through(0.5, 0.5, 0.5 + via.x, 0.5 + via.z, toX, toZ, solution.launchOffset) ?: return null
			return Bend(solution, path, buildPlan(solution.arc, path, halfWidth).nearestFirst(0.5 + via.x, 0.5 + via.z))
		}

		private fun viaAround(cellX: Int, cellZ: Int): Via? {
			fun lateral(x: Double, z: Double) = (z - 0.5) * unitX - (x - 0.5) * unitZ
			val side = Math.signum(lateral(cellX + 0.5, cellZ + 0.5))
			if (side == 0.0) return null
			var cornerX = cellX
			var cornerZ = cellZ
			var nearest = Double.POSITIVE_INFINITY
			for (x in cellX..cellX + 1) for (z in cellZ..cellZ + 1) {
				val reach = side * lateral(x.toDouble(), z.toDouble())
				if (reach < nearest) {
					nearest = reach
					cornerX = x
					cornerZ = z
				}
			}
			if (nearest < -DEGENERATE_LENGTH) return null
			val viaX = cornerX + (if (cornerX == cellX) -CORNER_CLEARANCE else CORNER_CLEARANCE) - 0.5
			val viaZ = cornerZ + (if (cornerZ == cellZ) -CORNER_CLEARANCE else CORNER_CLEARANCE) - 0.5
			val along = viaX * unitX + viaZ * unitZ
			return if (along > 0.0 && along < length) Via(viaX, viaZ) else null
		}
	}

	private class Bend(val solution: LaunchSolution, val path: ArcPath, val plan: SweepPlan)

	private val solutionCache = ConcurrentHashMap<SolveKey, List<Candidate>>()

	private fun candidates(
		from: Stance,
		dx: Int,
		dz: Int,
		modes: List<LaunchMode>,
		riseHeight: Double,
	): List<Candidate> {
		val key = SolveKey(dx, dz, riseHeight.toRawBits(), modes)
		solutionCache[key]?.let { return it }
		if (solutionCache.size > SOLUTION_CACHE_LIMIT) solutionCache.clear()
		return solutionCache.computeIfAbsent(key) {
			LaunchSolver.solve(from, from.offset(dx, 0, dz), modes, rise = riseHeight).map { Candidate(it, dx, dz, modes, riseHeight) }
		}
	}

	private class SweepPlan(val cells: LongArray, val intersectsFull: BooleanArray) {
		fun nearestFirst(x: Double, z: Double): SweepPlan {
			fun distance(index: Int) = hypot(BlockPos.unpackLongX(cells[index]) + 0.5 - x, BlockPos.unpackLongZ(cells[index]) + 0.5 - z)
			val order = cells.indices.sortedBy(::distance)
			return SweepPlan(LongArray(cells.size) { cells[order[it]] }, BooleanArray(cells.size) { intersectsFull[order[it]] })
		}
	}

	fun probe(
		view: CoarseVoxelView,
		from: Stance,
		dx: Int,
		dz: Int,
		riseHeight: Double,
		modes: List<LaunchMode> = LaunchMode.entries,
		launchHeight: Double = from.y.toDouble(),
	): Probed<LaunchSolution>? = probed { reads -> solve(view, from, dx, dz, modes, riseHeight, launchHeight, reads) }

	private fun solve(
		view: CoarseVoxelView,
		from: Stance,
		dx: Int,
		dz: Int,
		modes: List<LaunchMode>,
		riseHeight: Double,
		launchHeight: Double,
		reads: LongOpenHashSet?,
	): LaunchSolution? {
		val arcs = lazy(LazyThreadSafetyMode.NONE) { ArcSweep(view, from, dx, dz, reads) }
		val planar = launchHeight == from.y.toDouble()
		var clipped: Candidate? = null
		var clip: Sweep.Clipped? = null

		for (candidate in candidates(from, dx, dz, modes, riseHeight)) {
			val solution = candidate.solution
			val sweep = if (planar) {
				sweepByPlan(view, from, candidate.plan, reads) { arcs.value.sweep(solution.arc, solution.launchOffset, launchHeight, halfWidth = candidate.halfWidth) }
			} else {
				arcs.value.sweep(solution.arc, solution.launchOffset, launchHeight, halfWidth = candidate.halfWidth)
			}
			when (sweep) {
				Sweep.Clear -> return solution
				Sweep.Blocked -> {}
				Sweep.Partial -> dodge(solution, launchHeight, arcs.value)?.let { return it }
				is Sweep.Clipped -> if (clipped == null) {
					clipped = candidate
					clip = sweep
				}
			}
		}
		if (clipped == null || clip == null) return null
		val bend = clipped.bends(clip.x - from.x, clip.z - from.z).firstOrNull { bend ->
			val sweep = if (planar) {
				sweepByPlan(view, from, bend.plan, reads) { arcs.value.sweep(bend.solution.arc, bend.path.offset(from.x.toDouble(), from.z.toDouble()), launchHeight, clipped.halfWidth) }
			} else {
				arcs.value.sweep(bend.solution.arc, bend.path.offset(from.x.toDouble(), from.z.toDouble()), launchHeight, clipped.halfWidth)
			}
			sweep == Sweep.Clear
		} ?: return null
		return bend.solution
	}

	private fun dodge(solution: LaunchSolution, launchHeight: Double, arcs: ArcSweep): LaunchSolution? {
		val slack = solution.lateralSlack
		if (slack < MIN_DODGE_SLACK) return null
		for (fraction in DODGE_FRACTIONS) {
			val offset = fraction * slack
			val dodged = arcs.sweep(
				solution.arc, solution.launchOffset, launchHeight, lateralOffset = offset, halfWidth = BODY_HALF_WIDTH,
			)
			if (dodged == Sweep.Clear) return solution.copy(lateralOffset = offset)
		}
		return null
	}

	private inline fun sweepByPlan(
		view: CoarseVoxelView,
		from: Stance,
		plan: SweepPlan,
		reads: LongOpenHashSet?,
		partial: () -> Sweep,
	): Sweep {
		for (index in plan.cells.indices) {
			val offset = plan.cells[index]
			val x = from.x + BlockPos.unpackLongX(offset)
			val y = from.y + BlockPos.unpackLongY(offset)
			val z = from.z + BlockPos.unpackLongZ(offset)
			reads?.add(BlockPos.asLong(x, y, z))

			when (view.collisionClass(x, y, z)) {
				CollisionClass.EMPTY -> {}

				CollisionClass.UNKNOWN -> return Sweep.Blocked

				CollisionClass.FULL -> if (plan.intersectsFull[index]) return Sweep.Clipped(x, z)

				CollisionClass.PARTIAL -> return partial()
			}
		}
		return Sweep.Clear
	}

	private fun buildPlan(arc: ArcSample, path: ArcPath, halfWidth: Double): SweepPlan {
		val order = LongArrayList()
		val seen = LongOpenHashSet()
		val intersecting = LongOpenHashSet()
		forEachSweptCell(arc, path, 0.0, halfWidth) { x, y, z, swept ->
			val key = BlockPos.asLong(x, y, z)
			if (seen.add(key)) order.add(key)
			if (swept.intersects(x.toDouble(), y.toDouble(), z.toDouble(), x + 1.0, y + 1.0, z + 1.0)) intersecting.add(key)
		}
		val cells = order.toLongArray()
		return SweepPlan(cells, BooleanArray(cells.size) { cells[it] in intersecting })
	}

	private const val MIN_DODGE_SLACK = 0.05

	private val DODGE_FRACTIONS = doubleArrayOf(0.5, -0.5, 1.0, -1.0)

	private const val SOLUTION_CACHE_LIMIT = 100_000

	private const val MIN_TURN_COSINE = 0.5

	private val LANDING_SHIFTS = listOf(0.0, 0.5, 1.0)

}
