package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.SteeringField
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView

/**
 * What the trajectory search reads of the coarse layer: the value labels descending to one
 * goal, the lazy edges, and steering chains over them. [CoarseValueField] is the one real
 * implementation; [SwitchableValueField] lets a running search change goal when a compound
 * route hands over from one leg to the next.
 */
interface ValueField : SteeringField {
	val view: CoarseVoxelView
	val goal: Stance

	fun invalidate(sections: Set<PathingSection>)
	fun lowerBound(stance: Stance): Double
	fun clearGuideCache()
	fun guide(stance: Stance): Double
	fun guide(stance: Stance, speed: SpeedClass): Double
	fun isMapped(stance: Stance): Boolean
	fun edgesFrom(stance: Stance): List<CoarseEdge>
	fun isStance(stance: Stance): Boolean
	fun steps(
		stance: Stance,
		count: Int,
		marginTicks: Double = Double.MAX_VALUE,
		heading: Pair<Double, Double>? = null,
	): List<CoarseEdge>

	fun reachesGoal(chain: List<Stance>): Boolean
}

/**
 * A value field whose target can be swapped under a running search: every read goes to
 * [current]. The search holds one reference for its whole life; a leg handover replaces
 * the delegate and re-roots the frontier, nothing else has to learn about legs.
 */
class SwitchableValueField(initial: CoarseValueField) : ValueField {
	@Volatile
	var current: CoarseValueField = initial

	override val view: CoarseVoxelView get() = current.view
	override val goal: Stance get() = current.goal

	override fun invalidate(sections: Set<PathingSection>) = current.invalidate(sections)
	override fun lowerBound(stance: Stance): Double = current.lowerBound(stance)
	override fun clearGuideCache() = current.clearGuideCache()
	override fun guide(stance: Stance): Double = current.guide(stance)
	override fun guide(stance: Stance, speed: SpeedClass): Double = current.guide(stance, speed)
	override fun isMapped(stance: Stance): Boolean = current.isMapped(stance)
	override fun edgesFrom(stance: Stance): List<CoarseEdge> = current.edgesFrom(stance)
	override fun isStance(stance: Stance): Boolean = current.isStance(stance)
	override fun steps(stance: Stance, count: Int, marginTicks: Double, heading: Pair<Double, Double>?): List<CoarseEdge> =
		current.steps(stance, count, marginTicks, heading)

	override fun chain(stance: Stance, firstStep: Stance?, length: Int, heading: Pair<Double, Double>?): List<Stance> =
		current.chain(stance, firstStep, length, heading)

	override fun reachesGoal(chain: List<Stance>): Boolean = current.reachesGoal(chain)
}
