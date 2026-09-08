package com.lambda.pathing.actions

import com.lambda.pathing.core.VoxelPos

/**
 * A change a decision makes to the world as part of executing: the slot Stage 8's
 * world-changing actions fill (docs/pathplanner-reimplementation-plan.md §1.8, Stage 8).
 * Carried by [TrajectoryDecision.effect]; nothing consumes it yet.
 */
sealed interface WorldEffect {

	data class Place(val at: VoxelPos, val block: BlockKind) : WorldEffect

	data class Break(val at: VoxelPos) : WorldEffect

	data class Toggle(val at: VoxelPos) : WorldEffect
}

/** The kinds of block a [WorldEffect.Place] can put down; grows with the placing actions. */
enum class BlockKind {
	SOLID,
}
