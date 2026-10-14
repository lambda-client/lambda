package com.lambda.pathing.search

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.coarse.SwitchableValueField
import com.lambda.pathing.core.Stance

internal class PlanLeg(val route: CoarseRoutePlan, val field: CoarseValueField)

internal class Spine(val steps: List<SpineStep>, val legs: List<PlanLeg>)

internal class FixedLegs(private val legs: List<PlanLeg>, private var current: Int, private val field: SwitchableValueField) : LegSource {
	override fun hasNext(): Boolean = current + 1 < legs.size

	override fun handoff(touched: Stance): CoarseRoutePlan? {
		if (!hasNext()) return null
		current++
		field.current = legs[current].field
		return legs[current].route
	}

	override fun rewind(): CoarseRoutePlan {
		current--
		field.current = legs[current].field
		return legs[current].route
	}
}
