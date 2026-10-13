package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.CoarseMoveRates
import com.lambda.pathing.actions.MotionTemplateId
import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.FallReach
import com.lambda.pathing.physics.FallRule
import com.lambda.pathing.world.ColumnOccupancy
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Medium
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.ChunkSectionPos
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal class FallEdge(
	val dx: Int,
	val dy: Int,
	val dz: Int,
	val ticks: Double,
	val standingStart: Boolean,
	val water: Boolean,
	val jumps: Boolean,
)

internal class FallArrival(val origin: Stance, val edge: FallEdge)

internal class FallEdges(
	private val view: CoarseVoxelView,
	moves: SimpleMoveLibrary,
	private val standable: StanceMemo,
) {
	private val limits = moves.catalog.options.fallLimits
	private val shallowWater = moves.catalog.options.allowSwimming
	private val jumpPenalty = moves.catalog.options.jumpPenaltyTicks
	private val occupancy = ColumnOccupancy(view)
	private val outgoing = Long2ObjectLinkedOpenHashMap<Array<FallEdge>>()
	private val incoming = Long2ObjectLinkedOpenHashMap<Array<FallArrival>>()
	private val rimMemo = Long2ObjectOpenHashMap<LongArray>()
	private val gates = Long2BooleanOpenHashMap()
	private val rims = LongOpenHashSet()
	private val normalFloors = limits.maxFallOnto(FallRule.NORMAL) >= MIN_DROP

	fun synchronize() {
		if (occupancy.synchronize()) dropCaches()
	}

	fun clear() {
		occupancy.clear()
		dropCaches()
	}

	private fun dropCaches() {
		outgoing.clear()
		incoming.clear()
		rimMemo.clear()
		gates.clear()
	}

	private val idle: Boolean get() = !normalFloors && !view.maySoftLand

	fun from(origin: Stance): Array<FallEdge> {
		if (idle) return NONE
		synchronize()
		if (!rim(origin)) return NONE
		val key = key(origin)
		outgoing.getAndMoveToLast(key)?.let { return it }
		val edges = scan(origin)
		if (outgoing.size >= CACHED_ORIGINS) outgoing.removeFirst()
		outgoing.putAndMoveToLast(key, edges)
		return edges
	}

	fun to(target: Stance): Array<FallArrival> {
		if (idle) return NO_ARRIVALS
		synchronize()
		val key = key(target)
		incoming.getAndMoveToLast(key)?.let { return it }
		val arrivals = discover(target)
		if (incoming.size >= CACHED_TARGETS) incoming.removeFirst()
		incoming.putAndMoveToLast(key, arrivals)
		return arrivals
	}

	fun readers(changed: CellBox): CellBox =
		changed.grown(-MAX_REACH - 1, MAX_REACH + 1, -HEADROOM_CELLS - 1, MAX_DEPTH + 2, -MAX_REACH - 1, MAX_REACH + 1)

	fun reads(changed: List<CellBox>, key: Long): Boolean {
		val x = PackedStance.unpackX(key)
		val y = PackedStance.unpackY(key)
		val z = PackedStance.unpackZ(key)
		if (changed.none { it.overlaps(x - MAX_REACH - 1, y - MAX_DEPTH - 2, z - MAX_REACH - 1, x + MAX_REACH + 1, y + HEADROOM_CELLS + 1, z + MAX_REACH + 1) }) return false
		if (rims.contains(key)) return true
		if (idle) return false
		synchronize()
		return rim(Stance(x, y, z))
	}

	fun coarseEdge(origin: Stance, edge: FallEdge): CoarseEdge {
		val target = origin.offset(edge.dx, edge.dy, edge.dz)
		return CoarseEdge(
			template = TEMPLATE,
			from = origin,
			to = target,
			movement = MovementId.FALL,
			lowerBoundTicks = edge.ticks,
			readSet = lazyReads(origin, target),
			standingStart = edge.standingStart,
		)
	}

	private fun lazyReads(origin: Stance, target: Stance): Set<VoxelPos> = object : AbstractSet<VoxelPos>() {
		private val cells by lazy(LazyThreadSafetyMode.PUBLICATION) {
			buildSet {
				for (y in target.y - 2..origin.y + HEADROOM_CELLS) add(VoxelPos(target.x, y, target.z))
				for (y in origin.y - 2..origin.y + HEADROOM_CELLS) add(VoxelPos(origin.x, y, origin.z))
				val dx = target.x - origin.x
				val dz = target.z - origin.z
				val steps = maxOf(abs(dx), abs(dz))
				for (step in 1 until steps) {
					val x = origin.x + (dx.toDouble() * step / steps).roundToInt()
					val z = origin.z + (dz.toDouble() * step / steps).roundToInt()
					for (y in target.y..origin.y + 1) add(VoxelPos(x, y, z))
				}
			}
		}

		override val size: Int get() = cells.size

		override fun iterator(): Iterator<VoxelPos> = cells.iterator()

		override fun contains(element: VoxelPos): Boolean = element in cells
	}

	private fun rim(origin: Stance): Boolean {
		val section = ChunkSectionPos.asLong(origin.x shr 4, origin.y shr 4, origin.z shr 4)
		val bits = rimMemo.get(section) ?: LongArray(MEMO_WORDS * 2).also { rimMemo.put(section, it) }
		val index = ((origin.y and 15) shl 8) or ((origin.z and 15) shl 4) or (origin.x and 15)
		val word = index ushr 6
		val mask = 1L shl index
		if (bits[word] and mask != 0L) return bits[MEMO_WORDS + word] and mask != 0L
		val rim = view.medium(origin.x, origin.y, origin.z) == Medium.AIR && standable.isStance(origin) && openSides(origin) != 0
		bits[word] = bits[word] or mask
		if (rim) {
			bits[MEMO_WORDS + word] = bits[MEMO_WORDS + word] or mask
			rims.add(key(origin))
		}
		return rim
	}

	private fun openSides(origin: Stance): Int {
		var open = 0
		for (side in 0 until SIDES) {
			val x = origin.x + SIDE_DX[side]
			val z = origin.z + SIDE_DZ[side]
			val drops = !occupancy.occupied(x, origin.y - 1, z) && !occupancy.occupied(x, origin.y - 2, z) && passable(x, origin.y, z)
			if (!drops && !waterSide(x, origin.y, z)) continue
			if (SIDE_DX[side] != 0 && SIDE_DZ[side] != 0 && (!crossable(origin.x + SIDE_DX[side], origin.y, origin.z) || !crossable(origin.x, origin.y, origin.z + SIDE_DZ[side]))) continue
			open = open or (1 shl side)
		}
		return open
	}

	private fun passable(x: Int, y: Int, z: Int): Boolean = !occupancy.occupied(x, y, z) && !occupancy.occupied(x, y + 1, z)

	private fun crossable(x: Int, y: Int, z: Int): Boolean = passable(x, y, z) || waterSide(x, y, z)

	private fun waterSide(x: Int, y: Int, z: Int): Boolean {
		if (!shallowWater) return false
		val top = occupancy.highestAtOrBelow(x, y + 1, z, y - SHALLOW_WATER_DEPTH)
		return top != Int.MIN_VALUE && top <= y && view.medium(x, top, z) == Medium.WATER
	}

	private fun softBelow(origin: Stance): Boolean {
		val section = ChunkSectionPos.asLong(origin.x shr 4, origin.y shr 4, origin.z shr 4)
		if (gates.containsKey(section)) return gates.get(section)
		val baseX = (origin.x shr 4) shl 4
		val baseY = (origin.y shr 4) shl 4
		val baseZ = (origin.z shr 4) shl 4
		val soft = view.maySoftLandWithin(
			baseX - MAX_REACH, baseY - MAX_DEPTH, baseZ - MAX_REACH,
			baseX + 15 + MAX_REACH, baseY + 15 - if (shallowWater) MIN_WATER_DROP else MIN_DROP, baseZ + 15 + MAX_REACH,
		)
		gates.put(section, soft)
		return soft
	}

	private fun scan(origin: Stance): Array<FallEdge> {
		if (!normalFloors && !softBelow(origin)) return NONE
		val open = openSides(origin)
		val launch = origin.y + view.surfaceOffset(origin.x, origin.y - 1, origin.z)
		val canJump = (2..HEADROOM_CELLS).none { occupancy.occupied(origin.x, origin.y + it, origin.z) }
		var found: ArrayList<FallEdge>? = null
		for (index in LATTICE_DX.indices) {
			if (open and (1 shl LATTICE_SIDE[index]) == 0) continue
			val edge = landing(origin, LATTICE_DX[index], LATTICE_DZ[index], launch, canJump) ?: continue
			(found ?: ArrayList<FallEdge>().also { found = it }).add(edge)
		}
		return found?.toTypedArray() ?: NONE
	}

	private fun landing(origin: Stance, dx: Int, dz: Int, launch: Double, canJump: Boolean): FallEdge? {
		val x = origin.x + dx
		val z = origin.z + dz
		val hit = occupancy.highestAtOrBelow(x, origin.y + 1, z, origin.y - MAX_DEPTH)
		if (hit == Int.MIN_VALUE) return null
		val water = when (view.medium(x, hit, z)) {
			Medium.WATER -> true
			Medium.SOLID -> false
			else -> return null
		}
		val target = Stance(x, if (water) hit else hit + 1, z)
		if (target.y - origin.y > -minimumDrop(water)) return null
		if (!water && view.voxel(x, hit, z).bouncy) return null
		if (!standable.isStance(target)) return null
		val landingHeight = if (water) hit + 1.0 else target.y + view.surfaceOffset(x, target.y - 1, z)
		val drop = launch - landingHeight
		if (drop < if (water && shallowWater) MIN_WATER_REAL_DROP else MIN_REAL_DROP) return null

		val length = hypot(dx.toDouble(), dz.toDouble())
		val unitX = dx / length
		val unitZ = dz / length
		var near = Double.NEGATIVE_INFINITY
		var far = Double.POSITIVE_INFINITY
		for (axis in 0..1) {
			val unit = if (axis == 0) unitX else unitZ
			val centre = (if (axis == 0) dx else dz).toDouble()
			val low = centre - LANDING_HALF_EXTENT
			val high = centre + LANDING_HALF_EXTENT
			if (abs(unit) < AXIS_EPSILON) {
				if (low > 0.0 || high < 0.0) return null
				continue
			}
			near = max(near, min(low / unit, high / unit))
			far = min(far, max(low / unit, high / unit))
		}
		if (far <= near) return null

		val waterCells = if (water) waterCells(x, hit, z) else 0
		val rule = if (water) FallRule.NONE else view.fallRule(x, floor(landingHeight - LANDING_PROBE_DEPTH).toInt(), z)
		if (!water && !acceptsDeepFalls(rule)) return null
		for (jumps in JUMP_ORDER) {
			if (jumps && !canJump) continue
			val flight = FallReach.flight(jumps, drop) ?: continue
			if (far <= flight.launchOffset || near > flight.reach(MOMENTUM_SPEED)) continue
			if (!water && drop + (if (jumps) JUMP_APEX else 0.0) > limits.maxFallOnto(rule)) continue
			if (!clearChord(origin, unitX, unitZ, flight.launchOffset, launch + if (jumps) JUMP_APEX else 0.0, x, z, landingHeight)) continue
			val settle = if (water) FallReach.plunge(flight.impactSpeed, waterCells).ticks.toDouble() else FLOOR_SETTLE_TICKS
			val ticks = max((flight.airTicks + LAUNCH_TICKS).toDouble(), near / CoarseMoveRates.SPRINT_JUMP_BLOCKS_PER_TICK) + settle + if (jumps) jumpPenalty else 0.0
			val standing = near <= flight.reach(STANDING_SPEED)
			if (!standing && !runUpBehind(origin, unitX, unitZ)) continue
			return FallEdge(dx, target.y - origin.y, dz, ticks, standing, water, jumps)
		}
		return null
	}

	private fun runUpBehind(origin: Stance, unitX: Double, unitZ: Double): Boolean {
		val x = floor(origin.x + 0.5 - unitX).toInt()
		val z = floor(origin.z + 0.5 - unitZ).toInt()
		return (-1..1).any { dy -> standable.isStance(Stance(x, origin.y + dy, z)) }
	}

	private fun waterCells(x: Int, top: Int, z: Int): Int {
		var cells = 0
		while (cells < MAX_WATER_CELLS && view.medium(x, top - cells, z) == Medium.WATER) cells++
		return cells
	}

	private fun clearChord(origin: Stance, unitX: Double, unitZ: Double, offset: Double, startHeight: Double, x: Int, z: Int, landingHeight: Double): Boolean {
		val startX = origin.x + 0.5 + unitX * offset
		val startZ = origin.z + 0.5 + unitZ * offset
		val endX = x + 0.5
		val endZ = z + 0.5
		val endHeight = landingHeight + 1.0
		val span = max(hypot(endX - startX, endZ - startZ), abs(startHeight - endHeight))
		val steps = max(1, ceil(span / CHORD_STEP).toInt())
		for (step in 1..steps) {
			val t = step.toDouble() / steps
			val px = floor(startX + (endX - startX) * t).toInt()
			val pz = floor(startZ + (endZ - startZ) * t).toInt()
			val py = startHeight + (endHeight - startHeight) * t
			for (lift in BODY_SAMPLES) if (occupancy.occupied(px, floor(py + lift).toInt(), pz)) return false
		}
		return true
	}

	private fun landable(target: Stance): Boolean =
		standable.isStance(target) && when (view.medium(target.x, target.y, target.z)) {
			Medium.WATER -> true
			Medium.AIR -> {
				val floorY = floor(target.y + view.surfaceOffset(target.x, target.y - 1, target.z) - LANDING_PROBE_DEPTH).toInt()
				acceptsDeepFalls(view.fallRule(target.x, floorY, target.z))
			}
			else -> false
		}

	private fun acceptsDeepFalls(rule: FallRule): Boolean = normalFloors || limits.maxFallOnto(rule) >= MIN_DROP

	private fun minimumDrop(water: Boolean): Int = if (water && shallowWater) MIN_WATER_DROP else MIN_DROP

	private fun discover(target: Stance): Array<FallArrival> {
		if (!landable(target)) return NO_ARRIVALS
		val water = view.medium(target.x, target.y, target.z) == Medium.WATER
		val clearFrom = if (water) target.y + 1 else target.y
		val ceiling = occupancy.lowestAbove(target.x, clearFrom - 1, target.z, target.y + MAX_DEPTH + 1)
		val highest = minOf(ceiling - 2, target.y + MAX_DEPTH, view.simulableStanceY.last)
		val lowest = target.y + minimumDrop(water)
		if (highest < lowest) return NO_ARRIVALS
		var found: ArrayList<FallArrival>? = null
		for (index in LATTICE_DX.indices) {
			val dx = LATTICE_DX[index]
			val dz = LATTICE_DZ[index]
			val x = target.x - dx
			val z = target.z - dz
			val bottom = lowest - INTRUDING_FLOOR_DEPTH
			if (occupancy.top(x, z) < bottom) continue
			var probe = highest + 1
			while (true) {
				val floorY = occupancy.highestAtOrBelow(x, probe, z, bottom)
				if (floorY == Int.MIN_VALUE || floorY < bottom) break
				for (stanceY in floorY + 1..floorY + if (view.voxel(x, floorY, z).intrudesAbove) INTRUDING_FLOOR_DEPTH else 1) {
					if (stanceY !in lowest..highest) continue
					val origin = Stance(x, stanceY, z)
					for (edge in from(origin)) {
						if (edge.dx == dx && edge.dz == dz && stanceY + edge.dy == target.y) {
							(found ?: ArrayList<FallArrival>().also { found = it }).add(FallArrival(origin, edge))
						}
					}
				}
				val gap = occupancy.highestEmptyAtOrBelow(x, floorY, z, bottom)
				if (gap == Int.MIN_VALUE || gap >= probe) break
				probe = gap
			}
		}
		return found?.toTypedArray() ?: NO_ARRIVALS
	}

	private fun key(stance: Stance): Long = PackedStance.pack(stance, SpeedClass.STOPPED)

	companion object {
		const val MIN_DROP = 4

		const val MAX_DEPTH = 128

		const val MAX_REACH = 16

		val TEMPLATE = MotionTemplateId(Int.MAX_VALUE)

		private const val MIN_REAL_DROP = 3.0

		private const val MIN_WATER_DROP = 0

		private const val MIN_WATER_REAL_DROP = -1.0

		private const val SHALLOW_WATER_DEPTH = 3

		private const val HEADROOM_CELLS = 3

		private const val INTRUDING_FLOOR_DEPTH = 2

		private const val LAUNCH_TICKS = 1

		private const val FLOOR_SETTLE_TICKS = 2.0

		private const val MAX_WATER_CELLS = 5

		private const val CHORD_STEP = 0.5

		private const val LANDING_PROBE_DEPTH = 0.2

		private const val AXIS_EPSILON = 1e-9

		private const val LANDING_HALF_EXTENT = 0.5 + 0.3

		private const val CACHED_ORIGINS = 1 shl 14

		private const val CACHED_TARGETS = 1 shl 14

		private const val MEMO_WORDS = 4096 / 64

		private const val SIDES = 8

		private val NONE = emptyArray<FallEdge>()

		private val NO_ARRIVALS = emptyArray<FallArrival>()

		private val JUMP_ORDER = booleanArrayOf(false, true)

		private val BODY_SAMPLES = doubleArrayOf(0.0, 0.9, 1.75)

		private val JUMP_APEX = BallisticProfile.VANILLA.jumpApex

		private val MOMENTUM_SPEED = BallisticProfile.VANILLA.momentumSpeed(sprint = true)

		private val STANDING_SPEED = BallisticProfile.VANILLA.let {
			it.runUpSpeed(0.0, it.groundRunUpTicks(0.0, STANDING_RUN_UP, sprint = true, maxTicks = 24), sprint = true)
		}

		private const val STANDING_RUN_UP = 0.7

		private val SIDE_DX = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)

		private val SIDE_DZ = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

		private const val DENSE_REACH = 3

		private val RING_RADII = intArrayOf(4, 5, 6, 7, 8, 10, 12, 14, MAX_REACH)

		private const val WIDE_RING = 8

		private const val NARROW_HEADINGS = 16

		private const val WIDE_HEADINGS = 32

		private val LATTICE_DX: IntArray

		private val LATTICE_DZ: IntArray

		private val LATTICE_SIDE: IntArray

		init {
			val offsets = LinkedHashSet<Pair<Int, Int>>()
			for (dx in -DENSE_REACH..DENSE_REACH) for (dz in -DENSE_REACH..DENSE_REACH) if (dx != 0 || dz != 0) offsets += dx to dz
			for (radius in RING_RADII) {
				val headings = if (radius <= WIDE_RING) NARROW_HEADINGS else WIDE_HEADINGS
				for (heading in 0 until headings) {
					val angle = 2.0 * Math.PI * heading / headings
					offsets += (radius * cos(angle)).roundToInt() to (radius * sin(angle)).roundToInt()
				}
			}
			val sorted = offsets.sortedWith(compareBy({ hypot(it.first.toDouble(), it.second.toDouble()) }, { it.first }, { it.second }))
			LATTICE_DX = IntArray(sorted.size) { sorted[it].first }
			LATTICE_DZ = IntArray(sorted.size) { sorted[it].second }
			LATTICE_SIDE = IntArray(sorted.size) {
				val angle = atan2(sorted[it].second.toDouble(), sorted[it].first.toDouble())
				Math.floorMod((angle / (Math.PI / 4.0)).roundToInt(), SIDES)
			}
		}
	}
}
