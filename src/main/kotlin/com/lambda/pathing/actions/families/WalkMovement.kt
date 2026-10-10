package com.lambda.pathing.actions.families

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.BodyState
import com.lambda.pathing.actions.CellCondition
import com.lambda.pathing.actions.CellPredicate
import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.CompletionContext
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.DecisionPrice
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SteeringField
import com.lambda.pathing.actions.control.HeadingFollowerProgram
import com.lambda.pathing.actions.Movement
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.ProposalContext
import com.lambda.pathing.actions.control.SegmentFollowerProgram
import com.lambda.pathing.actions.StanceRules.CARDINALS
import com.lambda.pathing.actions.StanceRules.DIAGONALS
import com.lambda.pathing.actions.StanceRules.stanceConditions
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.actions.control.yawTowards
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.MovementKeys
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.Via
import com.lambda.pathing.core.plus
import com.lambda.pathing.core.bearingBetween
import com.lambda.pathing.core.center
import com.lambda.pathing.core.headingOfBearing
import com.lambda.pathing.launch.CORNER_CLEARANCE
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.world.Medium
import com.lambda.pathing.world.canStandAt
import kotlin.math.abs
import kotlin.math.floor

object WalkMovement : Movement {
	override val id = MovementId.WALK

	override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
		val options = context.options
		val costs = context.costs
		for ((dx, dz) in CARDINALS) {
			add(spec(dx, 0, dz, costs.cardinalWalk, stanceConditions(dx, 0, dz)))

			if (options.allowStepUp) {
				add(
					spec(
						dx, 1, dz, costs.stepUp,
						stanceConditions(dx, 1, dz) + STEP_UP_HEADROOM,
						MovementId.STEP_UP,
						strideCost = costs.cardinalWalk,
					)
				)
			}

			if (options.maxWalkOffDepth >= 1) {
				add(
					spec(
						dx, -1, dz, costs.walkOffCost(1),
						stanceConditions(dx, -1, dz) +
								(1 downTo 0).map { y -> CellCondition(dx, y, dz, CellPredicate.CENTER_SLICE) },
						MovementId.WALK_OFF,
					)
				)
			}
		}

		if (options.allowDiagonal) {
			for ((dx, dz) in DIAGONALS) for (dy in if (options.allowStepUp) 0..1 else 0..0) {
				val rising = dy > 0
				val movement = if (rising) MovementId.STEP_UP else id
				val stance = stanceConditions(dx, dy, dz) + if (rising) listOf(STEP_UP_HEADROOM) else emptyList()
				add(
					spec(
						dx, dy, dz, if (rising) costs.stepUp else costs.diagonalWalk,
						stance + listOf(
							CellCondition(dx, dy, 0, CellPredicate.OPEN_COLUMN),
							CellCondition(0, dy, dz, CellPredicate.OPEN_COLUMN),
						),
						movement,
						strideCost = if (rising) costs.diagonalWalk else null,
					)
				)
				for ((openX, openZ) in listOf(dx to 0, 0 to dz)) {
					val blockedX = dx - openX
					val blockedZ = dz - openZ
					add(
						spec(
							dx, dy, dz, if (rising) costs.stepUp + costs.cardinalWalk else costs.cornerWalk,
							stance + listOf(
								CellCondition(openX, dy, openZ, CellPredicate.OPEN_COLUMN),
								CellCondition(blockedX, dy, blockedZ, CellPredicate.BLOCKED_COLUMN),
								CellCondition(dx + openX, dy, dz + openZ, CellPredicate.OPEN_COLUMN),
							),
							movement,
							strideCost = if (rising) costs.cornerWalk else null,
							via = Via(
								dx * 0.5 + (openX - blockedX) * CORNER_CLEARANCE,
								dz * 0.5 + (openZ - blockedZ) * CORNER_CLEARANCE,
							),
						)
					)
				}
			}
		}
	}

	private fun spec(
		dx: Int,
		dy: Int,
		dz: Int,
		cost: Double,
		conditions: List<CellCondition>,
		movement: MovementId = id,
		strideCost: Double? = null,
		via: Via? = null,
	) = TemplateSpec(dx, dy, dz, movement, cost, conditions, strideCost = strideCost, via = via)

	private fun walks(step: CoarseEdge, constraints: MotionConstraints): List<TrajectoryDecision> = buildList {
		for (sprint in constraints.sprintModes) {
			for ((lookAhead, ease) in WALK_STYLES) {
				add(TrajectoryDecision.Walk(sprint, step.to, lookAhead, ease, via = step.via))
			}
		}
	}

	override fun proposals(context: ProposalContext): List<TrajectoryDecision> {
		val steps = context.steps
		val body = context.body
		val proposals = ArrayList<TrajectoryDecision>()

		val climbing = context.view.medium(body.stance.x, body.stance.y, body.stance.z) == Medium.CLIMBABLE
		for (step in steps) {
			if (!walkable(step)) continue

			if (climbing && !(step.movement == MovementId.STEP_UP && step.to.y > body.stance.y)) continue
			proposals += walks(step, context.constraints)
		}

		val leading = steps.first()
		if (climbing || !walkable(leading)) return proposals
		val target = leading.to
		val chain = context.steering.chain(body.stance, target, BEARING_LOOKAHEAD + 1, body.heading())
		val bearing = routeBearing(body, chain)

		val turning = turnsAhead(chain)
		val hazardous = body.hazardFrame != null
		val fanned = hazardous || turning

		fun heading(sprint: Boolean, yaw: Double, delay: Int?, keys: MovementKeys = MovementKeys.FORWARD, air: MovementKeys = keys) {
			proposals += TrajectoryDecision.Heading(sprint, target, yaw, delay, keys, air)
		}

		for (sprint in context.constraints.sprintModes) {
			heading(sprint, bearing, null)
			if (fanned) for (offset in HEADING_FAN_DEGREES) heading(sprint, bearing + offset, null)
			if (turning) for ((keys, facingOffset) in DECOUPLED_FACINGS) heading(sprint, bearing + facingOffset, null, keys)
		}

		for (sprint in context.constraints.sprintModes) {
			for (delay in OFF_AXIS_LAUNCH_DELAYS) if (delay == 0 || hazardous) heading(sprint, bearing, delay)
			if (fanned) for (offset in HEADING_FAN_DEGREES) for (delay in OFF_AXIS_LAUNCH_DELAYS) heading(sprint, bearing + offset, delay)
			if (hazardous) for (delay in OFF_AXIS_LAUNCH_DELAYS) for (air in AIRBORNE_STRAFE_KEYS) heading(sprint, bearing, delay, air = air)
		}

		return proposals
	}

	private fun walkable(edge: CoarseEdge): Boolean = when (edge.movement) {
		MovementId.BOUNCE, MovementId.LADDER_CATCH, MovementId.CLIMB -> false
		else -> edge.movement !in MovementId.AQUATIC
	}

	override fun price(decision: TrajectoryDecision, context: DecisionContext): DecisionPrice {
		val grounded = when (context.edge.movement) {
			MovementId.WALK, MovementId.STEP_UP, MovementId.WALK_OFF -> DecisionPrice.FREE
			else -> DecisionPrice(difficulty = GROUNDED_ON_AIR_EDGE_DIFFICULTY)
		}
		val heading = decision as? TrajectoryDecision.Heading ?: return grounded
		if (heading.delayFrames == null) return grounded
		val landable = landableAlong(context, heading.yaw)
		return grounded + DecisionPrice(
			difficulty = if (landable) LAUNCH_HEADING_DIFFICULTY else BLIND_HEADING_DIFFICULTY,
		)
	}

	private fun landableAlong(context: DecisionContext, bearing: Double): Boolean {
		val (unitX, unitZ) = headingOfBearing(bearing)
		val origin = context.body.stance
		val view = context.view
		val corridorFloor = context.steering
			.chain(origin, context.edge.to, SteeringField.PURSUIT_CHAIN_LENGTH, context.body.heading())
			.minOf { it.y }
		val lowest = minOf(corridorFloor, origin.y)
		val highest = origin.y + LaunchMode.MAX_JUMP_RISE
		if (highest < lowest) return true
		for (distance in 1..LAUNCH_REACH_BLOCKS) {
			val x = floor(origin.x + 0.5 + unitX * distance).toInt()
			val z = floor(origin.z + 0.5 + unitZ * distance).toInt()
			for (y in highest downTo lowest) {
				if (!view.isKnown(x, y, z) || !view.isKnown(x, y - 1, z)) return true
				if (view.canStandAt(x, y, z)) return true
			}
		}
		return false
	}

	private fun turnsAhead(chain: List<Stance>): Boolean {
		if (chain.size < 3) return false
		val first = bearingBetween(chain[0].center(), chain[1].center())
		val second = bearingBetween(chain[1].center(), chain[chain.lastIndex].center())
		return abs(Rotation.wrap(second - first)) >= DECOUPLE_TURN_DEGREES
	}

	private fun routeBearing(body: BodyState, chain: List<Stance>): Double {
		return yawTowards(chain[minOf(chain.lastIndex, BEARING_LOOKAHEAD)].center(), body.state)
	}

	override fun program(context: ProgramContext): ControlProgram {
		val decision = context.decision
		if (decision is TrajectoryDecision.Heading) return HeadingFollowerProgram(decision, context)
		val walk = decision as TrajectoryDecision.Walk
		return SegmentFollowerProgram(
			nodes = context.nodes,
			sprint = walk.sprint,
			lookAheadNodes = walk.lookAheadNodes,
			launch = context.launch,
			maxYawChange = context.constraints.maxYawDegreesPerFrame,
			easeTurns = walk.easeTurns,
			via = walk.via?.let { context.nodes.first() + it },
		)
	}

	override fun completed(context: CompletionContext): Boolean {
		val decision = context.decision
		if (context.launch != null) return context.launch.hasFired && context.airborne
		if (decision is TrajectoryDecision.Heading) {
			return context.frameIndex + 1 >= HEADING_COMMIT_FRAMES
		}
		return context.stance != context.body.stance
	}

	private val WALK_STYLES = listOf(1 to false, 2 to false, 1 to true)

	private val STEP_UP_HEADROOM = CellCondition(0, 2, 0, CellPredicate.CENTER_SLICE)

	private val HEADING_FAN_DEGREES = listOf(-12.0, 12.0)

	private const val HEADING_COMMIT_FRAMES = 12

	private val OFF_AXIS_LAUNCH_DELAYS = listOf(0, 2, 4)

	private val DECOUPLED_FACINGS = listOf(
		MovementKeys.FORWARD_RIGHT to -45.0,
		MovementKeys.FORWARD_LEFT to 45.0,
	)

	private const val DECOUPLE_TURN_DEGREES = 35.0

	private val AIRBORNE_STRAFE_KEYS = listOf(MovementKeys.FORWARD_LEFT, MovementKeys.FORWARD_RIGHT)

	private const val BEARING_LOOKAHEAD = 2

	private const val LAUNCH_REACH_BLOCKS = 4


	private const val LAUNCH_HEADING_DIFFICULTY = 0.45

	private const val BLIND_HEADING_DIFFICULTY = 0.95

	private const val GROUNDED_ON_AIR_EDGE_DIFFICULTY = 0.2
}
