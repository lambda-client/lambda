package com.lambda.pathing.movement

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.MovementKeys
import com.lambda.pathing.core.Stance
import com.lambda.pathing.launch.BounceSolution
import com.lambda.pathing.launch.LaunchSolution

sealed interface TrajectoryDecision {

    val movement: MovementId

    val sprint: Boolean

    val step: Stance?

    val margin: Double get() = 0.0

    data class Walk(
        override val sprint: Boolean,
        override val step: Stance?,
        val lookAheadNodes: Int,
        val easeTurns: Boolean,
        override val movement: MovementId = MovementId.WALK,
    ) : TrajectoryDecision

    data class Launch(
        override val sprint: Boolean,
        override val step: Stance?,
        val delayFrames: Int,
        val solution: LaunchSolution? = null,
        override val movement: MovementId = MovementId.JUMP,
    ) : TrajectoryDecision {
        override val margin: Double get() = solution?.margin ?: 0.0
    }

    data class Drop(
        override val sprint: Boolean,
        override val step: Stance?,
        val solution: LaunchSolution,
        override val movement: MovementId = MovementId.DROP,
    ) : TrajectoryDecision {
        override val margin: Double get() = solution.margin
    }

    data class Climb(
        override val step: Stance?,

        val holdForward: Boolean,

        val holdWhileClimbing: Boolean,

        val climbWithJump: Boolean,

        val yaw: Double?,
        override val sprint: Boolean = false,
        override val movement: MovementId = MovementId.CLIMB,
    ) : TrajectoryDecision

    data class Heading(
        override val sprint: Boolean,
        override val step: Stance?,
        val yaw: Double,
        val delayFrames: Int?,
        val keys: MovementKeys = MovementKeys.FORWARD,
        val airborneKeys: MovementKeys = keys,
        override val movement: MovementId = MovementId.WALK,
    ) : TrajectoryDecision

    data class RunUpLaunch(
        override val sprint: Boolean,
        override val step: Stance?,
        val solution: LaunchSolution,
        val retreatAlong: Double,
        val hopAlong: Double?,
        override val movement: MovementId = MovementId.JUMP,
    ) : TrajectoryDecision {
        override val margin: Double get() = solution.margin
    }

    data class Bounce(
        override val sprint: Boolean,
        override val step: Stance?,
        val solution: BounceSolution,
        override val movement: MovementId = MovementId.BOUNCE,
    ) : TrajectoryDecision {

        override val margin: Double get() = solution.speedSlack
    }
}
