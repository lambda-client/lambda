package com.lambda.pathing.actions

import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchSolution
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.physics.MovementSimulationState

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

    /** The field the rollout will steer by; null where no corridor is in play. */
    val steering: SteeringField? = null,

    /** Length of the steering chain the rollout will follow. */
    val chainLength: Int = 1,
)

interface SteeringField {
    fun chain(
        stance: Stance,
        firstStep: Stance? = null,
        length: Int,
        heading: Pair<Double, Double>? = null,
    ): List<Stance>
}

class ProposalContext(
    val body: BodyState,
    val steps: List<CoarseEdge>,
    val constraints: MotionConstraints,
    val view: CoarseVoxelView,
    val steering: SteeringField,
    val headingFanDegrees: List<Double>,

    /** Remaining guide ticks per stance, NaN where no field is in play: lets a proposer price its own claim. */
    val guideTicks: (Stance) -> Double = { Double.NaN },

    /** The MOVING-class guide: what the remainder costs a body that keeps its momentum. */
    val movingGuideTicks: (Stance) -> Double = guideTicks,

    /** Whether momentum proposals (gait hops, skips across walked cells) are wanted; the improver's vocabulary only. */
    val momentum: Boolean = false,
)

class Proposals(
    val walks: List<TrajectoryDecision> = emptyList(),
    val launches: List<TrajectoryDecision> = emptyList(),
) {
    companion object {
        val EMPTY = Proposals()
    }
}

class ProgramContext(
    val decision: TrajectoryDecision,
    val body: BodyState,

    val nodes: List<HorizontalPoint>,
    val constraints: MotionConstraints,
    val launch: LaunchTrigger?,

    /**
     * Whether the decision's landing sits in walkable surroundings rather than on an
     * isolated pad. Open ground rewards landing long -- momentum is free distance and
     * anything down-range catches the body -- while an isolated landing rewards
     * precision. Controllers use this to decide whether to fly the arc closed-loop.
     */
    val openLanding: Boolean = false,
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

/**
 * How close [solution]'s landing comes to taking fall damage, as a 0..1 fraction: zero
 * until the drop is within [ARC_MODEL_ERROR_BLOCKS] of the safe limit, then steep. Bouncy
 * ground (exempt from fall damage) prices at zero. Risky launches are priced, never
 * refused here. See docs/decisions/movement-tuning.md (landing risk).
 */
fun DecisionContext.landingRisk(solution: LaunchSolution): Double {
    if (view.voxel(edge.to.x, edge.to.y - 1, edge.to.z).bouncy) return 0.0
    val limit = constraints.maxSafeFallDistance
    if (limit <= 0.0) return 1.0
    val rise = (edge.to.y - edge.from.y).toDouble()
    val fall = solution.fallDistance(rise)
    val safe = limit - ARC_MODEL_ERROR_BLOCKS
    return ((fall - safe) / ARC_MODEL_ERROR_BLOCKS).coerceIn(0.0, 1.0)
}

/** Blocks from the safe fall limit inside which a landing is priced as a gamble; wider than the arc model's measured landing error. */
private const val ARC_MODEL_ERROR_BLOCKS = 1.0

interface Movement {
    val id: MovementId

    fun templates(context: MovementContext): List<TemplateSpec>

    fun decisions(context: DecisionContext): List<TrajectoryDecision>

    /**
     * What attempting [decision] costs the search, beyond the coarse edge it serves.
     *
     * Called once per decision when an anchor's vocabulary is built, so it may read the
     * world but should not simulate. The default prices every movement as free, which
     * leaves ordering to the coarse cost alone -- correct for a movement whose variants
     * are genuinely interchangeable.
     */
    fun price(decision: TrajectoryDecision, context: DecisionContext): DecisionPrice =
        DecisionPrice.FREE

    fun proposals(context: ProposalContext): Proposals = Proposals.EMPTY

    fun offersFor(edge: CoarseEdge): Boolean = false

    fun occupies(view: CoarseVoxelView, stance: Stance): Boolean = false

    val completesAirborne: Boolean get() = false

    val pressesIntoTerrain: Boolean get() = false

    fun descentAllowance(decision: TrajectoryDecision): Double = 0.0

    fun transitionFrames(decision: TrajectoryDecision): Int = 0

    fun program(context: ProgramContext): ControlProgram

    fun completed(context: CompletionContext): Boolean
}
