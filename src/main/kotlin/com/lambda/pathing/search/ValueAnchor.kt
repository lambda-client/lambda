package com.lambda.pathing.search

import com.lambda.pathing.rollout.SimulatedTrajectoryFrame

import com.lambda.pathing.actions.BodyState
import com.lambda.pathing.actions.PricedDecision
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState

internal class ValueAnchor(
	override val state: MovementSimulationState,
	override val stance: Stance,
	val elapsed: Int,
	val collisionEvents: Int,
	val launchMargin: Int,
	val inputSwitches: Int,
	val parent: ValueAnchor?,
	val inputs: List<MovementSimulationInput>,
	val boundary: Int,
) : BodyState {

	override val speed: Double = state.velocity.horizontalLength()

	var via: MovementId? = null

	var decision: TrajectoryDecision? = null

	var points: List<HorizontalPoint> = emptyList()

	var airborneCollisionEvents: Int = 0

	var legRoot: Boolean = false

	var trace: List<net.minecraft.util.math.Vec3d> = emptyList()

	companion object {

		fun traceOf(frames: List<SimulatedTrajectoryFrame>): List<net.minecraft.util.math.Vec3d> {
			if (frames.size <= TRACE_STRIDE + 1) return emptyList()
			val trace = ArrayList<net.minecraft.util.math.Vec3d>(frames.size / TRACE_STRIDE + 1)
			for (i in TRACE_STRIDE until frames.size - 1 step TRACE_STRIDE) trace += frames[i].state.position
			return trace
		}

		private const val TRACE_STRIDE = 3
	}

	var actions: List<PricedDecision>? = null

	var actionsHazardFrame: Int? = null

	var actionsEpoch: Int = -1

	var pendingSurcharge: Double = 0.0

	val attempted: MutableSet<TrajectoryDecision> = HashSet()

	val familyPrefixFailures: MutableMap<Any, Int> = HashMap()

	override var hazardFrame: Int? = null

	var sweptEpoch: Int = -1

	var divergenceElapsed: Int = -1
	var divergenceSequence: Long = Long.MIN_VALUE

	var momentumEpoch: Int = -1
	var momentumCredit: Double = Double.NaN
	var momentumTurnCost: Double = 0.0

	fun continuation() = ValueAnchor(
		state, stance, elapsed, collisionEvents, launchMargin, inputSwitches, parent, inputs, boundary,
	).also {
		it.via = via
		it.decision = decision
		it.points = points
		it.trace = trace
	}

	fun prefix(): List<MovementSimulationInput> {
		val chain = ArrayList<List<MovementSimulationInput>>()
		var node: ValueAnchor? = this
		while (node != null) {
			if (node.inputs.isNotEmpty()) chain += node.inputs
			node = node.parent
		}
		chain.reverse()
		return chain.flatten()
	}

	fun boundaries(): List<Int> {
		val frames = ArrayList<Int>()
		var node: ValueAnchor? = this
		while (node?.parent != null) {
			frames += node.boundary
			node = node.parent
		}
		return frames.asReversed().dropLast(1)
	}

	fun depth(): Int {
		var depth = 0
		var node: ValueAnchor? = this
		while (node?.parent != null) {
			depth++
			node = node.parent
		}
		return depth
	}

	fun descendsFrom(other: ValueAnchor): Boolean {
		var node: ValueAnchor? = this
		while (node != null) {
			if (node === other) return true
			node = node.parent
		}
		return false
	}

	fun hasVisited(visited: Stance): Boolean {
		var node: ValueAnchor? = this
		while (node != null) {
			if (node.stance == visited) return true
			if (node.legRoot) return false
			node = node.parent
		}
		return false
	}
}
