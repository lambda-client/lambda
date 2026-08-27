package com.lambda.pathing.movement

import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchSolution
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
 * How close [solution]'s landing comes to taking fall damage, as a 0..1 fraction.
 *
 * Deliberately flat then steep rather than proportional. Every jump has an apex above
 * its landing -- a level sprint jump measures about 1.25 blocks over the corpus -- and
 * charging for that is charging for jumping at all, which measurably starved the search
 * of the launches it needed. Nothing is at stake until the drop comes within the arc
 * model's own error of the limit, and then everything is: the model describes the ideal
 * entry, the body's real one differs, and the difference is what decides between a clean
 * landing and a refusal. Bouncy ground, which the evaluator exempts from fall damage
 * outright, prices at zero.
 *
 * Refusing the risky ones outright measured worse than pricing them. A launch the model
 * calls fatal is sometimes the only way across, and taking it away just moved the search
 * budget into colliding with walls instead.
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

/**
 * How far a landing has to be from the safe fall limit before it stops being a gamble.
 *
 * One block: wider than the gap measured between the ballistic model's predicted landing
 * and the simulator's, with room for the entry speed the body actually arrives at.
 */
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
