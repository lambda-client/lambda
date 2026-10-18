package com.lambda.pathing.search

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.Stance

interface LegSource {
	fun hasNext(): Boolean

	fun handoff(touched: Stance): CoarseRoutePlan?

	fun rewind(): CoarseRoutePlan

	val finalGoal: Stance? get() = null
}

data class LegTouch(val frame: Int, val waypoint: Stance)
