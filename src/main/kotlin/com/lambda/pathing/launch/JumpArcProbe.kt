package com.lambda.pathing.launch

import com.lambda.pathing.core.DEGENERATE_LENGTH
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.Via
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import com.lambda.pathing.world.Footing
import com.lambda.pathing.world.Medium
import it.unimi.dsi.fastutil.doubles.DoubleArrayList
import it.unimi.dsi.fastutil.longs.LongArrayList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sqrt

object JumpArcProbe {

	private data class SolveKey(
		val dx: Int,
		val dz: Int,
		val riseBits: Long,
		val modes: List<LaunchMode>,
		val headroomBits: Long,
		val everyLaunch: Boolean,
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
			val side = (via.z * unitX - via.x * unitZ).sign
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
			val side = lateral(cellX + 0.5, cellZ + 0.5).sign
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
		headroom: Double = Double.POSITIVE_INFINITY,
		everyLaunch: Boolean = false,
	): List<Candidate> {
		val key = SolveKey(dx, dz, riseHeight.toRawBits(), modes, headroom.toRawBits(), everyLaunch)
		solutionCache[key]?.let { return it }
		if (solutionCache.size > SOLUTION_CACHE_LIMIT) solutionCache.clear()
		return solutionCache.computeIfAbsent(key) {
			val solvable = if (headroom.isFinite()) modes.filter { it.jumps } else modes
			LaunchSolver.solve(from, from.offset(dx, 0, dz), solvable, rise = riseHeight, headroom = headroom, everyLaunch = everyLaunch).map { Candidate(it, dx, dz, modes, riseHeight) }
		}
	}

	private class Region(val columns: LongArray) {
		private val members = LongOpenHashSet(columns)

		operator fun contains(column: Long): Boolean = column in members
	}

	private val regions = ConcurrentHashMap<Long, Region>()

	private fun region(dx: Int, dz: Int): Region = regions.computeIfAbsent(BlockPos.asLong(dx, 0, dz)) {
		val length = hypot(dx.toDouble(), dz.toDouble())
		val reach = length + LANDING_OVERSHOOT
		val samples = ceil(reach * CEILING_SAMPLES_PER_BLOCK).toInt()
		val columns = LinkedHashSet<Long>()
		for (sample in 0..samples) {
			val along = reach * sample / samples
			forEachColumn(0.5 + dx / length * along, 0.5 + dz / length * along) { x, z -> columns += BlockPos.asLong(x, 0, z) }
		}
		Region(columns.toLongArray())
	}

	private fun ceilingLevels(view: CoarseVoxelView, from: Stance, dx: Int, dz: Int, launchHeight: Double, reads: LongOpenHashSet?): DoubleArray {
		val head = launchHeight + BODY_HEIGHT
		val levels = DoubleArrayList()
		for (column in region(dx, dz).columns) {
			forEachCeiling(view, from.x + BlockPos.unpackLongX(column), from.z + BlockPos.unpackLongZ(column), head, reads) { bottom, _, _, _, _ ->
				levels.add(quantized(bottom - head))
			}
		}
		if (levels.size < 2) return levels.toDoubleArray()
		val sorted = levels.toDoubleArray().also { it.sort() }
		var count = 1
		for (index in 1 until sorted.size) if (sorted[index].compareTo(sorted[count - 1]) != 0) sorted[count++] = sorted[index]
		return sorted.copyOf(count)
	}

	private fun bonks(view: CoarseVoxelView, from: Stance, dx: Int, dz: Int, solution: LaunchSolution, launchHeight: Double, reads: LongOpenHashSet?, lateral: Double = 0.0): Boolean {
		val (x, z) = bonkPoint(dx, dz, solution, lateral) ?: return false
		val region = region(dx, dz)
		val head = launchHeight + BODY_HEIGHT
		var bonked = false
		forEachColumn(x, z) { cellX, cellZ ->
			if (BlockPos.asLong(cellX, 0, cellZ) !in region) return@forEachColumn
			forEachCeiling(view, from.x + cellX, from.z + cellZ, head, reads) { bottom, minX, maxX, minZ, maxZ ->
				val over = cellX + maxX > x - BODY_HALF_WIDTH && cellX + minX < x + BODY_HALF_WIDTH &&
					cellZ + maxZ > z - BODY_HALF_WIDTH && cellZ + minZ < z + BODY_HALF_WIDTH
				if (over && quantized(bottom - head) == solution.headroom) bonked = true
			}
		}
		return bonked
	}

	private fun quantized(headroom: Double): Double = floor(headroom * HEADROOM_STEPS) / HEADROOM_STEPS

	private fun bonkPoint(dx: Int, dz: Int, solution: LaunchSolution, lateral: Double = 0.0): Pair<Double, Double>? {
		val arc = solution.arc
		val tick = arc.heights.indexOfFirst { it >= solution.headroom }
		if (tick < 0) return null
		val along = solution.launchOffset + if (tick == 0) 0.0 else arc.distances[tick - 1]
		val length = hypot(dx.toDouble(), dz.toDouble())
		val unitX = dx / length
		val unitZ = dz / length
		return 0.5 + unitX * along - unitZ * lateral to 0.5 + unitZ * along + unitX * lateral
	}

	private inline fun forEachColumn(x: Double, z: Double, visit: (Int, Int) -> Unit) {
		for (cellX in floor(x - BODY_HALF_WIDTH).toInt() until ceil(x + BODY_HALF_WIDTH).toInt()) {
			for (cellZ in floor(z - BODY_HALF_WIDTH).toInt() until ceil(z + BODY_HALF_WIDTH).toInt()) visit(cellX, cellZ)
		}
	}

	private inline fun forEachCeiling(
		view: CoarseVoxelView,
		cellX: Int,
		cellZ: Int,
		head: Double,
		reads: LongOpenHashSet?,
		found: (bottom: Double, minX: Double, maxX: Double, minZ: Double, maxZ: Double) -> Unit,
	) {
		for (cellY in floor(head).toInt()..floor(head + FREE_APEX).toInt()) {
			reads?.add(BlockPos.asLong(cellX, cellY, cellZ))
			when (view.collisionClass(cellX, cellY, cellZ)) {
				CollisionClass.FULL -> if (view.voxel(cellX, cellY, cellZ).medium == Medium.SOLID && cellY >= head && cellY < head + FREE_APEX) found(cellY.toDouble(), 0.0, 1.0, 0.0, 1.0)
				CollisionClass.PARTIAL -> {
					val boxes = view.collisionBoxes(cellX, cellY, cellZ) ?: continue
					for (box in 0 until boxes.size / 6) {
						val b = 6 * box
						val bottom = cellY + boxes[b + 1]
						if (bottom >= head && bottom < head + FREE_APEX) found(bottom, boxes[b], boxes[b + 3], boxes[b + 2], boxes[b + 5])
					}
				}
				else -> {}
			}
		}
	}

	private class SweepPlan(val cells: LongArray, val intersectsFull: BooleanArray) {
		fun nearestFirst(x: Double, z: Double): SweepPlan {
			fun distance(index: Int) = hypot(BlockPos.unpackLongX(cells[index]) + 0.5 - x, BlockPos.unpackLongZ(cells[index]) + 0.5 - z)
			val order = cells.indices.sortedBy(::distance)
			return SweepPlan(LongArray(cells.size) { cells[order[it]] }, BooleanArray(cells.size) { intersectsFull[order[it]] })
		}
	}

	internal class Footprint(
		val solution: LaunchSolution,
		val cells: List<VoxelPos>,
		val clipCells: List<VoxelPos>,
		val bonkCells: List<VoxelPos> = emptyList(),
	)

	internal fun footprints(dx: Int, dz: Int, rise: Double, modes: List<LaunchMode>): List<Footprint> =
		candidates(Stance(0, 0, 0), dx, dz, modes, rise).map { footprintOf(it.solution, it.plan) }

	internal fun alternativeFootprints(dx: Int, dz: Int, rise: Double, modes: List<LaunchMode>): List<Footprint> =
		alternatives(Stance(0, 0, 0), dx, dz, modes, rise).map { footprintOf(it.solution, it.plan) }

	private fun alternatives(from: Stance, dx: Int, dz: Int, modes: List<LaunchMode>, riseHeight: Double): List<Candidate> {
		val free = candidates(from, dx, dz, modes, riseHeight)
		return candidates(from, dx, dz, modes, riseHeight, everyLaunch = true).filter { candidate ->
			candidate.solution.landingSlack >= LaunchSolver.LANDING_SAFETY_BLOCKS && free.none { it.solution == candidate.solution }
		}
	}

	internal fun cappedFootprints(dx: Int, dz: Int, rise: Double, modes: List<LaunchMode>): List<Footprint> {
		if (modes.none { it.jumps }) return emptyList()
		val region = region(dx, dz)
		val rows = floor(BODY_HEIGHT).toInt()..floor(BODY_HEIGHT + FREE_APEX).toInt()
		val regionCells = region.columns.flatMap { column -> rows.map { VoxelPos(BlockPos.unpackLongX(column), it, BlockPos.unpackLongZ(column)) } }
		return rows.filter { it >= BODY_HEIGHT && it < BODY_HEIGHT + FREE_APEX }.flatMap { row ->
			candidates(Stance(0, 0, 0), dx, dz, modes, rise, quantized(row - BODY_HEIGHT)).mapNotNull { candidate ->
				val (x, z) = bonkPoint(dx, dz, candidate.solution) ?: return@mapNotNull null
				val bonks = ArrayList<VoxelPos>()
				forEachColumn(x, z) { cellX, cellZ -> if (BlockPos.asLong(cellX, 0, cellZ) in region) bonks += VoxelPos(cellX, row, cellZ) }
				val swept = footprintOf(candidate.solution, candidate.plan)
				Footprint(candidate.solution, (swept.cells + regionCells).distinct(), swept.clipCells, bonks)
			}
		}
	}

	internal fun bendFootprints(dx: Int, dz: Int, rise: Double, modes: List<LaunchMode>, cellX: Int, cellZ: Int): List<Footprint> =
		candidates(Stance(0, 0, 0), dx, dz, modes, rise).firstOrNull()?.bends(cellX, cellZ)?.map { footprintOf(it.solution, it.plan) }.orEmpty()

	private fun footprintOf(solution: LaunchSolution, plan: SweepPlan): Footprint {
		val cells = plan.cells.map { VoxelPos(BlockPos.unpackLongX(it), BlockPos.unpackLongY(it), BlockPos.unpackLongZ(it)) }
		return Footprint(solution, cells, cells.filterIndexed { index, _ -> plan.intersectsFull[index] })
	}

	fun probe(
		view: CoarseVoxelView,
		from: Stance,
		dx: Int,
		dz: Int,
		riseHeight: Double,
		modes: List<LaunchMode> = LaunchMode.entries,
		launchHeight: Double = from.y.toDouble(),
		everyLaunch: Boolean = true,
	): Probed<LaunchSolution>? = probed { reads -> solve(view, from, dx, dz, modes, riseHeight, launchHeight, everyLaunch, reads) }

	private fun solve(
		view: CoarseVoxelView,
		from: Stance,
		dx: Int,
		dz: Int,
		modes: List<LaunchMode>,
		riseHeight: Double,
		launchHeight: Double,
		everyLaunch: Boolean,
		reads: LongOpenHashSet?,
	): LaunchSolution? {
		val arcs by lazy(LazyThreadSafetyMode.NONE) { ArcSweep(view, from, dx, dz, reads) }
		val planar = launchHeight == from.y.toDouble()
		var clipped: Candidate? = null
		var clip: Sweep.Clipped? = null

		fun sweep(candidate: Candidate): Sweep {
			val solution = candidate.solution
			if (!planar) return arcs.sweep(solution.arc, solution.launchOffset, launchHeight, halfWidth = candidate.halfWidth)
			return sweepByPlan(view, from, candidate.plan, reads) { arcs.sweep(solution.arc, solution.launchOffset, launchHeight, halfWidth = candidate.halfWidth) }
		}

		val free = candidates(from, dx, dz, modes, riseHeight)
		for (candidate in free) {
			val solution = candidate.solution
			when (val sweep = sweep(candidate)) {
				Sweep.Clear -> return solution
				Sweep.Blocked -> {}
				Sweep.Partial -> dodge(solution, launchHeight, arcs)?.let { return it }
				is Sweep.Clipped -> if (clipped == null) {
					clipped = candidate
					clip = sweep
				}
			}
		}
		if (free.isNotEmpty() && modes.any { it.jumps }) {
			for (headroom in ceilingLevels(view, from, dx, dz, launchHeight, reads)) {
				for (candidate in candidates(from, dx, dz, modes, riseHeight, headroom)) {
					if (sweep(candidate) == Sweep.Clear && bonks(view, from, dx, dz, candidate.solution, launchHeight, reads)) return candidate.solution
				}
			}
		}
		if (everyLaunch) for (candidate in alternatives(from, dx, dz, modes, riseHeight)) {
			when (sweep(candidate)) {
				Sweep.Clear -> return candidate.solution
				Sweep.Partial -> dodge(candidate.solution, launchHeight, arcs)?.let { return it }
				else -> {}
			}
		}
		if (clipped == null || clip == null) return null
		val bend = clipped.bends(clip.x - from.x, clip.z - from.z).firstOrNull { bend ->
			val sweep = if (planar) {
				sweepByPlan(view, from, bend.plan, reads) { arcs.sweep(bend.solution.arc, bend.path.offset(from.x.toDouble(), from.z.toDouble()), launchHeight, clipped.halfWidth) }
			} else {
				arcs.sweep(bend.solution.arc, bend.path.offset(from.x.toDouble(), from.z.toDouble()), launchHeight, clipped.halfWidth)
			}
			sweep == Sweep.Clear
		} ?: return null
		return bend.solution
	}

	fun ledge(
		view: CoarseVoxelView,
		from: Stance,
		to: Stance,
		modes: List<LaunchMode>,
		admits: (Double) -> Boolean,
	): Probed<LaunchSolution>? = probed { reads -> solveLedge(view, from, to, modes, admits, reads) }

	private fun solveLedge(
		view: CoarseVoxelView,
		from: Stance,
		to: Stance,
		modes: List<LaunchMode>,
		admits: (Double) -> Boolean,
		reads: LongOpenHashSet?,
	): LaunchSolution? {
		if (reads != null) for (stance in listOf(from, to)) Footing.forEachRead(stance) { x, y, z -> reads.add(BlockPos.asLong(x, y, z)) }
		val takeoff = Footing.holds(view, from)
		val landing = Footing.holds(view, to)
		if (takeoff.isEmpty || landing.isEmpty) return null
		val dx = to.x - from.x
		val dz = to.z - from.z
		val length = hypot(dx.toDouble(), dz.toDouble())
		val unitX = dx / length
		val unitZ = dz / length
		val arcs = ArcSweep(view, from, dx, dz, reads)
		val culprits = DoubleArrayList()
		for (lateral in LEDGE_LATERALS) {
			fun feet(holds: Footing.Holds, along: Double) =
				holds.heightAt(from.x + 0.5 + unitX * along - unitZ * lateral, from.z + 0.5 + unitZ * along + unitX * lateral)
			val launchFeet = feet(takeoff, 0.0)
			if (launchFeet.isNaN()) continue
			val landingFeet = feet(landing, length)
			if (landingFeet.isNaN()) continue
			val rise = landingFeet - launchFeet
			if (!admits(rise)) continue
			var near = length
			while (!feet(landing, near - LEDGE_STEP).isNaN()) near -= LEDGE_STEP
			var far = length
			while (!feet(landing, far + LEDGE_STEP).isNaN()) far += LEDGE_STEP
			var launchable = 0
			for ((index, offset) in LaunchSolver.LAUNCH_OFFSETS.withIndex()) if (!feet(takeoff, offset).isNaN()) launchable = launchable or (1 shl index)
			if (launchable == 0) continue
			val headrooms = doubleArrayOf(Double.POSITIVE_INFINITY) + ceilingLevels(view, from, dx, dz, launchFeet, reads)
			for (headroom in headrooms) {
				for (solution in ledgeSolutions(LedgeKey(dx, dz, modes, rise.toRawBits(), near.toRawBits(), far.toRawBits(), launchable, headroom.toRawBits()))) {
					val path = ArcPath(
						from.x + 0.5 + unitX * solution.launchOffset - unitZ * lateral,
						from.z + 0.5 + unitZ * solution.launchOffset + unitX * lateral,
						unitX, unitZ,
					)
					if (arcs.hitsAny(solution.arc, path, launchFeet, BODY_HALF_WIDTH, culprits)) continue
					if (arcs.sweep(solution.arc, path, launchFeet, BODY_HALF_WIDTH, culprits) != Sweep.Clear) continue
					if (headroom.isFinite() && !bonks(view, from, dx, dz, solution, launchFeet, reads, lateral)) continue
					return solution.copy(lateralOffset = lateral)
				}
			}
		}
		return null
	}

	private data class LedgeKey(val dx: Int, val dz: Int, val modes: List<LaunchMode>, val riseBits: Long, val nearBits: Long, val farBits: Long, val launchable: Int, val headroomBits: Long)

	private val ledgeSolutionCache = ConcurrentHashMap<LedgeKey, List<LaunchSolution>>()

	private fun ledgeSolutions(key: LedgeKey): List<LaunchSolution> {
		ledgeSolutionCache[key]?.let { return it }
		if (ledgeSolutionCache.size > SOLUTION_CACHE_LIMIT) ledgeSolutionCache.clear()
		return ledgeSolutionCache.computeIfAbsent(key) {
			val origin = Stance(0, 0, 0)
			LaunchSolver.solve(
				origin, origin.offset(key.dx, 0, key.dz), key.modes,
				rise = Double.fromBits(key.riseBits),
				headroom = Double.fromBits(key.headroomBits),
				landingRange = Double.fromBits(key.nearBits)..Double.fromBits(key.farBits),
				launchable = { offset -> LaunchSolver.LAUNCH_OFFSETS.indexOf(offset).let { it >= 0 && key.launchable and (1 shl it) != 0 } },
			)
		}
	}

	private fun dodge(solution: LaunchSolution, launchHeight: Double, arcs: ArcSweep): LaunchSolution? {
		val slack = solution.lateralSlack
		if (slack < MIN_DODGE_SLACK) return null
		for (fraction in DODGE_FRACTIONS) {
			val offset = fraction * slack
			if (arcs.clearOf(solution.arc, arcs.path(solution.launchOffset, offset), launchHeight, BODY_HALF_WIDTH)) return solution.copy(lateralOffset = offset)
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
				CollisionClass.EMPTY -> if (view.medium(x, y, z) == Medium.CLIMBABLE) return partial()

				CollisionClass.UNKNOWN -> return Sweep.Blocked

				CollisionClass.FULL -> if (plan.intersectsFull[index]) return Sweep.Clipped(x, z)

				CollisionClass.PARTIAL -> return partial()
			}
		}
		return Sweep.Clear
	}

	private fun buildPlan(arc: ArcSample, path: ArcPath, halfWidth: Double): SweepPlan {
		val swept = LongOpenHashSet()
		val intersecting = LongOpenHashSet()
		forEachSweptCell(arc, path, 0.0, halfWidth) { x, y, z, box ->
			val key = BlockPos.asLong(x, y, z)
			swept.add(key)
			if (box.intersects(x.toDouble(), y.toDouble(), z.toDouble(), x + 1.0, y + 1.0, z + 1.0)) intersecting.add(key)
		}
		val order = LongArrayList()
		val seen = LongOpenHashSet()
		forEachSweptBox(arc, path, 0.0, halfWidth) { box ->
			val near = box.expand(APPROACH_REACH)
			for (y in floor(near.minY).toInt()..floor(near.maxY).toInt()) for (z in floor(near.minZ).toInt()..floor(near.maxZ).toInt()) {
				for (x in floor(near.minX).toInt()..floor(near.maxX).toInt()) {
					val key = BlockPos.asLong(x, y, z)
					if (key in swept && seen.add(key)) order.add(key)
				}
			}
		}
		val cells = order.toLongArray()
		return SweepPlan(cells, BooleanArray(cells.size) { cells[it] in intersecting })
	}

	private const val MIN_DODGE_SLACK = 0.05

	private val DODGE_FRACTIONS = doubleArrayOf(0.5, -0.5, 1.0, -1.0)

	private const val SOLUTION_CACHE_LIMIT = 100_000

	private const val MIN_TURN_COSINE = 0.5

	private val LANDING_SHIFTS = listOf(0.0, 0.5, 1.0)

	private val FREE_APEX = BallisticProfile.VANILLA.jumpApex

	private val LANDING_OVERSHOOT = (0.5 + BODY_HALF_WIDTH) * sqrt(2.0)

	private const val HEADROOM_STEPS = 32.0

	private const val LEDGE_STEP = 1.0 / 16

	private val LEDGE_LATERALS = (0..13).flatMap { listOf(it * LEDGE_STEP, -it * LEDGE_STEP) }.distinct()

	private const val CEILING_SAMPLES_PER_BLOCK = 4

	private const val APPROACH_REACH = 0.5

}
