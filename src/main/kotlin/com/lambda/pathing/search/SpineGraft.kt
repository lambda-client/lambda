package com.lambda.pathing.search

import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationInput
import kotlin.math.roundToInt

internal sealed interface SpineStep {
	val leg: Int

	class Decide(val decision: TrajectoryDecision, override val leg: Int) : SpineStep

	class Replay(val inputs: List<MovementSimulationInput>, override val leg: Int) : SpineStep
}

internal class GraftContext(
	val step: (ValueAnchor, TrajectoryDecision) -> Outcome,
	val replay: (ValueAnchor, List<MovementSimulationInput>) -> ValueAnchor?,
	val alternative: (ValueAnchor) -> TrajectoryDecision?,
	val finish: ((ValueAnchor) -> Solution?)?,
)

internal class SpineGraft(private val steps: List<SpineStep>, private val contextOf: (Int) -> GraftContext) {
	private val stances = ArrayList<Stance>(steps.size)
	private val elapsed = ArrayList<Int>(steps.size)
	private val byStance = HashMap<Stance, Int>()
	private val tried = HashSet<Attempt>()

	private data class Attempt(val stance: Stance, val elapsed: Int, val speed: Int)

	var attempts = 0
		private set

	var grafted = 0
		private set

	fun plant(root: ValueAnchor): Solution? {
		var anchor = root
		for ((index, step) in steps.withIndex()) {
			val next = when (step) {
				is SpineStep.Decide -> when (val outcome = contextOf(step.leg).step(anchor, step.decision)) {
					is Outcome.Anchored -> outcome.anchor
					is Outcome.Arrived -> return Solution(anchor, outcome.frames)
					else -> return null
				}

				is SpineStep.Replay -> contextOf(step.leg).replay(anchor, step.inputs) ?: return null
			}
			if (!markLeg(next, index)) return null
			anchor = next
			stances += anchor.stance
			elapsed += anchor.elapsed
			if (step is SpineStep.Decide) byStance[anchor.stance] = index
		}
		return finish(anchor)
	}

	fun graft(landed: ValueAnchor): Solution? {
		val index = byStance[landed.stance] ?: return null
		if (boundary(index) || landed.elapsed + GRAFT_MARGIN_FRAMES > elapsed[index]) return null
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
				is SpineStep.Decide -> when (val outcome = advance(anchor, step.decision, stances[k], context)) {
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

	private fun boundary(index: Int): Boolean = index + 1 < steps.size && steps[index + 1].leg != steps[index].leg

	private fun markLeg(anchor: ValueAnchor, index: Int): Boolean {
		if (!boundary(index)) return true
		if (!pressesCell(anchor.state, stances.getOrNull(index) ?: anchor.stance)) return false
		anchor.legRoot = true
		return true
	}

	private fun advance(anchor: ValueAnchor, decision: TrajectoryDecision, target: Stance, context: GraftContext): Outcome? {
		val first = context.step(anchor, decision)
		if (first is Outcome.Arrived || first is Outcome.Anchored && first.anchor.stance == target) return first
		var tried = 0
		while (tried < MAX_ALTERNATIVES) {
			val alternative = context.alternative(anchor) ?: return null
			if (alternative == decision || alternative.step != target) continue
			tried++
			val outcome = context.step(anchor, alternative)
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
