package com.lambda.pathing.session

import com.lambda.pathing.execution.ExecutionDeviation
import com.lambda.pathing.execution.TrajectoryExecutionCursor
import com.lambda.pathing.search.PublishedPath

internal class Replay(val path: PublishedPath, val cursor: TrajectoryExecutionCursor) {
	var pendingImprovement: PublishedPath? = null
	var holding = false
	var heldSince = 0L
	var heldRefusals = 0
	var lastHeldRefusal: String? = null
	var repairDeadline: Int? = null
	var repairDeviation: ExecutionDeviation? = null
}
