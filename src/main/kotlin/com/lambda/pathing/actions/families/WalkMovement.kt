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
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.MovementKeys
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.bearingBetween
import com.lambda.pathing.core.center
import com.lambda.pathing.core.headingOfBearing
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
						stanceConditions(dx, 1, dz) + CellCondition(0, 2, 0, CellPredicate.CENTER_SLICE),
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
			for ((dx, dz) in DIAGONALS) {

				add(
					spec(
						dx, 0, dz, costs.diagonalWalk,
						stanceConditions(dx, 0, dz) + listOf(
							CellCondition(dx, 0, 0, CellPredicate.FULL_SLICE),
							CellCondition(dx, 1, 0, CellPredicate.FULL_HEAD),
							CellCondition(0, 0, dz, CellPredicate.FULL_SLICE),
							CellCondition(0, 1, dz, CellPredicate.FULL_HEAD),
						),
					)
				)
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
	) = TemplateSpec(dx, dy, dz, movement, cost, conditions, strideCost = strideCost)

	override fun decisions(context: DecisionContext): List<TrajectoryDecision> = buildList {
		for (sprint in context.constraints.sprintModes) {
			for ((lookAhead, ease) in WALK_STYLES) {
				add(TrajectoryDecision.Walk(sprint, context.edge.to, lookAhead, ease))
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
			proposals += decisions(DecisionContext(body, step, context.constraints, context.view))
		}

		val leading = steps.first()
		if (climbing || !walkable(leading)) return proposals
		val target = leading.to
		val chain = context.steering.chain(body.stance, target, BEARING_LOOKAHEAD + 1, body.heading())
		val bearing = routeBearing(body, chain)

		val turning = turnsAhead(chain)
		val hazardous = body.hazardFrame != null
		val fanned = hazardous || turning

		for (sprint in context.constraints.sprintModes) {
			proposals += TrajectoryDecision.Heading(sprint, target, bearing, delayFrames = null)
			if (fanned) {
				for (offset in HEADING_FAN_DEGREES) {
					proposals += TrajectoryDecision.Heading(
						sprint, target, bearing + offset, delayFrames = null,
					)
				}
			}
			if (turning) {
				for ((keys, facingOffset) in DECOUPLED_FACINGS) {
					proposals += TrajectoryDecision.Heading(
						sprint, target, bearing + facingOffset, delayFrames = null, keys = keys,
					)
				}
			}
		}

		for (sprint in context.constraints.sprintModes) {
			for (delay in OFF_AXIS_LAUNCH_DELAYS) {
				if (delay != 0 && !hazardous) continue
				proposals += TrajectoryDecision.Heading(sprint, target, bearing, delayFrames = delay)
			}
			if (fanned) {
				for (offset in HEADING_FAN_DEGREES) {
					for (delay in OFF_AXIS_LAUNCH_DELAYS) {
						proposals += TrajectoryDecision.Heading(
							sprint, target, bearing + offset, delayFrames = delay,
						)
					}
				}
			}

			if (hazardous) {
				for (delay in OFF_AXIS_LAUNCH_DELAYS) {
					for (air in AIRBORNE_STRAFE_KEYS) {
						proposals += TrajectoryDecision.Heading(
							sprint, target, bearing, delayFrames = delay, airborneKeys = air,
						)
					}
				}
			}
		}

		return proposals
	}

	private fun walkable(edge: CoarseEdge): Boolean = when (edge.movement) {
		MovementId.BOUNCE, MovementId.LADDER_CATCH, MovementId.CLIMB -> false
		else -> true
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
			?.chain(origin, context.edge.to, SteeringField.PURSUIT_CHAIN_LENGTH, context.body.heading())
			?.minOf { it.y }
			?: origin.y
		val lowest = minOf(corridorFloor, origin.y)
		val highest = origin.y + LAUNCH_MAX_RISE
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
		val aim = chain[minOf(chain.lastIndex, BEARING_LOOKAHEAD)].center()
		val position = body.state.position
		return bearingBetween(HorizontalPoint(position.x, position.y, position.z), aim)
	}

	override fun program(context: ProgramContext): ControlProgram {
		val decision = context.decision
		if (decision is TrajectoryDecision.Heading) {
			return HeadingFollowerProgram(
				targetYaw = decision.yaw,
				sprint = decision.sprint && decision.keys.sustainsSprint,
				maxYawChange = context.constraints.maxYawDegreesPerFrame,
				launch = context.launch,
				keys = decision.keys,
				airborneKeys = decision.airborneKeys,
			)
		}
		val walk = decision as TrajectoryDecision.Walk
		return SegmentFollowerProgram(
			nodes = context.nodes,
			sprint = walk.sprint,
			lookAheadNodes = walk.lookAheadNodes,
			launch = context.launch,
			maxYawChange = context.constraints.maxYawDegreesPerFrame,
			easeTurns = walk.easeTurns,
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

	private const val LAUNCH_MAX_RISE = 1

	private const val LAUNCH_HEADING_DIFFICULTY = 0.45

	private const val BLIND_HEADING_DIFFICULTY = 0.95

	private const val GROUNDED_ON_AIR_EDGE_DIFFICULTY = 0.2
}
