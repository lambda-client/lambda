package com.lambda.pathing.actions

import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.Via
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.world.CoarseVoxelView

interface BodyState {
	val state: MovementSimulationState

	val stance: Stance

	val hazardFrame: Int?

	val speed: Double

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
	val via: Via? = null,
	val departure: CellPredicate? = CellPredicate.DRY,
	val maxRise: Double = Double.POSITIVE_INFINITY,
) {
	internal fun toTemplate(id: MotionTemplateId) =
		MotionTemplate(id, dx, dy, dz, movement, cost, listOfNotNull(departure?.let { CellCondition(0, 0, 0, it) }) + conditions, arc, strideCost, via, maxRise)
}

class MovementContext(
	val options: SimpleMoveOptions,
	val costs: CoarseMoveCosts,
)

class DecisionContext(
	val body: BodyState,
	val edge: CoarseEdge,
	val constraints: MotionConstraints,
	val view: CoarseVoxelView,
	val steering: SteeringField,
)

interface SteeringField {
	fun chain(
		stance: Stance,
		firstStep: Stance?,
		length: Int,
		heading: Pair<Double, Double>? = null,
	): List<Stance>

	companion object {
		const val PURSUIT_CHAIN_LENGTH = 6
	}
}

class ProposalContext(
	val body: BodyState,
	val steps: List<CoarseEdge>,
	val constraints: MotionConstraints,
	val view: CoarseVoxelView,
	val steering: SteeringField,
)

class ProgramContext(
	val decision: TrajectoryDecision,
	val body: BodyState,
	val nodes: List<HorizontalPoint>,
	val constraints: MotionConstraints,
	val launch: LaunchTrigger?,
	val openLanding: Boolean,
)

class CompletionContext(
	val decision: TrajectoryDecision,
	val body: BodyState,
	val frameIndex: Int,
	val observed: MovementSimulationState,
	val stance: Stance,
	val airborne: Boolean,
	val launch: LaunchTrigger?,
)

interface Movement {
	val id: MovementId

	fun templates(context: MovementContext): List<TemplateSpec>

	fun decisions(context: DecisionContext): List<TrajectoryDecision> = emptyList()

	fun price(decision: TrajectoryDecision, context: DecisionContext): DecisionPrice =
		DecisionPrice.FREE

	fun proposals(context: ProposalContext): List<TrajectoryDecision> = emptyList()

	fun offersFor(edge: CoarseEdge): Boolean = false

	fun occupies(view: CoarseVoxelView, stance: Stance): Boolean = false

	val inMedium: Boolean get() = false

	fun descentAllowance(decision: TrajectoryDecision): Double = 0.0

	fun transitionFrames(decision: TrajectoryDecision): Int = 0

	fun program(context: ProgramContext): ControlProgram

	fun completed(context: CompletionContext): Boolean
}
