package com.lambda.pathing.search

import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.rollout.TrajectoryDiagnostic
import kotlin.math.roundToInt

internal sealed interface SpineStep {
	val leg: Int

	class Decide(val decision: TrajectoryDecision, override val leg: Int, val memo: PlantMemo? = null, val chain: List<Stance>? = null) : SpineStep

	class Replay(val inputs: List<MovementSimulationInput>, override val leg: Int) : SpineStep
}

internal class PlantMemo {
	private var from: ValueAnchor? = null
	private var landed: ValueAnchor? = null

	fun recall(anchor: ValueAnchor): ValueAnchor? {
		val from = from ?: return null
		val landed = landed ?: return null
		if (anchor.state != from.state || anchor.stance != from.stance || anchor.inputs.lastOrNull() != from.inputs.lastOrNull()) return null
		return ValueAnchor(
			state = landed.state,
			stance = landed.stance,
			elapsed = anchor.elapsed + landed.elapsed - from.elapsed,
			collisionEvents = anchor.collisionEvents + landed.collisionEvents - from.collisionEvents,
			inputSwitches = anchor.inputSwitches + landed.inputSwitches - from.inputSwitches,
			parent = anchor,
			inputs = landed.inputs,
			decision = landed.decision,
			trace = landed.trace,
			jumps = anchor.jumps + landed.jumps - from.jumps,
			chain = landed.chain,
		)
	}

	fun keep(from: ValueAnchor, landed: ValueAnchor) {
		this.from = from
		this.landed = landed
	}
}

internal class GraftContext(
	val step: (ValueAnchor, TrajectoryDecision, List<Stance>?) -> Outcome,
	val replay: (ValueAnchor, List<MovementSimulationInput>) -> ValueAnchor?,
	val alternative: (ValueAnchor) -> TrajectoryDecision?,
	val finish: ((ValueAnchor) -> Solution?)?,
)

internal class SpineGraft(private val steps: List<SpineStep>, private val contextOf: (Int) -> GraftContext) {
	private val stances = ArrayList<Stance>(steps.size)
	private val elapsed = ArrayList<Int>(steps.size)
	private val byStance = HashMap<Stance, MutableList<Int>>()
	private val tried = HashSet<Attempt>()
	private val firstLeg = steps.firstOrNull()?.leg ?: 0

	private data class Attempt(val stance: Stance, val elapsed: Int, val speed: Int)

	var attempts = 0
		private set

	var grafted = 0
		private set

	private var rooted = false

	var plantFailure: String? = null
		private set

	fun plant(root: ValueAnchor): Solution? {
		var anchor = root
		for ((index, step) in steps.withIndex()) {
			val next = when (step) {
				is SpineStep.Decide -> step.memo?.recall(anchor) ?: when (val outcome = contextOf(step.leg).step(anchor, step.decision, step.chain)) {
					is Outcome.Anchored -> outcome.anchor.also { step.memo?.keep(anchor, it) }
					is Outcome.Arrived -> return Solution(anchor, outcome.frames)
					is Outcome.Rejected -> return failPlant("rejected ${rejection(outcome.diagnostic)}", index)
					is Outcome.Blocked -> return failPlant("blocked", index)
				}

				is SpineStep.Replay -> contextOf(step.leg).replay(anchor, step.inputs) ?: return failPlant("replay", index)
			}
			if (!markLeg(next, index)) return failPlant("leg", index)
			anchor = next
			stances += anchor.stance
			elapsed += anchor.elapsed
			if (step is SpineStep.Decide) byStance.getOrPut(anchor.stance) { ArrayList(1) } += index
		}
		rooted = true
		return finish(anchor) ?: failPlant("finish", steps.size)
	}

	private fun rejection(diagnostic: TrajectoryDiagnostic): String = when {
		diagnostic is TrajectoryDiagnostic.FellBelowRoute -> if (diagnostic.depth > 0.0) "FellBelowRoute floor" else "FellBelowRoute unmapped"
		else -> diagnostic::class.simpleName.orEmpty()
	}

	private fun failPlant(reason: String, index: Int): Solution? {
		plantFailure = "$reason ${if (index * 4 < steps.size) "early" else if (index * 4 >= steps.size * 3) "late" else "mid"}"
		return null
	}

	fun lead(anchor: ValueAnchor): Int? = indexOf(anchor) { true }?.let { elapsed[it] - anchor.elapsed }

	fun graft(landed: ValueAnchor): Solution? {
		if (!rooted) return null
		val index = indexOf(landed) { !boundary(it) && landed.elapsed + GRAFT_MARGIN_FRAMES <= elapsed[it] } ?: return null
		if (!tried.add(Attempt(landed.stance, landed.elapsed, (landed.speed * SPEED_KEY).roundToInt()))) return null
		attempts++
		return follow(landed, index + 1)?.also { grafted++ }
	}

	private fun follow(start: ValueAnchor, from: Int): Solution? {
		var anchor = start
		for (k in from until steps.size) {
			val step = steps[k]
			val context = contextOf(step.leg)
			val next = when (step) {
				is SpineStep.Decide -> when (val outcome = advance(anchor, step, stances[k], context)) {
					is Outcome.Anchored -> outcome.anchor
					is Outcome.Arrived -> return Solution(anchor, outcome.frames)
					else -> return null
				}

				is SpineStep.Replay -> context.replay(anchor, step.inputs) ?: return null
			}
			if (!markLeg(next, k)) return null
			anchor = next
			if (anchor.elapsed > elapsed[k] + LEAD_SLACK_FRAMES) return null
		}
		return finish(anchor)
	}

	private fun finish(anchor: ValueAnchor): Solution? = contextOf(steps.lastOrNull()?.leg ?: 0).finish?.invoke(anchor)

	private inline fun indexOf(anchor: ValueAnchor, admits: (Int) -> Boolean): Int? {
		val indices = byStance[anchor.stance] ?: return null
		if (indices.none(admits)) return null
		val leg = firstLeg + generateSequence(anchor) { it.parent }.count { it.legRoot }
		return indices.lastOrNull { steps[it].leg == leg }?.takeIf(admits)
	}

	private fun boundary(index: Int): Boolean = index + 1 < steps.size && steps[index + 1].leg != steps[index].leg

	private fun markLeg(anchor: ValueAnchor, index: Int): Boolean {
		if (!boundary(index)) return true
		if (!pressesCell(anchor.state, stances.getOrNull(index) ?: anchor.stance)) return false
		anchor.legRoot = true
		return true
	}

	private fun advance(anchor: ValueAnchor, step: SpineStep.Decide, target: Stance, context: GraftContext): Outcome? {
		val decision = step.decision
		val first = context.step(anchor, decision, step.chain)
		if (first is Outcome.Arrived || first is Outcome.Anchored && first.anchor.stance == target) return first
		var tried = 0
		while (tried < MAX_ALTERNATIVES) {
			val alternative = context.alternative(anchor) ?: return null
			if (alternative == decision || alternative.step != target) continue
			tried++
			val outcome = context.step(anchor, alternative, null)
			if (outcome is Outcome.Arrived || outcome is Outcome.Anchored && outcome.anchor.stance == target) return outcome
		}
		return null
	}

	private companion object {
		const val GRAFT_MARGIN_FRAMES = 2

		const val LEAD_SLACK_FRAMES = 2

		const val MAX_ALTERNATIVES = 8

		const val SPEED_KEY = 100.0
	}
}
