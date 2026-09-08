package com.lambda.pathing.core

import com.lambda.util.NamedEnum

/**
 * What reaching a waypoint means. STAND_STILL is the classic finish: the body comes to
 * rest at the goal and the next leg starts from rest. WALK_THROUGH is a checkpoint: the
 * body enters the goal cell at speed and the running search re-targets onto the next leg
 * without a stop, so a compound route is one continuous tape. The final goal always rests.
 */
enum class ArrivalMode(override val displayName: String) : NamedEnum {
	STAND_STILL("Stand Still"),
	WALK_THROUGH("Walk Through"),
}
