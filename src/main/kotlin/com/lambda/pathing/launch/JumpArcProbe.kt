package com.lambda.pathing.launch

import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.hypot

object JumpArcProbe {

	private data class SolveKey(
		val dx: Int,
		val dz: Int,
		val riseBits: Long,
		val modes: List<LaunchMode>,
	)

	private val solutionCache = ConcurrentHashMap<SolveKey, List<LaunchSolution>>()

	private fun solutions(
		from: Stance,
		to: Stance,
		dx: Int,
		dz: Int,
		modes: List<LaunchMode>,
		riseHeight: Double,
	): List<LaunchSolution> {
		if (solutionCache.size > SOLUTION_CACHE_LIMIT) solutionCache.clear()
		return solutionCache.computeIfAbsent(
			SolveKey(dx, dz, riseHeight.toRawBits(), modes)
		) { LaunchSolver.solve(from, to, modes, rise = riseHeight) }
	}

	private class SweepPlan(val cells: LongArray, val intersectsFull: BooleanArray)

	private val planCache = ConcurrentHashMap<ArcSample, SweepPlan>()

	fun probe(
		view: CoarseVoxelView,
		from: Stance,
		dx: Int,
		dz: Int,
		rise: Int,
		modes: List<LaunchMode> = LaunchMode.entries,

		riseHeight: Double = (rise).toDouble(),
		launchHeight: Double = from.y.toDouble(),
	): Probed<LaunchSolution>? = probed { reads -> solve(view, from, dx, dz, rise, modes, riseHeight, launchHeight, reads) }

	private fun solve(
		view: CoarseVoxelView,
		from: Stance,
		dx: Int,
		dz: Int,
		rise: Int,
		modes: List<LaunchMode>,
		riseHeight: Double,
		launchHeight: Double,
		reads: LongOpenHashSet?,
	): LaunchSolution? {
		val to = from.offset(dx, rise, dz)
		val cache = lazy(LazyThreadSafetyMode.NONE) { SweepCellCache() }
		val planar = launchHeight == from.y.toDouble()

		for (solution in solutions(from, to, dx, dz, modes, riseHeight)) {
			val sweep = if (planar) {
				sweepByPlan(view, from, dx, dz, solution, reads, cache)
			} else {
				sweepArc(view, from, dx, dz, solution.arc, solution.launchOffset, launchHeight, reads, cache.value)
			}
			if (sweep == Sweep.CLEAR) return solution

			if (sweep != Sweep.PARTIAL) continue
			val slack = solution.lateralSlack
			if (slack < MIN_DODGE_SLACK) continue
			for (fraction in DODGE_FRACTIONS) {
				val offset = fraction * slack
				val dodged = sweepArc(
					view, from, dx, dz, solution.arc, solution.launchOffset, launchHeight, reads,
					cache.value, lateralOffset = offset, halfWidth = BODY_HALF_WIDTH,
				)
				if (dodged == Sweep.CLEAR) return solution.copy(lateralOffset = offset)
			}
		}
		return null
	}

	private fun sweepByPlan(
		view: CoarseVoxelView,
		from: Stance,
		dx: Int,
		dz: Int,
		solution: LaunchSolution,
		reads: LongOpenHashSet?,
		cache: Lazy<SweepCellCache>,
	): Sweep {
		if (planCache.size > PLAN_CACHE_LIMIT) planCache.clear()
		val plan = planCache.computeIfAbsent(solution.arc) {
			buildPlan(dx, dz, solution.arc, solution.launchOffset)
		}

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

				CollisionClass.PARTIAL ->
					return sweepArc(
						view, from, dx, dz, solution.arc, solution.launchOffset,
						from.y.toDouble(), reads, cache.value,
					)
			}
		}
		return Sweep.CLEAR
	}

	private fun buildPlan(dx: Int, dz: Int, arc: ArcSample, launchOffset: Double): SweepPlan {
		val length = hypot(dx.toDouble(), dz.toDouble())
		val unitX = dx / length
		val unitZ = dz / length
		val launchX = 0.5 + unitX * launchOffset
		val launchZ = 0.5 + unitZ * launchOffset

		val heights = arc.heights
		val distances = arc.distances

		val order = LongArrayList()
		val seen = LongOpenHashSet()
		val intersecting = LongOpenHashSet()

		var previous = coreBox(launchX, 0.0, launchZ)
		for (index in heights.indices) {
			val along = distances[index]
			val box = coreBox(
				launchX + unitX * along,
				heights[index],
				launchZ + unitZ * along,
			)
			val swept = previous.union(box)
			previous = box

			val margin = swept.expand(SWEEP_MARGIN)
			for (y in MathHelper.floor(margin.minY)..MathHelper.floor(margin.maxY)) {
				for (z in MathHelper.floor(margin.minZ)..MathHelper.floor(margin.maxZ)) {
					for (x in MathHelper.floor(margin.minX)..MathHelper.floor(margin.maxX)) {
						val key = BlockPos.asLong(x, y, z)
						if (seen.add(key)) order.add(key)
						if (swept.intersects(x.toDouble(), y.toDouble(), z.toDouble(), x + 1.0, y + 1.0, z + 1.0)) {
							intersecting.add(key)
						}
					}
				}
			}
		}

		val cells = order.toLongArray()
		return SweepPlan(cells, BooleanArray(cells.size) { cells[it] in intersecting })
	}


	private const val MIN_DODGE_SLACK = 0.05


	private val DODGE_FRACTIONS = doubleArrayOf(0.5, -0.5, 1.0, -1.0)

	private const val SOLUTION_CACHE_LIMIT = 100_000
	private const val PLAN_CACHE_LIMIT = 100_000
}
