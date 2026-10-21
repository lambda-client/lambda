package com.lambda.pathing.actions

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.Via
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.BounceArcProbe
import com.lambda.pathing.launch.BounceSolution
import com.lambda.pathing.launch.JumpArcProbe
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.world.CellClass
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Footing
import com.lambda.pathing.world.Medium
import com.lambda.pathing.world.canStandAt
import com.lambda.pathing.world.edit.Flips
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

class MotionTemplate internal constructor(
	val id: MotionTemplateId,
	val dx: Int,
	val dy: Int,
	val dz: Int,
	val movement: MovementId,
	val lowerBoundTicks: Double,
	private val conditions: List<CellCondition>,
	private val arc: ArcSpec?,
	private val strideCost: Double?,
	private val via: Via?,
	private val maxRise: Double = Double.POSITIVE_INFINITY,
) {
	data class ArcSpec(
		val modes: List<LaunchMode> = LaunchMode.entries,
		val bounceDrop: Int? = null,
		val bounces: Int = 1,
		val riseAdmission: ((Double) -> Boolean)? = null,
		val momentumOnly: Boolean = false,
		val runUpNeeded: ((Double) -> Boolean)? = null,
		val exactOnly: Boolean = false,
	)

	init {
		require(dx != 0 || dy != 0 || dz != 0) { "Motion template cannot be stationary" }
		require(lowerBoundTicks.isFinite() && lowerBoundTicks > 0.0) {
			"Template lower bound must be finite and positive: $lowerBoundTicks"
		}
	}

	private fun target(origin: Stance): Stance = origin.offset(dx, dy, dz)

	internal val minimumTicks: Double = minOf(lowerBoundTicks, strideCost ?: lowerBoundTicks)

	internal val bounceDrop: Int? get() = arc?.bounceDrop

	internal val momentumOnly: Boolean get() = arc?.momentumOnly == true

	private fun surfaceOffset(view: CoarseVoxelView, stance: Stance): Double =
		view.surfaceOffset(stance.x, stance.y - 1, stance.z)

	private fun realRise(view: CoarseVoxelView, origin: Stance): Double =
		dy + surfaceOffset(view, target(origin)) - surfaceOffset(view, origin)

	private fun matches(view: CoarseVoxelView, origin: Stance): Boolean =
		conditions.all { it.matches(view, origin.x, origin.y, origin.z) }

	internal fun edge(view: CoarseVoxelView, origin: Stance): CoarseEdge? {
		val units = Flips.units(view, origin.x, origin.y, origin.z, sweptOffsets, sweptBounds)
		if (units.isEmpty()) return evaluate(view, origin)
		evaluate(view, origin)?.takeIf { clearSweep(view, origin) }?.let { return it }
		val flipped = Flips.search(view, units, pairs = arc == null) { flipped -> evaluate(flipped, origin, ledges = false)?.takeIf { clearSweep(flipped, origin) } }
			?: return null
		val edge = flipped.result
		return edge.copy(lowerBoundTicks = edge.lowerBoundTicks + FLIP_TICKS * flipped.units, readSet = edge.readSet + flipped.states.keys, flips = flipped.states)
	}

	private fun evaluate(view: CoarseVoxelView, origin: Stance, ledges: Boolean = true): CoarseEdge? {
		if (!matches(view, origin)) return if (ledges && undecided(view, origin)) ledgeEdge(view, origin) else null
		val spec = arc ?: return if (realRise(view, origin) > maxRise) null else edgeOf(origin, ticks(view, origin), probeReads = null)
		val launchOffset = surfaceOffset(view, origin)
		spec.bounceDrop?.let { drop ->
			val probed = BounceArcProbe.probe(view, origin, dx, dz, drop, dy, launchHeight = origin.y + launchOffset, bounces = spec.bounces)
				?: return null
			return edgeOf(origin, lowerBoundTicks, probed.reads, bounce = probed.solution)
		}
		val riseHeight = realRise(view, origin)
		spec.riseAdmission?.let { admits -> if (!admits(riseHeight)) return if (ledges) ledgeEdge(view, origin) else null }
		val momentum = spec.runUpNeeded?.invoke(riseHeight) == true
		val probed = JumpArcProbe.probe(view, origin, dx, dz, riseHeight, spec.modes, launchHeight = origin.y + launchOffset, everyLaunch = !momentum)
			?: return if (ledges) ledgeEdge(view, origin) else null
		if (probed.solution.mode.jumps && Footing.involvesPartial(view, origin.x, origin.y, origin.z) && !launchesFromFooting(view, origin, probed.solution)) return if (ledges) ledgeEdge(view, origin) else null
		val detour = probed.solution.via?.detour(dx, dz, probed.solution.lateralOffset) ?: 0.0
		val runUp = if (momentum) standingRunUpTicks(view, origin, probed.solution) ?: return null else null
		return edgeOf(
			origin, lowerBoundTicks + detour / CoarseMoveRates.SPRINT_JUMP_BLOCKS_PER_TICK + (runUp ?: 0), probed.reads,
			launch = probed.solution,
			standingStart = runUp != null || standingStartViable(view, origin, spec.modes, probed.solution, riseHeight),
		)
	}

	private fun standingRunUpTicks(view: CoarseVoxelView, origin: Stance, solution: LaunchSolution): Int? {
		if (Footing.involvesPartial(view, origin.x, origin.y, origin.z) && !view.canStandAt(origin.x - Integer.signum(dx), origin.y, origin.z - Integer.signum(dz))) return null
		val profile = BallisticProfile.VANILLA
		val ticks = profile.groundRunUpTicks(0.0, IN_CELL_RUN_UP, solution.mode.sprint, STANDING_RUN_UP_TICKS)
		return ticks.takeIf { profile.runUpSpeed(0.0, it, solution.mode.sprint) >= solution.speed - solution.speedSlack }
	}

	private fun undecided(view: CoarseVoxelView, origin: Stance): Boolean = conditions.all { condition ->
		condition.matches(view, origin.x, origin.y, origin.z) ||
				condition.reads().any { view.cellClass(origin.x + it.x, origin.y + it.y, origin.z + it.z) == CellClass.SPECIAL }
	}

	private fun launchesFromFooting(view: CoarseVoxelView, origin: Stance, solution: LaunchSolution): Boolean {
		val holds = Footing.holds(view, origin)
		if (holds.isEmpty) return false
		val towardX = solution.via?.x ?: dx.toDouble()
		val towardZ = solution.via?.z ?: dz.toDouble()
		val length = kotlin.math.hypot(towardX, towardZ)
		if (length == 0.0) return true
		val unitX = towardX / length
		val unitZ = towardZ / length
		val x = origin.x + 0.5 + unitX * solution.launchOffset - unitZ * solution.lateralOffset
		val z = origin.z + 0.5 + unitZ * solution.launchOffset + unitX * solution.lateralOffset
		return !holds.heightAt(x, z).isNaN()
	}

	private fun ledgeEdge(view: CoarseVoxelView, origin: Stance): CoarseEdge? {
		val spec = arc?.takeIf { it.bounceDrop == null } ?: return null
		val target = target(origin)
		if (!Footing.involvesPartial(view, origin.x, origin.y, origin.z) && !Footing.involvesPartial(view, target.x, target.y, target.z)) return null
		val probed = JumpArcProbe.ledge(view, origin, target, spec.modes, spec.riseAdmission ?: { true }) ?: return null
		val runUp = if (spec.runUpNeeded?.invoke(realRise(view, origin)) == true) standingRunUpTicks(view, origin, probed.solution) ?: return null else null
		return edgeOf(
			origin, lowerBoundTicks + (runUp ?: 0), probed.reads,
			launch = probed.solution,
			standingStart = runUp != null || standingStartViable(view, origin, spec.modes, probed.solution, dy.toDouble()),
		)
	}

	private fun ticks(view: CoarseVoxelView, origin: Stance): Double =
		if (strideCost != null && realRise(view, origin) <= CoarseMoveRates.FREE_STEP_RISE) strideCost else lowerBoundTicks

	private fun edgeOf(
		origin: Stance,
		ticks: Double,
		probeReads: (() -> LongOpenHashSet)?,
		launch: LaunchSolution? = null,
		bounce: BounceSolution? = null,
		standingStart: Boolean = true,
	) = CoarseEdge(
		template = id,
		from = origin,
		to = target(origin),
		movement = movement,
		lowerBoundTicks = ticks,
		readSet = LazyReadSet {
			buildSet {
				readOffsets.forEach { add(VoxelPos(origin.x + it.x, origin.y + it.y, origin.z + it.z)) }
				probeReads?.let { reads ->
					val iterator = reads().iterator()
					while (iterator.hasNext()) {
						val key = iterator.nextLong()
						add(VoxelPos(BlockPos.unpackLongX(key), BlockPos.unpackLongY(key), BlockPos.unpackLongZ(key)))
					}
				}
			}
		},
		launch = launch,
		bounce = bounce,
		standingStart = standingStart,
		via = via ?: launch?.via,
	)

	private fun standingStartViable(
		view: CoarseVoxelView,
		origin: Stance,
		modes: List<LaunchMode>,
		solution: LaunchSolution,
		rise: Double,
	): Boolean = standingStartViable(
		view.canStandAt(origin.x - Integer.signum(dx), origin.y, origin.z - Integer.signum(dz)), origin, modes, solution, rise,
	)

	private fun standingStartViable(
		behind: Boolean,
		origin: Stance,
		modes: List<LaunchMode>,
		solution: LaunchSolution,
		rise: Double,
	): Boolean {
		if (arc?.momentumOnly == true) return false
		val profile = BallisticProfile.VANILLA
		val runUp = IN_CELL_RUN_UP + if (behind) 1.0 else 0.0
		fun reach(sprint: Boolean): Double =
			profile.runUpSpeed(0.0, profile.groundRunUpTicks(0.0, runUp, sprint, STANDING_RUN_UP_TICKS), sprint)

		if (solution.speed - solution.speedSlack <= reach(solution.mode.sprint)) return true
		return LaunchSolver.solve(origin, target(origin), modes, maxEntrySpeed = { reach(it.sprint) }, rise = rise, headroom = solution.headroom).isNotEmpty()
	}

	internal val walkTicks: Double = if (strideCost != null && dy <= CoarseMoveRates.FREE_STEP_RISE) strideCost else lowerBoundTicks

	internal val stampedTicks: Double = if (arc == null) walkTicks else lowerBoundTicks

	internal fun stampProgram(): StampProgram? {
		val spec = arc
		if (spec?.bounceDrop != null || spec?.exactOnly == true) return null
		val clauses = conditions.map { condition ->
			StampProgram.Clause.of(condition.reads().distinct()) { condition.matches(it, 0, 0, 0) } ?: return null
		}
		val after = LinkedHashSet<VoxelPos>()
		val surfaceReads = listOf(VoxelPos(0, -1, 0), VoxelPos(0, -2, 0), VoxelPos(dx, dy - 1, dz), VoxelPos(dx, dy - 2, dz))
		if (spec == null) {
			if (strideCost != null || maxRise.isFinite()) after += surfaceReads
			return StampProgram(clauses, after, candidates = null, behind = null, IntArray(0), emptyList())
		}
		after += surfaceReads
		val rise = dy.toDouble()
		if (spec.runUpNeeded?.invoke(rise) == true) return null
		if (spec.riseAdmission?.invoke(rise) == false) return StampProgram(clauses, after, emptyList(), behind = null, IntArray(0), emptyList())
		val behindX = -Integer.signum(dx)
		val behindZ = -Integer.signum(dz)
		val behindCells = (-2..1).map { VoxelPos(behindX, it, behindZ) }
		val behind = StampProgram.Clause.of(behindCells) { it.canStandAt(behindX, 0, behindZ) } ?: return null
		after += behindCells
		val origin = Stance(0, 0, 0)
		fun candidateOf(probed: JumpArcProbe.Footprint) = StampProgram.Candidate(
			probed.solution, probed.cells, probed.clipCells,
			ticks = lowerBoundTicks + (probed.solution.via?.detour(dx, dz, probed.solution.lateralOffset) ?: 0.0) / CoarseMoveRates.SPRINT_JUMP_BLOCKS_PER_TICK,
			standingStart = standingStartViable(false, origin, spec.modes, probed.solution, rise),
			standingStartBehind = standingStartViable(true, origin, spec.modes, probed.solution, rise),
			bonkCells = probed.bonkCells,
		)

		val free = JumpArcProbe.footprints(dx, dz, rise, spec.modes)
		val capped = if (free.isEmpty()) emptyList() else JumpArcProbe.cappedFootprints(dx, dz, rise, spec.modes)
		val candidates = (free + capped + JumpArcProbe.alternativeFootprints(dx, dz, rise, spec.modes)).map(::candidateOf)
		val clipCells = free.firstOrNull()?.clipCells.orEmpty()
		val columns = LinkedHashMap<Pair<Int, Int>, Int>()
		val bendColumns = IntArray(clipCells.size) { columns.getOrPut(clipCells[it].x to clipCells[it].z) { columns.size } }
		val bendGroups = columns.keys.map { (x, z) -> JumpArcProbe.bendFootprints(dx, dz, rise, spec.modes, x, z).map(::candidateOf) }
		return StampProgram(clauses, after, candidates, behind, bendColumns, bendGroups)
	}

	internal fun stampedEdge(view: CoarseVoxelView, origin: Stance, candidate: StampProgram.Candidate?, behindStands: Boolean): CoarseEdge {
		val spec = arc
		if (spec == null || candidate == null) return edgeOf(origin, walkTicks, probeReads = null)
		val rise = dy.toDouble()
		return edgeOf(
			origin, candidate.ticks,
			{ JumpArcProbe.probe(view, origin, dx, dz, rise, spec.modes, origin.y.toDouble())?.reads?.invoke() ?: LongOpenHashSet() },
			launch = candidate.solution,
			standingStart = if (behindStands) candidate.standingStartBehind else candidate.standingStart,
		)
	}

	internal val readBounds: IntArray by lazy(LazyThreadSafetyMode.PUBLICATION) {
		val bounds = intArrayOf(0, -1, 0, 0, 1, 0)
		fun include(x: Int, y: Int, z: Int) {
			bounds[0] = minOf(bounds[0], x)
			bounds[1] = minOf(bounds[1], y)
			bounds[2] = minOf(bounds[2], z)
			bounds[3] = maxOf(bounds[3], x)
			bounds[4] = maxOf(bounds[4], y)
			bounds[5] = maxOf(bounds[5], z)
		}
		conditions.forEach { condition ->
			condition.reads().forEach { include(it.x, it.y, it.z) }
			condition.neighborReads().forEach { include(it.x, it.y, it.z) }
		}
		arc?.let { spec ->
			include(minOf(0, dx) - 1, minOf(dy, -(spec.bounceDrop ?: 0), 0) - 2, minOf(0, dz) - 1)
			include(maxOf(0, dx) + 1, ARC_READ_CEILING, maxOf(0, dz) + 1)
		}
		bounds
	}

	private val sweptOffsets: List<VoxelPos> by lazy(LazyThreadSafetyMode.PUBLICATION) {
		val steps = maxOf(abs(dx), abs(dz))
		val columns = LinkedHashSet<Pair<Int, Int>>()
		for (step in 0..steps) {
			columns += (if (steps == 0) 0 else (dx.toDouble() * step / steps).roundToInt()) to (if (steps == 0) 0 else (dz.toDouble() * step / steps).roundToInt())
		}
		if (abs(dx) == 1 && abs(dz) == 1) {
			columns += dx to 0
			columns += 0 to dz
		}
		val top = maxOf(0, dy) + if (arc == null) 1 else 2
		columns.flatMap { (x, z) -> (minOf(0, dy) - 1..top).map { y -> VoxelPos(x, y, z) } }
	}

	private fun clearSweep(view: CoarseVoxelView, origin: Stance): Boolean {
		if (dx != 0 && dz != 0 || dx == 0 && dz == 0) return true
		val target = target(origin)
		val bottom = maxOf(origin.y, target.y) + SWEEP_FLOOR
		val top = minOf(origin.y, target.y) + SWEEP_CEILING
		if (bottom >= top) return true
		val alongX = dx != 0
		val from = (if (alongX) minOf(origin.x, target.x) else minOf(origin.z, target.z)) + 0.5 - BODY_HALF
		val to = (if (alongX) maxOf(origin.x, target.x) else maxOf(origin.z, target.z)) + 0.5 + BODY_HALF
		val lane = if (alongX) origin.z else origin.x
		val blocked = ArrayList<DoubleArray>()
		for (along in floor(from).toInt()..floor(to).toInt()) for (side in lane - 1..lane + 1) for (cellY in floor(bottom).toInt()..floor(top).toInt()) {
			val cellX = if (alongX) along else side
			val cellZ = if (alongX) side else along
			if (view.medium(cellX, cellY, cellZ) == Medium.WATER) continue
			val boxes = view.collisionBoxes(cellX, cellY, cellZ) ?: continue
			for (box in boxes.indices step 6) {
				val minAlong = if (alongX) boxes[box] + cellX else boxes[box + 2] + cellZ
				val maxAlong = if (alongX) boxes[box + 3] + cellX else boxes[box + 5] + cellZ
				if (maxAlong <= from || minAlong >= to || boxes[box + 4] + cellY <= bottom || boxes[box + 1] + cellY >= top) continue
				blocked += if (alongX) doubleArrayOf(boxes[box + 2] + cellZ, boxes[box + 5] + cellZ) else doubleArrayOf(boxes[box] + cellX, boxes[box + 3] + cellX)
			}
		}
		blocked.sortBy { it[0] }
		blocked += doubleArrayOf(lane + 2.0, lane + 3.0)
		var free = lane - 1.0
		for (interval in blocked) {
			val width = interval[0] - free
			if (width >= SWEEP_WIDTH && interval[0] - SWEEP_WIDTH / 2 >= lane && free + SWEEP_WIDTH / 2 <= lane + 1) return true
			free = maxOf(free, interval[1])
		}
		return false
	}

	internal fun sweep(origin: Stance): List<VoxelPos> = sweptOffsets.map { VoxelPos(origin.x + it.x, origin.y + it.y, origin.z + it.z) }

	internal fun swept(origin: Stance, cells: Set<VoxelPos>): List<VoxelPos> = sweep(origin).filter { it in cells }

	private val sweptBounds: IntArray by lazy(LazyThreadSafetyMode.PUBLICATION) {
		intArrayOf(sweptOffsets.minOf { it.x }, sweptOffsets.minOf { it.y }, sweptOffsets.minOf { it.z }, sweptOffsets.maxOf { it.x }, sweptOffsets.maxOf { it.y }, sweptOffsets.maxOf { it.z })
	}

	internal val readOffsets: List<VoxelPos> by lazy(LazyThreadSafetyMode.PUBLICATION) {
		buildList {
			addAll(ORIGIN_STANCE_READS)
			conditions.forEach {
				addAll(it.reads())
				addAll(it.neighborReads())
			}

			arc?.let { spec ->

				val steps = maxOf(abs(dx), abs(dz))
				for (step in 0..steps) {
					val alongX = if (steps == 0) 0 else (dx.toDouble() * step / steps).roundToInt()
					val alongZ = if (steps == 0) 0 else (dz.toDouble() * step / steps).roundToInt()
					val floorReach = minOf(dy, -(spec.bounceDrop ?: 0), 0) - 2
					for (y in floorReach..ARC_READ_CEILING) {
						for (ox in -1..1) {
							for (oz in -1..1) {
								add(VoxelPos(alongX + ox, y, alongZ + oz))
							}
						}
					}
				}
			}
		}
	}

	internal companion object {

		const val FLIP_TICKS = 0.05

		const val SWEEP_FLOOR = 0.6

		const val SWEEP_CEILING = 1.8

		const val SWEEP_WIDTH = 0.7

		const val BODY_HALF = 0.3

		const val IN_CELL_RUN_UP = 0.7

		const val STANDING_RUN_UP_TICKS = 24

		val ORIGIN_STANCE_READS = listOf(
			VoxelPos(0, -1, 0),
			VoxelPos(0, 0, 0),
			VoxelPos(0, 1, 0),
		)

		const val ARC_READ_CEILING = 4
	}
}

private class LazyReadSet(supplier: () -> Set<VoxelPos>) : AbstractSet<VoxelPos>() {
	private val backing: Set<VoxelPos> by lazy(LazyThreadSafetyMode.PUBLICATION, supplier)

	override val size: Int get() = backing.size

	override fun iterator(): Iterator<VoxelPos> = backing.iterator()

	override fun contains(element: VoxelPos): Boolean = element in backing
}
