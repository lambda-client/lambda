package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.TerminalApproach
import com.lambda.util.player.prediction.PlayerPhysicsProfile

data class PublishedPath(
    val route: CoarseRoutePlan,
    val plan: TrajectoryPlan,
    val profile: PlayerPhysicsProfile,
    val parameters: TerminalApproach,
    val safeAnchorStance: Stance,
    val safeAnchorFrame: Int,
    val remainingGuideTicks: Double,
    val attempts: Int,
    val planMillis: Long,
    val finalGoal: Stance,
    val controlSegments: Int = 1,
    val spliceFrames: List<Int> = emptyList(),
    val launchMarginFrames: Int = 0,
    val partial: Boolean = false,
    val planningGeneration: Long = 0L,
    val publicationSequence: Int = 0,
)
