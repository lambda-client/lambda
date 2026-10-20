package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.MotionTemplateId
import com.lambda.pathing.actions.families.SlimeBounceMovement
import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.Via
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.launch.ArcSample
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.BounceArcProbe
import com.lambda.pathing.launch.BounceSolution
import com.lambda.pathing.launch.BounceSolver
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.launch.Sweep
import com.lambda.pathing.launch.probed
import com.lambda.pathing.launch.sweepPoints
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.CollisionClass
import com.lambda.pathing.world.Footing
import com.lambda.pathing.world.TerrainWatch
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
	private val bends: Boolean,
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
	private val surfaces = Long2ObjectOpenHashMap<DoubleArray>()
	private val surfaceWatch = TerrainWatch(view)
	private var lastSurfaceKey = 0L
	private var lastSurfaces: DoubleArray? = null
	private val surfaceBounds = IntArray(6)

	fun clear() {
		forgetSurfaces()
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
		if (changed.any(::coversSurfaces)) forgetSurfaces()
		if (scannedSections.isEmpty() && discovered.isEmpty()) return
		if (minX > maxX || changed.any { it.overlaps(minX - CHAIN_REACH - 1, minY - maxDrop - 2, minZ - CHAIN_REACH - 1, maxX + CHAIN_REACH + 1, maxY + maxDrop + 2, maxZ + CHAIN_REACH + 1) }) clear()
	}

	fun from(origin: Stance): List<ChainEdge> {
		if (!view.mayBounceWithin(origin.x - FIRST_REACH, origin.y - maxDrop - 1, origin.z - FIRST_REACH, origin.x + FIRST_REACH, origin.y - SlimeBounceMovement.MIN_DROP - 1, origin.z + FIRST_REACH)) return NO_EDGES
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
				for (y in padY + 1 + SlimeBounceMovement.MIN_DROP..padY + 1 + maxDrop) {
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
		val tried = HashSet<Attempt>()
		val bent = HashMap<Stance, Bent>()
		val bearings = LongOpenHashSet()
		for (sectionX in ((origin.x - CHAIN_REACH) shr 4)..((origin.x + CHAIN_REACH) shr 4)) {
			for (sectionY in ((origin.y - maxDrop - 1) shr 4)..((origin.y - SlimeBounceMovement.MIN_DROP - 1) shr 4)) for (sectionZ in ((origin.z - CHAIN_REACH) shr 4)..((origin.z + CHAIN_REACH) shr 4)) {
				for (pad in pads.padsIn(sectionX, sectionY, sectionZ)) {
					val padY = BlockPos.unpackLongY(pad)
					if (origin.y - padY - 1 !in SlimeBounceMovement.MIN_DROP..maxDrop) continue
					val aimX = BlockPos.unpackLongX(pad) - origin.x.toDouble()
					val aimZ = BlockPos.unpackLongZ(pad) - origin.z.toDouble()
					val length = hypot(aimX, aimZ)
					if (length < MIN_FIRST_REACH || length > CHAIN_REACH) continue
					val bearing = ((atan2(aimZ, aimX) + PI) / (2.0 * PI) * BEARING_BINS).toLong()
					if (!bearings.add(bearing)) continue
					chase(origin, launchHeight, aimX / length, aimZ / length, tried, bent)
				}
			}
		}
		for ((target, best) in bent) {
			add(origin, ChainEdge(target.x - origin.x, target.y - origin.y, target.z - origin.z, best.solution.airTicks.toDouble(), best.solution, best.reads))
		}
	}

	private fun chase(origin: Stance, launchHeight: Double, unitX: Double, unitZ: Double, tried: HashSet<Attempt>, bent: HashMap<Stance, Bent>) {
		val bottom = launchHeight - (origin.y - maxDrop - 1) + BELOW_PADS
		for ((jump, sprint) in STYLES) {
			for (step in 0..SPEED_STEPS) {
				val bending = bends && jump && step in BENT_SPEED_STEPS
				follow(origin, launchHeight, unitX, unitZ, jump, sprint, ballistics.momentumSpeed(sprint) * step / SPEED_STEPS, bottom, tried, bent.takeIf { bending })
			}
		}
	}

	private fun follow(origin: Stance, launchHeight: Double, unitX: Double, unitZ: Double, jump: Boolean, sprint: Boolean, speed: Double, bottom: Double, tried: HashSet<Attempt>, bent: HashMap<Stance, Bent>?) {
		var depths = DoubleArray(0)
		var levels = IntArray(0)
		var ceiling = Double.POSITIVE_INFINITY
		while (true) {
			var still = flight(depths, bottom, jump, sprint, 0.0, ceiling) ?: return
			var moving = flight(depths, bottom, jump, sprint, 1.0, ceiling) ?: return
			if (levels.isNotEmpty()) {
				val found = reboundCeiling(origin, unitX, unitZ, launchHeight, still, moving, speed, still.contacts[levels.size - 1])
				if (found < ceiling) {
					ceiling = found
					still = flight(depths, bottom, jump, sprint, 0.0, ceiling) ?: return
					moving = flight(depths, bottom, jump, sprint, 1.0, ceiling) ?: return
				}
				if (bent != null && levels.size == 1) bend(origin, launchHeight, unitX, unitZ, jump, sprint, speed, still, moving, depths[0], levels[0], bent)
			}
			val start = if (levels.isEmpty()) 1 else still.contacts[levels.size - 1] + 1
			val hit = firstSurface(origin, launchHeight, unitX, unitZ, still, moving, speed, start) ?: return
			if (!hit.pad) {
				if (levels.isNotEmpty()) record(origin, Stance(hit.x, hit.y, hit.z), levels, launchHeight, ceiling, tried)
				return
			}
			val level = hit.y - 1
			if (levels.isEmpty() && origin.y - level - 1 < SlimeBounceMovement.MIN_DROP) return
			if (levels.size == MAX_CONTACTS) return
			val depth = launchHeight - hit.surface
			if (depth <= 0.0) return
			depths += depth
			levels += level
		}
	}

	private fun flight(depths: DoubleArray, bottom: Double, jump: Boolean, sprint: Boolean, entrySpeed: Double, ceiling: Double): ArcSample? =
		if (depths.isEmpty()) {
			ballistics.fly(LaunchMode.entries.first { it.sprint == sprint && it.jumps == jump }, entrySpeed, -bottom, maxTicks = BallisticProfile.MAX_BOUNCE_TICKS)
		} else {
			ballistics.bounce(entrySpeed, depths, -bottom, holdForward = true, sprint = sprint, jump = jump, reboundHeadroom = ceiling)
		}

	private fun record(origin: Stance, target: Stance, levels: IntArray, launchHeight: Double, ceiling: Double, tried: HashSet<Attempt>) {
		val drop = origin.y - levels[0] - 1
		val level = levels.all { it == levels[0] }
		val templated = level && drop <= SlimeBounceMovement.TEMPLATE_MAX_DROP && ceiling.isInfinite()
		if (templated && levels.size < MIN_CONTACTS) return
		if (!tried.add(Attempt(target, levels.toList()))) return
		val dx = target.x - origin.x
		val dz = target.z - origin.z
		if (templated && levels.size <= SlimeBounceMovement.DOUBLE_BOUNCES && (dx == 0 || dz == 0 || abs(dx) == abs(dz))) return
		val probed = BounceArcProbe.probe(
			view, origin, dx, dz, drop, target.y - origin.y, launchHeight,
			bounces = levels.size, reboundHeadroom = ceiling, levels = if (level) null else levels,
		) ?: return
		add(origin, ChainEdge(dx, target.y - origin.y, dz, probed.solution.airTicks.toDouble(), probed.solution, probed.reads))
	}

	private fun add(origin: Stance, edge: ChainEdge) {
		outgoing.getOrPut(key(origin)) { ArrayList() } += edge
		incoming.getOrPut(key(origin.offset(edge.dx, edge.dy, edge.dz))) { ArrayList() } += ChainArrival(origin, edge)
	}

	private fun bend(
		origin: Stance, launchHeight: Double, unitX: Double, unitZ: Double, jump: Boolean, sprint: Boolean, speed: Double,
		still: ArcSample, moving: ArcSample, depth: Double, level: Int, bent: HashMap<Stance, Bent>,
	) {
		if (Footing.involvesPartial(view, origin.x, origin.y, origin.z)) return
		val contact = still.contacts[0]
		val contactAlong = along(still, moving, speed, contact)
		val padX = origin.x + 0.5 + unitX * contactAlong
		val padZ = origin.z + 0.5 + unitZ * contactAlong
		var apexTick = contact + 1
		while (apexTick + 1 < still.heights.size && still.heights[apexTick + 1] > still.heights[apexTick]) apexTick++
		val apex = launchHeight + still.heights[apexTick]
		val padTop = launchHeight - depth
		for (x in floor(padX - BENT_REACH).toInt()..floor(padX + BENT_REACH).toInt()) for (z in floor(padZ - BENT_REACH).toInt()..floor(padZ + BENT_REACH).toInt()) {
			val towardX = x + 0.5 - padX
			val towardZ = z + 0.5 - padZ
			val distance = hypot(towardX, towardZ)
			if (distance > BENT_REACH || distance < MIN_BENT_REACH) continue
			if ((towardX * unitX + towardZ * unitZ) / distance > MAX_BENT_COSINE) continue
			for (y in floor(padTop + MIN_BENT_RISE).toInt() + 1..floor(apex).toInt() + 1) {
				val surface = view.standingSurface(x, y - 1, z) ?: continue
				val height = y - 1 + surface
				if (height < padTop + MIN_BENT_RISE || height > apex) continue
				var landing = apexTick
				while (landing + 1 < still.heights.size && launchHeight + still.heights[landing] > height) landing++
				if (distance > (landing - contact) * BENT_TRAVEL_PER_TICK + BENT_TRAVEL_SLACK) continue
				if (BounceArcProbe.isPad(view, x, y - 1, z)) continue
				val target = Stance(x, y, z)
				if (!standable.isStance(target)) continue
				val probed = probed { reads -> bentFlight(origin, target, height, launchHeight, unitX, unitZ, jump, sprint, speed, still, depth, level, reads) } ?: continue
				val via = probed.solution.via ?: continue
				val contactX = origin.x + 0.5 + via.x
				val contactZ = origin.z + 0.5 + via.z
				val score = hypot(contactX - (floor(contactX) + 0.5), contactZ - (floor(contactZ) + 0.5)) + speed * SPEED_SCORE
				if (score < (bent[target]?.score ?: Double.POSITIVE_INFINITY)) bent[target] = Bent(score, probed.solution, probed.reads)
			}
		}
	}

	private fun bentFlight(
		origin: Stance, target: Stance, height: Double, launchHeight: Double, unitX: Double, unitZ: Double, jump: Boolean, sprint: Boolean, speed: Double,
		still: ArcSample, depth: Double, level: Int, reads: LongOpenHashSet?,
	): BounceSolution? {
		val launchX = origin.x + 0.5 + unitX * BounceSolver.LAUNCH_OFFSET
		val launchZ = origin.z + 0.5 + unitZ * BounceSolver.LAUNCH_OFFSET
		val contact = still.contacts[0]
		val path = steered(target, launchHeight, launchX, launchZ, unitX, unitZ, jump, sprint, speed, still)
		val padX = launchX + path[2 * contact]
		val padZ = launchZ + path[2 * contact + 1]
		reads?.add(BlockPos.asLong(floor(padX).toInt(), level, floor(padZ).toInt()))
		if (!BounceArcProbe.isPad(view, floor(padX).toInt(), level, floor(padZ).toInt())) return null
		val landing = bentLanding(target, height, launchHeight, launchX, launchZ, still, path, reads) ?: return null
		val count = landing + 2
		val xs = DoubleArray(count)
		val feet = DoubleArray(count)
		val zs = DoubleArray(count)
		xs[0] = launchX
		feet[0] = launchHeight
		zs[0] = launchZ
		for (tick in 0..landing) {
			xs[tick + 1] = launchX + path[2 * tick]
			feet[tick + 1] = if (tick == landing) height else launchHeight + still.heights[tick]
			zs[tick + 1] = launchZ + path[2 * tick + 1]
		}
		if (sweepPoints(view, xs, feet, zs, count, reads) != Sweep.Clear) return null

		var slack = if (speed <= BounceSolver.STANDING_REST_SPEED) BounceSolver.STANDING_REST_SPEED else 0.0
		if (speed > BounceSolver.STANDING_REST_SPEED) {
			for (step in SLACK_STEPS) {
				val holds = listOf(speed - step, speed + step).all { candidate ->
					if (candidate < 0.0 || candidate > ballistics.momentumSpeed(sprint)) return@all false
					val shifted = steered(target, launchHeight, launchX, launchZ, unitX, unitZ, jump, sprint, candidate, still)
					BounceArcProbe.isPad(view, floor(launchX + shifted[2 * contact]).toInt(), level, floor(launchZ + shifted[2 * contact + 1]).toInt()) &&
							bentLanding(target, height, launchHeight, launchX, launchZ, still, shifted, null) != null
				}
				if (!holds) break
				slack = step
			}
		}

		val heights = still.heights.copyOf(landing + 1)
		heights[landing] = height - launchHeight
		val distances = DoubleArray(landing + 1)
		var travelled = 0.0
		for (tick in 0..landing) {
			travelled += hypot(xs[tick + 1] - xs[tick], zs[tick + 1] - zs[tick])
			distances[tick] = travelled
		}
		val exit = if (landing == 0) 0.0 else hypot(path[2 * landing] - path[2 * landing - 2], path[2 * landing + 1] - path[2 * landing - 1])
		return BounceSolution(
			sprint = sprint,
			holdForward = true,
			jump = jump,
			holdTicks = Int.MAX_VALUE,
			speed = speed,
			speedSlack = slack,
			launchOffset = BounceSolver.LAUNCH_OFFSET,
			arc = ArcSample(heights, distances, exit, intArrayOf(contact)),
			contactDepth = depth,
			via = Via(padX - (origin.x + 0.5), padZ - (origin.z + 0.5)),
		)
	}

	private fun steered(target: Stance, launchHeight: Double, launchX: Double, launchZ: Double, unitX: Double, unitZ: Double, jump: Boolean, sprint: Boolean, speed: Double, still: ArcSample): DoubleArray =
		ballistics.steeredPath(
			speed, sprint, jump, still.contacts, still.heights.size - 1, unitX, unitZ,
			turnAt = still.contacts[0] + 1, targetX = target.x + 0.5 - launchX, targetZ = target.z + 0.5 - launchZ,
			contactAcceleration = ballistics.groundAcceleration(sprint, Vanilla.SLIME_SLIPPERINESS),
			contactFriction = ballistics.groundFriction(Vanilla.SLIME_SLIPPERINESS),
		) { alongX, x, z, delta, tick -> allowedMove(alongX, launchX + x, launchZ + z, delta, launchHeight + still.heights[tick]) }

	private fun allowedMove(alongX: Boolean, x: Double, z: Double, delta: Double, feet: Double): Double {
		if (delta == 0.0) return 0.0
		val half = Vanilla.PLAYER_HALF_WIDTH.toDouble()
		val lead = (if (alongX) x else z) + if (delta > 0.0) half else -half
		val minSide = (if (alongX) z else x) - half
		val maxSide = (if (alongX) z else x) + half
		val top = feet + Vanilla.PLAYER_HEIGHT
		var allowed = delta
		for (cell in floor(minOf(lead, lead + delta)).toInt()..floor(maxOf(lead, lead + delta)).toInt()) {
			for (side in floor(minSide + Vanilla.COLLISION_EPSILON).toInt()..floor(maxSide - Vanilla.COLLISION_EPSILON).toInt()) {
				for (y in floor(feet + Vanilla.COLLISION_EPSILON).toInt()..floor(top - Vanilla.COLLISION_EPSILON).toInt()) {
					val cx = if (alongX) cell else side
					val cz = if (alongX) side else cell
					val boxes = when (view.collisionClass(cx, y, cz)) {
						CollisionClass.EMPTY -> continue
						CollisionClass.FULL, CollisionClass.UNKNOWN -> FULL_BOX
						CollisionClass.PARTIAL -> view.collisionBoxes(cx, y, cz) ?: FULL_BOX
					}
					for (box in 0 until boxes.size / 6) {
						val b = 6 * box
						if (y + boxes[b + 4] <= feet + Vanilla.COLLISION_EPSILON || y + boxes[b + 1] >= top - Vanilla.COLLISION_EPSILON) continue
						val sideMin = (if (alongX) cz else cx) + boxes[b + if (alongX) 2 else 0]
						val sideMax = (if (alongX) cz else cx) + boxes[b + if (alongX) 5 else 3]
						if (sideMax <= minSide + Vanilla.COLLISION_EPSILON || sideMin >= maxSide - Vanilla.COLLISION_EPSILON) continue
						val faceMin = cell + boxes[b + if (alongX) 0 else 2]
						val faceMax = cell + boxes[b + if (alongX) 3 else 5]
						if (delta > 0.0 && faceMin >= lead - Vanilla.COLLISION_EPSILON) allowed = minOf(allowed, faceMin - lead)
						if (delta < 0.0 && faceMax <= lead + Vanilla.COLLISION_EPSILON) allowed = maxOf(allowed, faceMax - lead)
					}
				}
			}
		}
		return allowed
	}

	private fun bentLanding(target: Stance, height: Double, launchHeight: Double, launchX: Double, launchZ: Double, still: ArcSample, path: DoubleArray, reads: LongOpenHashSet?): Int? {
		val half = Vanilla.PLAYER_HALF_WIDTH.toDouble()
		for (tick in still.contacts[0] + 1 until still.heights.size) {
			val above = launchHeight + still.heights[tick - 1]
			val below = launchHeight + still.heights[tick]
			if (below >= above) continue
			val centerX = launchX + path[2 * tick]
			val centerZ = launchZ + path[2 * tick + 1]
			var landedX = 0
			var landedY = 0
			var landedZ = 0
			var landed = Double.NEGATIVE_INFINITY
			for (x in floor(centerX - half + Vanilla.COLLISION_EPSILON).toInt()..floor(centerX + half - Vanilla.COLLISION_EPSILON).toInt()) {
				for (z in floor(centerZ - half + Vanilla.COLLISION_EPSILON).toInt()..floor(centerZ + half - Vanilla.COLLISION_EPSILON).toInt()) {
					for (y in floor(above).toInt() + 1 downTo floor(below).toInt()) {
						reads?.add(BlockPos.asLong(x, y - 1, z))
						val surface = view.standingSurface(x, y - 1, z) ?: continue
						val crossed = y - 1 + surface
						if (crossed > above || crossed < below) continue
						if (crossed > landed) {
							landed = crossed
							landedX = x
							landedY = y
							landedZ = z
						}
						break
					}
				}
			}
			if (landed == Double.NEGATIVE_INFINITY) continue
			val onTarget = landedY == target.y && abs(landedX - target.x) <= 1 && abs(landedZ - target.z) <= 1 && abs(landed - height) < LANDING_EPSILON
			return tick.takeIf { onTarget && !BounceArcProbe.isPad(view, landedX, landedY - 1, landedZ) && standable.isStance(Stance(landedX, landedY, landedZ)) }
		}
		return null
	}

	private fun firstSurface(origin: Stance, launchHeight: Double, unitX: Double, unitZ: Double, still: ArcSample, moving: ArcSample, speed: Double, start: Int): Hit? {
		for (tick in maxOf(1, start) until still.heights.size) {
			val above = launchHeight + still.heights[tick - 1]
			val below = launchHeight + still.heights[tick]
			if (below >= above) continue
			val distance = along(still, moving, speed, tick)
			val x = floor(origin.x + 0.5 + unitX * distance).toInt()
			val z = floor(origin.z + 0.5 + unitZ * distance).toInt()
			for (y in floor(above).toInt() + 1 downTo floor(below).toInt()) {
				val surface = standingSurface(x, y - 1, z)
				if (surface.isNaN()) continue
				val height = y - 1 + surface
				if (height > above || height < below) continue
				if (BounceArcProbe.isPad(view, x, y - 1, z)) return Hit(x, y, z, pad = true, surface = height)
				return Hit(x, y, z, pad = false, surface = height).takeIf { standable.isStance(Stance(x, y, z)) }
			}
		}
		return null
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

	private fun standingSurface(x: Int, y: Int, z: Int): Double {
		surfaceWatch.poll(::forgetSurfaces) { box -> if (coversSurfaces(box)) forgetSurfaces() }
		val bottom = y and SEGMENT_MASK.inv()
		val key = BlockPos.asLong(x, bottom, z)
		val segment = lastSurfaces?.takeIf { lastSurfaceKey == key } ?: surfaces.get(key) ?: DoubleArray(SEGMENT_HEIGHT) { index ->
			view.standingSurface(x, bottom + index, z) ?: Double.NaN
		}.also {
			val bounds = surfaceBounds
			if (surfaces.isEmpty()) {
				bounds[0] = x
				bounds[1] = bottom
				bounds[2] = z
				bounds[3] = x
				bounds[4] = bottom + SEGMENT_MASK
				bounds[5] = z
			} else {
				bounds[0] = minOf(bounds[0], x)
				bounds[1] = minOf(bounds[1], bottom)
				bounds[2] = minOf(bounds[2], z)
				bounds[3] = maxOf(bounds[3], x)
				bounds[4] = maxOf(bounds[4], bottom + SEGMENT_MASK)
				bounds[5] = maxOf(bounds[5], z)
			}
			surfaces.put(key, it)
		}
		lastSurfaceKey = key
		lastSurfaces = segment
		return segment[y and SEGMENT_MASK]
	}

	private fun forgetSurfaces() {
		surfaces.clear()
		lastSurfaces = null
	}

	private fun coversSurfaces(box: CellBox): Boolean {
		val bounds = surfaceBounds
		return !surfaces.isEmpty() && box.overlaps(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5])
	}

	private fun ceilingBottom(x: Int, y: Int, z: Int): Double? = when (view.collisionClass(x, y, z)) {
		CollisionClass.EMPTY -> null
		CollisionClass.FULL, CollisionClass.UNKNOWN -> y.toDouble()
		CollisionClass.PARTIAL -> view.collisionBoxes(x, y, z)?.let { boxes -> y + (0 until boxes.size / 6).minOf { boxes[6 * it + 1] } }
	}

	private fun along(still: ArcSample, moving: ArcSample, speed: Double, tick: Int): Double =
		BounceSolver.LAUNCH_OFFSET + still.distances[tick] + speed * (moving.distances[tick] - still.distances[tick])

	private fun key(stance: Stance): Long = BlockPos.asLong(stance.x, stance.y, stance.z)

	private companion object {
		val ballistics = BallisticProfile.VANILLA

		val TEMPLATE = MotionTemplateId(Int.MAX_VALUE - 1)

		val NO_EDGES = emptyList<ChainEdge>()

		val NO_ARRIVALS = emptyList<ChainArrival>()

		val STYLES = listOf(true to true, true to false, false to true, false to false)

		const val FIRST_REACH = 10

		const val MIN_FIRST_REACH = 0.5

		const val CHAIN_REACH = 40

		const val MIN_CONTACTS = 2

		const val MAX_CONTACTS = 4

		const val SPEED_STEPS = 8

		const val BEARING_BINS = 720L

		const val BELOW_PADS = 1.0

		val FOOTPRINT_SAMPLES = doubleArrayOf(-Vanilla.PLAYER_HALF_WIDTH.toDouble(), 0.0, Vanilla.PLAYER_HALF_WIDTH.toDouble())

		const val CEILING_EPSILON = 1.0E-6

		const val SEGMENT_HEIGHT = 16

		const val SEGMENT_MASK = SEGMENT_HEIGHT - 1

		const val BENT_REACH = 6.0

		const val MIN_BENT_REACH = 0.5

		val MAX_BENT_COSINE = kotlin.math.cos(Math.toRadians(15.0))

		const val MIN_BENT_RISE = 0.5

		const val LANDING_EPSILON = 1.0E-6

		val FULL_BOX = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)

		val SLACK_STEPS = doubleArrayOf(0.005, 0.01, 0.02, 0.04)

		const val SPEED_SCORE = 0.5

		val BENT_SPEED_STEPS = intArrayOf(0, SPEED_STEPS / 2, SPEED_STEPS)

		const val BENT_TRAVEL_PER_TICK = 0.3

		const val BENT_TRAVEL_SLACK = 1.0
	}

	private class Hit(val x: Int, val y: Int, val z: Int, val pad: Boolean, val surface: Double)

	private class Bent(val score: Double, val solution: BounceSolution, val reads: () -> LongOpenHashSet)

	private data class Attempt(val target: Stance, val levels: List<Int>)
}
