package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.MotionTemplateId
import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.launch.ArcSample
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.BounceArcProbe
import com.lambda.pathing.launch.BounceSolution
import com.lambda.pathing.actions.families.SlimeBounceMovement
import com.lambda.pathing.launch.BounceSolver
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.BlockPos
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot

internal class ChainEdge(
	val dx: Int,
	val dy: Int,
	val dz: Int,
	val ticks: Double,
	val solution: BounceSolution,
	private val probedReads: () -> LongOpenHashSet,
) {
	val reads: Set<VoxelPos> by lazy(LazyThreadSafetyMode.PUBLICATION) {
		probedReads().mapTo(HashSet()) { VoxelPos(BlockPos.unpackLongX(it), BlockPos.unpackLongY(it), BlockPos.unpackLongZ(it)) }
	}
}

internal class ChainArrival(val origin: Stance, val edge: ChainEdge)

internal class BounceChains(
	private val view: CoarseVoxelView,
	private val standable: StanceMemo,
	private val pads: BouncePads,
	private val maxDrop: Int,
) {
	private val outgoing = Long2ObjectOpenHashMap<ArrayList<ChainEdge>>()
	private val incoming = Long2ObjectOpenHashMap<ArrayList<ChainArrival>>()
	private val discovered = LongOpenHashSet()
	private val scannedSections = LongOpenHashSet()
	private var minX = Int.MAX_VALUE
	private var minY = Int.MAX_VALUE
	private var minZ = Int.MAX_VALUE
	private var maxX = Int.MIN_VALUE
	private var maxY = Int.MIN_VALUE
	private var maxZ = Int.MIN_VALUE

	fun clear() {
		outgoing.clear()
		incoming.clear()
		discovered.clear()
		scannedSections.clear()
		minX = Int.MAX_VALUE
		minY = Int.MAX_VALUE
		minZ = Int.MAX_VALUE
		maxX = Int.MIN_VALUE
		maxY = Int.MIN_VALUE
		maxZ = Int.MIN_VALUE
	}

	fun invalidate(changed: List<CellBox>) {
		if (scannedSections.isEmpty() && discovered.isEmpty()) return
		if (minX > maxX || changed.any { it.overlaps(minX - CHAIN_REACH - 1, minY - maxDrop - 2, minZ - CHAIN_REACH - 1, maxX + CHAIN_REACH + 1, maxY + maxDrop + 2, maxZ + CHAIN_REACH + 1) }) clear()
	}

	fun from(origin: Stance): List<ChainEdge> {
		if (!view.mayBounceWithin(origin.x - FIRST_REACH, origin.y - maxDrop - 1, origin.z - FIRST_REACH, origin.x + FIRST_REACH, origin.y - MIN_DROP - 1, origin.z + FIRST_REACH)) return NO_EDGES
		discover(origin)
		return outgoing.get(key(origin)) ?: NO_EDGES
	}

	fun to(target: Stance): List<ChainArrival> {
		val minY = target.y - maxDrop - 1
		val maxY = target.y + maxDrop
		if (!view.mayBounceWithin(target.x - CHAIN_REACH, minY, target.z - CHAIN_REACH, target.x + CHAIN_REACH, maxY, target.z + CHAIN_REACH)) return NO_ARRIVALS
		for (sectionX in ((target.x - CHAIN_REACH) shr 4)..((target.x + CHAIN_REACH) shr 4)) {
			for (sectionY in (minY shr 4)..(maxY shr 4)) for (sectionZ in ((target.z - CHAIN_REACH) shr 4)..((target.z + CHAIN_REACH) shr 4)) scan(sectionX, sectionY, sectionZ)
		}
		return incoming.get(key(target)) ?: NO_ARRIVALS
	}

	fun readers(changed: CellBox): CellBox = changed.grown(-CHAIN_REACH - 1, CHAIN_REACH + 1, -maxDrop - 2, maxDrop + 2, -CHAIN_REACH - 1, CHAIN_REACH + 1)

	fun reads(changed: List<CellBox>, key: Long): Boolean {
		if (outgoing.isEmpty() && incoming.isEmpty()) return false
		val x = PackedStance.unpackX(key)
		val y = PackedStance.unpackY(key)
		val z = PackedStance.unpackZ(key)
		return changed.any { it.overlaps(x - CHAIN_REACH - 1, y - maxDrop - 2, z - CHAIN_REACH - 1, x + CHAIN_REACH + 1, y + maxDrop + 2, z + CHAIN_REACH + 1) }
	}

	fun coarseEdge(origin: Stance, edge: ChainEdge): CoarseEdge = CoarseEdge(
		template = TEMPLATE,
		from = origin,
		to = origin.offset(edge.dx, edge.dy, edge.dz),
		movement = MovementId.BOUNCE,
		lowerBoundTicks = edge.ticks,
		readSet = edge.reads,
		bounce = edge.solution,
	)

	private fun scan(sectionX: Int, sectionY: Int, sectionZ: Int) {
		if (!scannedSections.add(BlockPos.asLong(sectionX, sectionY, sectionZ))) return
		for (pad in pads.padsIn(sectionX, sectionY, sectionZ)) {
			val padX = BlockPos.unpackLongX(pad)
			val padY = BlockPos.unpackLongY(pad)
			val padZ = BlockPos.unpackLongZ(pad)
			for (x in padX - FIRST_REACH..padX + FIRST_REACH) for (z in padZ - FIRST_REACH..padZ + FIRST_REACH) {
				if (hypot((x - padX).toDouble(), (z - padZ).toDouble()) > FIRST_REACH) continue
				for (y in padY + 1 + MIN_DROP..padY + 1 + maxDrop) {
					val origin = Stance(x, y, z)
					if (standable.isStance(origin)) discover(origin)
				}
			}
		}
	}

	private fun discover(origin: Stance) {
		if (!discovered.add(key(origin))) return
		minX = minOf(minX, origin.x)
		minY = minOf(minY, origin.y)
		minZ = minOf(minZ, origin.z)
		maxX = maxOf(maxX, origin.x)
		maxY = maxOf(maxY, origin.y)
		maxZ = maxOf(maxZ, origin.z)
		val launchHeight = origin.y + view.surfaceOffset(origin.x, origin.y - 1, origin.z)
		val tried = HashSet<Pair<Stance, Int>>()
		val bearings = LongOpenHashSet()
		for (sectionX in ((origin.x - CHAIN_REACH) shr 4)..((origin.x + CHAIN_REACH) shr 4)) {
			for (sectionY in ((origin.y - maxDrop - 1) shr 4)..((origin.y - MIN_DROP - 1) shr 4)) for (sectionZ in ((origin.z - CHAIN_REACH) shr 4)..((origin.z + CHAIN_REACH) shr 4)) {
				for (pad in pads.padsIn(sectionX, sectionY, sectionZ)) {
					val padY = BlockPos.unpackLongY(pad)
					if (origin.y - padY - 1 !in MIN_DROP..maxDrop) continue
					val aimX = BlockPos.unpackLongX(pad) - origin.x.toDouble()
					val aimZ = BlockPos.unpackLongZ(pad) - origin.z.toDouble()
					val length = hypot(aimX, aimZ)
					if (length < MIN_FIRST_REACH || length > CHAIN_REACH) continue
					val bearing = ((atan2(aimZ, aimX) + PI) / (2.0 * PI) * BEARING_BINS).toLong()
					if (!bearings.add(padY.toLong() * BEARING_BINS + bearing)) continue
					chase(origin, launchHeight, aimX / length, aimZ / length, padY, tried)
				}
			}
		}
	}

	private fun chase(origin: Stance, launchHeight: Double, unitX: Double, unitZ: Double, padY: Int, tried: HashSet<Pair<Stance, Int>>) {
		val depth = launchHeight - (padY + 1)
		val drop = origin.y - padY - 1
		for ((jump, sprint) in STYLES) {
			val still = arc(depth, jump, sprint, MAX_CONTACTS) ?: continue
			val moving = arc(depth, jump, sprint, MAX_CONTACTS, entrySpeed = 1.0) ?: continue
			val hits = contactTicks(still, depth)
			for (step in 0..SPEED_STEPS) {
				val speed = ballistics.momentumSpeed(sprint) * step / SPEED_STEPS
				var onPads = 0
				while (onPads < hits.size && onPad(origin, unitX, unitZ, along(still, moving, speed, hits[onPads]), padY)) onPads++
				for (contacts in 1..onPads) {
					val stillChain = arc(depth, jump, sprint, contacts) ?: continue
					val movingChain = arc(depth, jump, sprint, contacts, entrySpeed = 1.0) ?: continue
					val last = contactTicks(stillChain, depth).lastOrNull() ?: continue
					val ceiling = reboundCeiling(origin, unitX, unitZ, launchHeight, stillChain, movingChain, speed, last)
					val templated = drop <= SlimeBounceMovement.TEMPLATE_MAX_DROP && ceiling.isInfinite()
					if (templated && contacts < MIN_CONTACTS) continue
					val target = if (ceiling.isInfinite()) {
						landing(origin, unitX, unitZ, launchHeight, stillChain, movingChain, speed, last)
					} else {
						val capped = ballistics.bounce(speed, depth, -depth - BELOW_PADS, holdForward = true, sprint = sprint, jump = jump, bounces = contacts, reboundHeadroom = ceiling) ?: continue
						landing(origin, unitX, unitZ, launchHeight, capped, capped, 0.0, last)
					} ?: continue
					if (!tried.add(target to contacts)) continue
					verify(origin, target, drop, launchHeight, contacts, ceiling, templated)
				}
			}
		}
	}

	private fun reboundCeiling(origin: Stance, unitX: Double, unitZ: Double, launchHeight: Double, still: ArcSample, moving: ArcSample, speed: Double, lastContact: Int): Double {
		for (tick in lastContact + 1 until still.heights.size) {
			if (still.heights[tick] <= still.heights[tick - 1]) return Double.POSITIVE_INFINITY
			val below = launchHeight + still.heights[tick - 1] + Vanilla.PLAYER_HEIGHT
			val head = launchHeight + still.heights[tick] + Vanilla.PLAYER_HEIGHT
			val distance = along(still, moving, speed, tick)
			for (offset in FOOTPRINT_SAMPLES) {
				val x = floor(origin.x + 0.5 + unitX * (distance + offset)).toInt()
				val z = floor(origin.z + 0.5 + unitZ * (distance + offset)).toInt()
				for (y in floor(below).toInt()..floor(head).toInt()) {
					val bottom = ceilingBottom(x, y, z) ?: continue
					if (bottom > below - CEILING_EPSILON && bottom < head) return bottom - Vanilla.PLAYER_HEIGHT - launchHeight
				}
			}
		}
		return Double.POSITIVE_INFINITY
	}

	private fun ceilingBottom(x: Int, y: Int, z: Int): Double? = when (view.collisionClass(x, y, z)) {
		CollisionClass.EMPTY -> null
		CollisionClass.FULL, CollisionClass.UNKNOWN -> y.toDouble()
		CollisionClass.PARTIAL -> view.collisionBoxes(x, y, z)?.let { boxes -> y + (0 until boxes.size / 6).minOf { boxes[6 * it + 1] } }
	}

	private fun along(still: ArcSample, moving: ArcSample, speed: Double, tick: Int): Double =
		BounceSolver.LAUNCH_OFFSET + still.distances[tick] + speed * (moving.distances[tick] - still.distances[tick])

	private fun verify(origin: Stance, target: Stance, drop: Int, launchHeight: Double, contacts: Int, ceiling: Double, templated: Boolean) {
		val dx = target.x - origin.x
		val dz = target.z - origin.z
		if (templated && contacts <= TEMPLATED_CONTACTS && (dx == 0 || dz == 0 || abs(dx) == abs(dz))) return
		val probed = BounceArcProbe.probe(view, origin, dx, dz, drop, target.y - origin.y, launchHeight, bounces = contacts, reboundHeadroom = ceiling) ?: return
		val edge = ChainEdge(dx, target.y - origin.y, dz, probed.solution.airTicks + SETTLE_TICKS, probed.solution, probed.reads)
		outgoing.getOrPut(key(origin)) { ArrayList() } += edge
		incoming.getOrPut(key(target)) { ArrayList() } += ChainArrival(origin, edge)
	}

	private fun landing(origin: Stance, unitX: Double, unitZ: Double, launchHeight: Double, still: ArcSample, moving: ArcSample, speed: Double, lastContact: Int): Stance? {
		for (tick in lastContact + 2 until still.heights.size) {
			val above = launchHeight + still.heights[tick - 1]
			val below = launchHeight + still.heights[tick]
			if (below >= above) continue
			val distance = along(still, moving, speed, tick)
			val x = floor(origin.x + 0.5 + unitX * distance).toInt()
			val z = floor(origin.z + 0.5 + unitZ * distance).toInt()
			for (y in floor(above).toInt() + 1 downTo floor(below).toInt()) {
				val surface = view.standingSurface(x, y - 1, z) ?: continue
				val height = y - 1 + surface
				if (height > above || height < below) continue
				if (BounceArcProbe.isPad(view, x, y - 1, z)) return null
				val stance = Stance(x, y, z)
				return stance.takeIf { standable.isStance(it) }
			}
		}
		return null
	}

	private fun onPad(origin: Stance, unitX: Double, unitZ: Double, along: Double, padY: Int): Boolean =
		BounceArcProbe.isPad(view, floor(origin.x + 0.5 + unitX * along).toInt(), padY, floor(origin.z + 0.5 + unitZ * along).toInt())

	private fun arc(depth: Double, jump: Boolean, sprint: Boolean, contacts: Int, entrySpeed: Double = 0.0): ArcSample? =
		ballistics.bounce(entrySpeed, depth, -depth - BELOW_PADS, holdForward = true, sprint = sprint, jump = jump, bounces = contacts)

	private fun contactTicks(arc: ArcSample, depth: Double): List<Int> =
		arc.heights.indices.filter { abs(arc.heights[it] + depth) < CONTACT_EPSILON }

	private fun key(stance: Stance): Long = BlockPos.asLong(stance.x, stance.y, stance.z)

	private companion object {
		val ballistics = BallisticProfile.VANILLA

		val TEMPLATE = MotionTemplateId(Int.MAX_VALUE - 1)

		val NO_EDGES = emptyList<ChainEdge>()

		val NO_ARRIVALS = emptyList<ChainArrival>()

		val STYLES = listOf(true to true, true to false, false to true, false to false)

		const val MIN_DROP = 2

		const val FIRST_REACH = 10

		const val MIN_FIRST_REACH = 0.5

		const val CHAIN_REACH = 40

		const val MIN_CONTACTS = 2

		const val MAX_CONTACTS = 4

		const val TEMPLATED_CONTACTS = 2

		const val SPEED_STEPS = 8

		const val BEARING_BINS = 720L

		const val BELOW_PADS = 1.0

		val FOOTPRINT_SAMPLES = doubleArrayOf(-Vanilla.PLAYER_HALF_WIDTH.toDouble(), 0.0, Vanilla.PLAYER_HALF_WIDTH.toDouble())

		const val CEILING_EPSILON = 1.0E-6

		const val CONTACT_EPSILON = 1.0E-9

		const val SETTLE_TICKS = 2.0
	}
}
