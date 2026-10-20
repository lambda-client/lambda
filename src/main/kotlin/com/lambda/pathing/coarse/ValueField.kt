package com.lambda.pathing.coarse

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.SteeringField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.world.CoarseVoxelView

interface ValueField : SteeringField {
	val view: CoarseVoxelView

	fun invalidate()
	fun lowerBound(stance: Stance): Double
	override fun guide(stance: Stance): Double
	fun guide(stance: Stance, speed: SpeedClass): Double
	override fun isMapped(stance: Stance): Boolean
	fun isStance(stance: Stance): Boolean
	fun steps(stance: Stance, count: Int, marginTicks: Double = Double.MAX_VALUE): List<CoarseEdge>

	fun reachesGoal(chain: List<Stance>): Boolean

	fun edge(from: Stance, to: Stance): CoarseEdge? = null

	fun replanned(edge: CoarseEdge, view: CoarseVoxelView, touched: Set<VoxelPos>): CoarseEdge? = edge

	fun sweep(edge: CoarseEdge): List<VoxelPos> = emptyList()

	fun obstruct(cells: LongArray) {}
}

class SwitchableValueField(initial: CoarseValueField) : ValueField {
	private var obstacles: LongArray? = null

	@Volatile
	var current: CoarseValueField = initial
		set(value) {
			field = value
			obstacles?.let(value::obstruct)
		}

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

	override fun edge(from: Stance, to: Stance): CoarseEdge? = current.edge(from, to)

	override fun replanned(edge: CoarseEdge, view: CoarseVoxelView, touched: Set<VoxelPos>): CoarseEdge? = current.replanned(edge, view, touched)

	override fun sweep(edge: CoarseEdge): List<VoxelPos> = current.sweep(edge)

	override fun obstruct(cells: LongArray) {
		obstacles = cells
		current.obstruct(cells)
	}
}
