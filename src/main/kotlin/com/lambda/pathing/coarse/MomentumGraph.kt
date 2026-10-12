package com.lambda.pathing.coarse

import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.graph.EdgeList

enum class SpeedClass {
	STOPPED, MOVING;

	companion object {

		private const val MOVING_THRESHOLD_BLOCKS_PER_TICK = 0.15

		fun of(speed: Double): SpeedClass =
			if (speed >= MOVING_THRESHOLD_BLOCKS_PER_TICK) MOVING else STOPPED
	}
}

internal class MomentumRules(templates: List<MotionTemplate>) {

	private val departsStoppedOnly = BooleanArray(templates.size) { templates[it].movement in STOPPED_DEPARTURES }

	private val arrival = Array(templates.size) { if (templates[it].movement in STOPPED_ARRIVALS) SpeedClass.STOPPED else SpeedClass.MOVING }

	private val sharesNeighbor = templates.groupBy { listOf(it.dx, it.dy, it.dz, arrival[it.id.value].ordinal) }.values
		.filter { it.size > 1 }.flatten().let { shared -> BooleanArray(templates.size).also { flags -> shared.forEach { flags[it.id.value] = true } } }

	private val searchCaps = HeuristicCaps.of(templates, ::costFloor)

	private val groundCaps = HeuristicCaps.of(templates.filter { it.bounceDrop == null }, ::costFloor)

	fun heuristic(from: Long, to: Long, grounded: Boolean): Double = (if (grounded) groundCaps else searchCaps).estimate(
		PackedStance.unpackX(to) - PackedStance.unpackX(from),
		PackedStance.unpackY(to) - PackedStance.unpackY(from),
		PackedStance.unpackZ(to) - PackedStance.unpackZ(from),
	)

	private fun costFloor(template: MotionTemplate): Double =
		if (departsStoppedOnly[template.id.value]) template.minimumTicks else movingCost(template.minimumTicks)

	private fun movingCost(ticks: Double): Double =
		(ticks - CHAIN_TAX_TICKS).coerceAtLeast(ticks * MIN_CHAINED_COST_FRACTION)

	private fun stoppedCost(template: Int, ticks: Double): Double =
		if (departsStoppedOnly[template]) ticks else ticks + STARTUP_TICKS

	fun successors(edges: CoarseEdgeCache, node: Long, out: EdgeList, settledOnly: Boolean = false, reached: java.util.function.LongPredicate? = null) {
		val speed = PackedStance.speed(node)
		if (speed == SpeedClass.MOVING) {
			out.add(PackedStance.withSpeed(node, SpeedClass.STOPPED), BRAKE_TICKS)
		}
		val x = PackedStance.unpackX(node)
		val y = PackedStance.unpackY(node)
		val z = PackedStance.unpackZ(node)
		val visitor = EdgeVisitor { template, ticks, standingStart ->
			val id = template.id.value
			if (speed == SpeedClass.MOVING && departsStoppedOnly[id]) return@EdgeVisitor
			if (speed == SpeedClass.STOPPED && !standingStart) return@EdgeVisitor
			val cost = when (speed) {
				SpeedClass.MOVING -> movingCost(ticks)
				SpeedClass.STOPPED -> stoppedCost(id, ticks)
			}
			val target = PackedStance.pack(x + template.dx, y + template.dy, z + template.dz, arrival[id])
			if (sharesNeighbor[id]) out.addMin(target, cost) else out.append(target, cost)
		}
		if (settledOnly) return edges.forEachSettledEdgeFrom(node, visitor)
		val wanted = reached?.let { reach ->
			PendingFilter { template ->
				val id = template.id.value
				!(speed == SpeedClass.MOVING && departsStoppedOnly[id]) && reach.test(PackedStance.pack(x + template.dx, y + template.dy, z + template.dz, arrival[id]))
			}
		}
		edges.forEachEdgeFrom(node, wanted, visitor)
		edges.forEachFallFrom(node) { _, fall ->
			if (speed == SpeedClass.STOPPED && !fall.standingStart) return@forEachFallFrom
			val ticks = if (speed == SpeedClass.MOVING) movingCost(fall.ticks) else fall.ticks + STARTUP_TICKS
			out.addMin(PackedStance.pack(x + fall.dx, y + fall.dy, z + fall.dz, fallArrival(fall)), fallCost(fall, ticks))
		}
	}

	fun predecessors(edges: CoarseEdgeCache, node: Long, out: EdgeList, wanted: java.util.function.LongPredicate? = null) {
		val speed = PackedStance.speed(node)
		if (speed == SpeedClass.STOPPED) {
			out.add(PackedStance.withSpeed(node, SpeedClass.MOVING), BRAKE_TICKS)
		}
		val x = PackedStance.unpackX(node)
		val y = PackedStance.unpackY(node)
		val z = PackedStance.unpackZ(node)
		val pending = PendingFilter { template ->
			val id = template.id.value
			if (arrival[id] != speed) return@PendingFilter false
			val origins = wanted ?: return@PendingFilter true
			val originX = x - template.dx
			val originY = y - template.dy
			val originZ = z - template.dz
			!departsStoppedOnly[id] && origins.test(PackedStance.pack(originX, originY, originZ, SpeedClass.MOVING)) ||
					origins.test(PackedStance.pack(originX, originY, originZ, SpeedClass.STOPPED))
		}
		edges.forEachEdgeTo(node, pending) { template, ticks, standingStart ->
			val id = template.id.value
			if (arrival[id] != speed) return@forEachEdgeTo
			val originX = x - template.dx
			val originY = y - template.dy
			val originZ = z - template.dz
			val shared = sharesNeighbor[id]
			if (!departsStoppedOnly[id]) {
				val origin = PackedStance.pack(originX, originY, originZ, SpeedClass.MOVING)
				if (shared) out.addMin(origin, movingCost(ticks)) else out.append(origin, movingCost(ticks))
			}
			if (standingStart) {
				val origin = PackedStance.pack(originX, originY, originZ, SpeedClass.STOPPED)
				if (shared) out.addMin(origin, stoppedCost(id, ticks)) else out.append(origin, stoppedCost(id, ticks))
			}
		}
		edges.forEachFallTo(node) { origin, fall ->
			if (fallArrival(fall) != speed) return@forEachFallTo
			out.addMin(PackedStance.pack(origin, SpeedClass.MOVING), fallCost(fall, movingCost(fall.ticks)))
			if (fall.standingStart) out.addMin(PackedStance.pack(origin, SpeedClass.STOPPED), fallCost(fall, fall.ticks + STARTUP_TICKS))
		}
	}

	private fun fallArrival(fall: FallEdge): SpeedClass = if (fall.water) SpeedClass.STOPPED else SpeedClass.MOVING

	private fun fallCost(fall: FallEdge, ticks: Double): Double = maxOf(ticks, groundCaps.estimate(fall.dx, fall.dy, fall.dz))

	private companion object {

		val STOPPED_DEPARTURES = setOf(MovementId.CLIMB, MovementId.SWIM, MovementId.EXIT_WATER)

		val STOPPED_ARRIVALS = STOPPED_DEPARTURES + setOf(MovementId.STEP_UP, MovementId.LADDER_CATCH, MovementId.ENTER_WATER)

		const val STARTUP_TICKS = 2.0

		const val BRAKE_TICKS = 4.0

		const val CHAIN_TAX_TICKS = 1.0

		const val MIN_CHAINED_COST_FRACTION = 0.5
	}
}
