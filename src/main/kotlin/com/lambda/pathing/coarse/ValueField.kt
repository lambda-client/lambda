package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.SteeringField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView

interface ValueField : SteeringField {
	val view: CoarseVoxelView

	fun invalidate()
	fun lowerBound(stance: Stance): Double
	fun guide(stance: Stance): Double
	fun guide(stance: Stance, speed: SpeedClass): Double
	fun isMapped(stance: Stance): Boolean
	fun isStance(stance: Stance): Boolean
	fun steps(stance: Stance, count: Int, marginTicks: Double = Double.MAX_VALUE): List<CoarseEdge>

	fun reachesGoal(chain: List<Stance>): Boolean
}

class SwitchableValueField(initial: CoarseValueField) : ValueField {
	@Volatile
	var current: CoarseValueField = initial

	override val view: CoarseVoxelView get() = current.view

	override fun invalidate() = current.invalidate()
	override fun lowerBound(stance: Stance): Double = current.lowerBound(stance)
	override fun guide(stance: Stance): Double = current.guide(stance)
	override fun guide(stance: Stance, speed: SpeedClass): Double = current.guide(stance, speed)
	override fun isMapped(stance: Stance): Boolean = current.isMapped(stance)
	override fun isStance(stance: Stance): Boolean = current.isStance(stance)
	override fun steps(stance: Stance, count: Int, marginTicks: Double): List<CoarseEdge> =
		current.steps(stance, count, marginTicks)

	override fun chain(stance: Stance, firstStep: Stance?, length: Int): List<Stance> =
		current.chain(stance, firstStep, length)

	override fun reachesGoal(chain: List<Stance>): Boolean = current.reachesGoal(chain)
}
