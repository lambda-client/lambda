package com.lambda.pathing.launch

import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.hypot

object JumpArcProbe {

	private data class SolveKey(
		val dx: Int,
		val dz: Int,
		val riseBits: Long,
		val modes: List<LaunchMode>,
	)

	private class Candidate(val solution: LaunchSolution, dx: Int, dz: Int) {
		val plan: SweepPlan by lazy(LazyThreadSafetyMode.PUBLICATION) {
			buildPlan(dx, dz, solution.arc, solution.launchOffset)
		}
	}

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
			LaunchSolver.solve(from, from.offset(dx, 0, dz), modes, rise = riseHeight).map { Candidate(it, dx, dz) }
		}
	}

	private class SweepPlan(val cells: LongArray, val intersectsFull: BooleanArray)

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

		for (candidate in candidates(from, dx, dz, modes, riseHeight)) {
			val solution = candidate.solution
			val sweep = if (planar) {
				sweepByPlan(view, from, candidate.plan, solution, reads, arcs)
			} else {
				arcs.value.sweep(solution.arc, solution.launchOffset, launchHeight)
			}
			if (sweep == Sweep.CLEAR) return solution

			if (sweep != Sweep.PARTIAL) continue
			val slack = solution.lateralSlack
			if (slack < MIN_DODGE_SLACK) continue
			for (fraction in DODGE_FRACTIONS) {
				val offset = fraction * slack
				val dodged = arcs.value.sweep(
					solution.arc, solution.launchOffset, launchHeight, lateralOffset = offset, halfWidth = BODY_HALF_WIDTH,
				)
				if (dodged == Sweep.CLEAR) return solution.copy(lateralOffset = offset)
			}
		}
		return null
	}

	private fun sweepByPlan(
		view: CoarseVoxelView,
		from: Stance,
		plan: SweepPlan,
		solution: LaunchSolution,
		reads: LongOpenHashSet?,
		arcs: Lazy<ArcSweep>,
	): Sweep {
		for (index in plan.cells.indices) {
			val offset = plan.cells[index]
			val x = from.x + BlockPos.unpackLongX(offset)
			val y = from.y + BlockPos.unpackLongY(offset)
			val z = from.z + BlockPos.unpackLongZ(offset)
			reads?.add(BlockPos.asLong(x, y, z))

			when (view.collisionClass(x, y, z)) {
				CollisionClass.EMPTY -> {}

				CollisionClass.UNKNOWN -> return Sweep.BLOCKED

				CollisionClass.FULL -> if (plan.intersectsFull[index]) return Sweep.BLOCKED

				CollisionClass.PARTIAL -> return arcs.value.sweep(solution.arc, solution.launchOffset, from.y.toDouble())
			}
		}
		return Sweep.CLEAR
	}

	private fun buildPlan(dx: Int, dz: Int, arc: ArcSample, launchOffset: Double): SweepPlan {
		val length = hypot(dx.toDouble(), dz.toDouble())
		val unitX = dx / length
		val unitZ = dz / length
		val order = LongArrayList()
		val seen = LongOpenHashSet()
		val intersecting = LongOpenHashSet()
		forEachSweptCell(arc, 0.5 + unitX * launchOffset, 0.0, 0.5 + unitZ * launchOffset, unitX, unitZ, CORE_HALF_WIDTH) { x, y, z, swept ->
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
}
