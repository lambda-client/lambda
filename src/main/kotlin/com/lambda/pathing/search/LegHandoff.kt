package com.lambda.pathing.search

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.core.Stance

interface LegSource {
	fun hasNext(): Boolean

	fun handoff(touched: Stance): LegHandoff?
}

class LegHandoff(
	val route: CoarseRoutePlan,
	val field: CoarseValueField,

	val waypoint: Stance,
)

data class LegTouch(val frame: Int, val waypoint: Stance)
