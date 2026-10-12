package com.lambda.pathing.search

import com.lambda.pathing.rollout.SimulatedTrajectoryFrame

import com.lambda.pathing.actions.BodyState
import com.lambda.pathing.actions.PricedDecision
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import net.minecraft.util.math.Vec3d

internal class ValueAnchor(
	override val state: MovementSimulationState,
	override val stance: Stance,
	val elapsed: Int,
	val collisionEvents: Int,
	val inputSwitches: Int,
	val parent: ValueAnchor?,
	val inputs: List<MovementSimulationInput>,
	val decision: TrajectoryDecision? = null,
	val trace: List<Vec3d> = emptyList(),
	val jumps: Int = 0,
) : BodyState {
	override val speed: Double = state.velocity.horizontalLength()

	var legRoot: Boolean = false

	var actions: List<PricedDecision>? = null

	var actionsHazardFrame: Int? = null

	var actionsEpoch: Int = -1

	var actionCursor: Int = 0

	var pendingSurcharge: Double = 0.0

	private val attempted = HashSet<TrajectoryDecision>()

	fun attempt(decision: TrajectoryDecision): Boolean = attempted.add(decision)

	fun hasAttempted(decision: TrajectoryDecision): Boolean = decision in attempted

	fun retry(decision: TrajectoryDecision) {
		attempted.remove(decision)
		actionCursor = 0
	}

	fun retryAll() {
		attempted.clear()
		actionCursor = 0
	}

	override var hazardFrame: Int? = null

	var sweptEpoch: Int = -1

	var divergenceElapsed: Int = -1
	var divergenceSequence: Long = Long.MIN_VALUE

	var momentumEpoch: Int = -1
	var momentumCredit: Double = Double.NaN
	var momentumTurnCost: Double = 0.0

	fun continuation() = ValueAnchor(state, stance, elapsed, collisionEvents, inputSwitches, parent, inputs, decision, trace, jumps)

	fun restingChild() = ValueAnchor(state, stance, elapsed, collisionEvents, inputSwitches, this, emptyList(), jumps = jumps)

	fun lineFrom(root: ValueAnchor?): List<ValueAnchor> =
		generateSequence(this) { it.parent }
			.takeWhile { it !== root && it.parent != null }
			.toList()
			.asReversed()

	fun descendsFrom(other: ValueAnchor): Boolean {
		var node: ValueAnchor? = this
		while (node != null && node.elapsed >= other.elapsed) {
			if (node === other) return true
			node = node.parent
		}
		return false
	}

	fun ancestorAtOrBefore(frame: Int): ValueAnchor? {
		var node: ValueAnchor? = this
		while (node != null && node.elapsed > frame) node = node.parent
		return node
	}

	fun ancestorAt(frame: Int): ValueAnchor? = ancestorAtOrBefore(frame)?.takeIf { it.elapsed == frame }

}

internal fun ValueAnchor.arrivalEstimate(guide: Double, allowance: Double = 0.0): Double =
	elapsed + guide + allowance + Solution.COLLISION_FRAME_PENALTY * collisionEvents

internal fun ValueField.guideFor(anchor: ValueAnchor): Double = guide(anchor.stance, SpeedClass.of(anchor.speed))

internal fun SearchProbe.traceOf(frames: List<SimulatedTrajectoryFrame>): List<Vec3d> {
	if (!treeEnabled && !candidatesEnabled || frames.size <= TRACE_STRIDE + 1) return emptyList()
	val trace = ArrayList<Vec3d>(frames.size / TRACE_STRIDE + 1)
	for (i in TRACE_STRIDE until frames.size - 1 step TRACE_STRIDE) trace += frames[i].state.position
	return trace
}

private const val TRACE_STRIDE = 3
