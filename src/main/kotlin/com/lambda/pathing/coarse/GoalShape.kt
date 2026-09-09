package com.lambda.pathing.coarse

import com.lambda.pathing.core.Stance

interface GoalShape {

	fun heuristic(from: Stance): Double

	fun satisfied(stance: Stance): Boolean

	val anchorStance: Stance

	data class Point(val stance: Stance, val moves: SimpleMoveLibrary) : GoalShape {

		override fun heuristic(from: Stance): Double = moves.heuristic(from, stance)

		override fun satisfied(stance: Stance): Boolean = stance == this.stance

		override val anchorStance: Stance get() = stance
	}
}
