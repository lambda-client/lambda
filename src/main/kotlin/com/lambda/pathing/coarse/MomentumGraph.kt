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

	fun successors(edges: CoarseEdgeCache, node: Long, out: EdgeList) {
		val speed = PackedStance.speed(node)
		if (speed == SpeedClass.MOVING) {
			out.add(PackedStance.withSpeed(node, SpeedClass.STOPPED), BRAKE_TICKS)
		}
		val x = PackedStance.unpackX(node)
		val y = PackedStance.unpackY(node)
		val z = PackedStance.unpackZ(node)
		edges.forEachEdgeFrom(node) { template, ticks, standingStart ->
			val id = template.id.value
			if (speed == SpeedClass.MOVING && departsStoppedOnly[id]) return@forEachEdgeFrom
			if (speed == SpeedClass.STOPPED && !standingStart) return@forEachEdgeFrom
			val cost = when (speed) {
				SpeedClass.MOVING -> movingCost(ticks)
				SpeedClass.STOPPED -> stoppedCost(id, ticks)
			}
			out.addMin(PackedStance.pack(x + template.dx, y + template.dy, z + template.dz, arrival[id]), cost)
		}
	}

	fun predecessors(edges: CoarseEdgeCache, node: Long, out: EdgeList) {
		val speed = PackedStance.speed(node)
		if (speed == SpeedClass.STOPPED) {
			out.add(PackedStance.withSpeed(node, SpeedClass.MOVING), BRAKE_TICKS)
		}
		val x = PackedStance.unpackX(node)
		val y = PackedStance.unpackY(node)
		val z = PackedStance.unpackZ(node)
		edges.forEachEdgeTo(node) { template, ticks, standingStart ->
			val id = template.id.value
			if (arrival[id] != speed) return@forEachEdgeTo
			val originX = x - template.dx
			val originY = y - template.dy
			val originZ = z - template.dz
			if (!departsStoppedOnly[id]) {
				out.addMin(PackedStance.pack(originX, originY, originZ, SpeedClass.MOVING), movingCost(ticks))
			}
			if (standingStart) {
				out.addMin(PackedStance.pack(originX, originY, originZ, SpeedClass.STOPPED), stoppedCost(id, ticks))
			}
		}
	}

	private companion object {

		val STOPPED_DEPARTURES = setOf(MovementId.CLIMB, MovementId.SWIM, MovementId.EXIT_WATER)

		val STOPPED_ARRIVALS = STOPPED_DEPARTURES + setOf(MovementId.STEP_UP, MovementId.LADDER_CATCH, MovementId.ENTER_WATER)

		const val STARTUP_TICKS = 2.0

		const val BRAKE_TICKS = 4.0

		const val CHAIN_TAX_TICKS = 1.0

		const val MIN_CHAINED_COST_FRACTION = 0.5
	}
}
