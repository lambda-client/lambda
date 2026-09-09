package com.lambda.pathing.actions.families

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.CellCondition
import com.lambda.pathing.actions.CellPredicate
import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.CompletionContext
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.DecisionContext
import com.lambda.pathing.actions.DecisionPrice
import com.lambda.pathing.actions.control.HeadingFollowerProgram
import com.lambda.pathing.actions.Movement
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.ProposalContext
import com.lambda.pathing.actions.Proposals
import com.lambda.pathing.actions.control.PursuitTracker
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

	override fun proposals(context: ProposalContext): Proposals {
		val steps = context.steps
		val body = context.body
		val walks = ArrayList<TrajectoryDecision>()
		val launches = ArrayList<TrajectoryDecision>()

		val climbing = context.view.medium(body.stance.x, body.stance.y, body.stance.z) == Medium.CLIMBABLE
		for (step in steps) {
			if (!walkable(step)) continue

			if (climbing && !(step.movement == MovementId.STEP_UP && step.to.y > body.stance.y)) continue
			walks += decisions(DecisionContext(body, step, context.constraints, context.view))
		}

		val leading = steps.first()
		if (climbing || !walkable(leading)) return Proposals(walks, launches)
		val target = leading.to
		val bearing = routeBearing(context, target)

		val turning = turnsAhead(context, target)
		val hazardous = body.hazardFrame != null
		val fanned = hazardous || turning

		for (sprint in context.constraints.sprintModes) {
			walks += TrajectoryDecision.Heading(sprint, target, bearing, delayFrames = null)
			if (fanned) {
				for (offset in context.headingFanDegrees) {
					if (offset == 0.0) continue
					walks += TrajectoryDecision.Heading(
						sprint, target, bearing + offset, delayFrames = null,
					)
				}
			}
			if (turning) {
				for ((keys, facingOffset) in DECOUPLED_FACINGS) {
					walks += TrajectoryDecision.Heading(
						sprint, target, bearing + facingOffset, delayFrames = null, keys = keys,
					)
				}
			}
		}

		for (sprint in context.constraints.sprintModes) {
			for (delay in OFF_AXIS_LAUNCH_DELAYS) {
				if (delay != 0 && !hazardous) continue
				launches += TrajectoryDecision.Heading(sprint, target, bearing, delayFrames = delay)
			}
			if (fanned) {
				for (offset in context.headingFanDegrees) {
					if (offset == 0.0) continue
					for (delay in OFF_AXIS_LAUNCH_DELAYS) {
						launches += TrajectoryDecision.Heading(
							sprint, target, bearing + offset, delayFrames = delay,
						)
					}
				}
			}

			if (hazardous) {
				for (delay in OFF_AXIS_LAUNCH_DELAYS) {
					for (air in AIRBORNE_KEYS.drop(1)) {
						launches += TrajectoryDecision.Heading(
							sprint, target, bearing, delayFrames = delay,
							keys = MovementKeys.FORWARD, airborneKeys = air,
						)
					}
				}
			}
		}

		return Proposals(walks, launches)
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
			?.chain(origin, context.edge.to, context.chainLength, context.body.heading())
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
				if (view.standingSurface(x, y - 1, z) != null &&
					view.voxel(x, y, z).centerPassable &&
					view.voxel(x, y + 1, z).centerPassable
				) return true
			}
		}
		return false
	}

	private fun turnsAhead(context: ProposalContext, firstStep: Stance): Boolean {
		val chain = context.steering.chain(
			context.body.stance, firstStep, BEARING_LOOKAHEAD + 1, context.body.heading(),
		)
		if (chain.size < 3) return false
		val first = bearingBetween(chain[0].center(), chain[1].center())
		val second = bearingBetween(chain[1].center(), chain[chain.lastIndex].center())
		return abs(Rotation.wrap(second - first)) >= DECOUPLE_TURN_DEGREES
	}

	private fun routeBearing(context: ProposalContext, firstStep: Stance): Double {
		val chain = context.steering.chain(
			context.body.stance, firstStep, BEARING_LOOKAHEAD, context.body.heading(),
		)
		val aim = chain[minOf(chain.lastIndex, BEARING_LOOKAHEAD)].center()
		return bearingBetween(
			HorizontalPoint(
				context.body.state.position.x,
				context.body.state.position.y,
				context.body.state.position.z,
			),
			aim,
		)
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
		return SegmentFollowerProgram(
			nodes = context.nodes,
			sprint = decision.sprint,
			lookAheadNodes = decision.followLookAheadNodes ?: PursuitTracker.DEFAULT_LOOK_AHEAD_NODES,
			launch = context.launch,
			maxYawChange = context.constraints.maxYawDegreesPerFrame,
			easeTurns = (decision as? TrajectoryDecision.Walk)?.easeTurns == true,
		)
	}

	override fun completed(context: CompletionContext): Boolean {
		val decision = context.decision
		if (context.launch != null) return context.launch.hasFired && context.airborne
		if (decision is TrajectoryDecision.Heading) {
			return context.frameIndex + 1 >= context.headingCommitFrames
		}
		return context.stance != context.body.stance
	}

	private val WALK_STYLES = listOf(1 to false, 2 to false, 1 to true)

	private val OFF_AXIS_LAUNCH_DELAYS = listOf(0, 2, 4)

	private val DECOUPLED_FACINGS = listOf(
		MovementKeys.FORWARD_RIGHT to -45.0,
		MovementKeys.FORWARD_LEFT to 45.0,
	)

	private const val DECOUPLE_TURN_DEGREES = 35.0

	private val AIRBORNE_KEYS = listOf(
		MovementKeys.FORWARD, MovementKeys.FORWARD_LEFT, MovementKeys.FORWARD_RIGHT,
	)

	private const val BEARING_LOOKAHEAD = 2

	private const val LAUNCH_REACH_BLOCKS = 4

	private const val LAUNCH_MAX_RISE = 1

	private const val LAUNCH_HEADING_DIFFICULTY = 0.45

	private const val BLIND_HEADING_DIFFICULTY = 0.95

	private const val GROUNDED_ON_AIR_EDGE_DIFFICULTY = 0.2
}
