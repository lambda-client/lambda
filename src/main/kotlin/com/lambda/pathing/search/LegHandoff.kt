package com.lambda.pathing.search

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.core.Stance

/**
 * The next leg of a compound route, resolved and ready to be searched: the coarse route
 * from the waypoint just touched and the value field descending to the leg's goal. The
 * running search swaps these in and keeps expanding from the touch anchor; the tape never
 * breaks. See docs/decisions/arrival.md (walk-through waypoints).
 */
class LegHandoff(
	val route: CoarseRoutePlan,
	val field: CoarseValueField,
	/** The waypoint whose touch triggered the handoff. */
	val waypoint: Stance,
)

/** The frame a certified tape touches a walk-through waypoint, in tape frame numbering. */
data class LegTouch(val frame: Int, val waypoint: Stance)
