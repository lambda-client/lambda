package com.lambda.pathing.movement

import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.util.player.prediction.MovementSimulationState

interface BodyState {
    val state: MovementSimulationState

    val stance: Stance

    val hazardFrame: Int?

    val speed: Double get() = state.velocity.horizontalLength()

    fun heading(): Pair<Double, Double>? =
        if (speed <= HEADING_EPSILON) null else state.velocity.x to state.velocity.z

    private companion object {
        const val HEADING_EPSILON = 1e-6
    }
}

data class TemplateSpec(
    val dx: Int,
    val dy: Int,
    val dz: Int,
    val movement: MovementId,
    val cost: Double,
    val conditions: List<CellCondition>,
    val arc: MotionTemplate.ArcSpec? = null,

    val strideCost: Double? = null,
) {
    internal fun toTemplate(id: MotionTemplateId) =
        MotionTemplate(id, dx, dy, dz, movement, cost, conditions, arc, strideCost)
}

class MovementContext(
    val options: SimpleMoveOptions,
    val costs: CoarseMoveCosts,
    val ballistics: BallisticProfile = BallisticProfile.VANILLA,
)

class DecisionContext(
    val body: BodyState,
    val edge: CoarseEdge,
    val constraints: MotionConstraints,
    val view: CoarseVoxelView,
    val ballistics: BallisticProfile = BallisticProfile.VANILLA,
)

class ProgramContext(
    val decision: TrajectoryDecision,
    val body: BodyState,

    val nodes: List<HorizontalPoint>,
    val constraints: MotionConstraints,
    val launch: LaunchTrigger?,
)

class CompletionContext(
    val decision: TrajectoryDecision,
    val body: BodyState,
    val frameIndex: Int,
    val observed: MovementSimulationState,

    val stance: Stance,

    val airborne: Boolean,
    val launch: LaunchTrigger?,
    val headingCommitFrames: Int,
)

interface Movement {
    val id: MovementId

    fun templates(context: MovementContext): List<TemplateSpec>

    fun decisions(context: DecisionContext): List<TrajectoryDecision>

    fun offersFor(edge: CoarseEdge): Boolean = false

    fun occupies(view: CoarseVoxelView, stance: Stance): Boolean = false

    val completesAirborne: Boolean get() = false

    val pressesIntoTerrain: Boolean get() = false

    fun descentAllowance(decision: TrajectoryDecision): Double = 0.0

    fun transitionFrames(decision: TrajectoryDecision): Int = 0

    fun program(context: ProgramContext): ControlProgram

    fun completed(context: CompletionContext): Boolean
}
