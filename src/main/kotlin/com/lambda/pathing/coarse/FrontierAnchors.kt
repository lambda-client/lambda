package com.lambda.pathing.coarse

import com.lambda.pathing.actions.families.DiveMovement
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxelView

object FrontierAnchors {
	fun goalResolved(view: CoarseVoxelView, goal: Stance): Boolean =
		view.isKnown(goal.x, goal.y - 1, goal.z) &&
				view.isKnown(goal.x, goal.y, goal.z) &&
				view.isKnown(goal.x, goal.y + 1, goal.z)

	fun refusesOptimism(view: CoarseVoxelView, moves: SimpleMoveLibrary, goal: Stance): Boolean =
		goalResolved(view, goal) && !moves.isStance(view, goal) && !DiveMovement.inOpenColumn(view, goal, goal)
}
