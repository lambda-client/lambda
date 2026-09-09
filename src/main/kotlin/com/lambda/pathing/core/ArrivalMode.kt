package com.lambda.pathing.core

import com.lambda.util.NamedEnum

enum class ArrivalMode(override val displayName: String) : NamedEnum {
	@Suppress("unused")
	STAND_STILL("Stand Still"),
	WALK_THROUGH("Walk Through"),
}
