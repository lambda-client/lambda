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
import com.lambda.pathing.actions.Movement
import com.lambda.pathing.actions.MovementContext
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.ProposalContext
import com.lambda.pathing.actions.StanceRules.CARDINALS
import com.lambda.pathing.actions.StanceRules.DIAGONALS
import com.lambda.pathing.actions.StanceRules.leaving
import com.lambda.pathing.actions.StanceRules.stanceConditions
import com.lambda.pathing.actions.StanceRules.walkConditions
import com.lambda.pathing.actions.SteeringField
import com.lambda.pathing.actions.TemplateSpec
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.actions.control.HeadingFollowerProgram
import com.lambda.pathing.actions.control.SegmentFollowerProgram
import com.lambda.pathing.actions.control.yawTowards
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.MovementKeys
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.Via
import com.lambda.pathing.core.bearingBetween
import com.lambda.pathing.core.center
import com.lambda.pathing.core.headingOfBearing
import com.lambda.pathing.core.plus
import com.lambda.pathing.launch.CORNER_CLEARANCE
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.Medium
import com.lambda.pathing.world.canStandAt
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

object WalkMovement : Movement {
	override val id = MovementId.WALK

	override fun templates(context: MovementContext): List<TemplateSpec> = buildList {
		val options = context.options
		val costs = context.costs
		for ((dx, dz) in CARDINALS) {
			val behind = CoarseVoxel.side(-dx, -dz)
			add(spec(dx, 0, dz, costs.cardinalWalk, walkConditions(dx, 0, dz)))

			if (options.allowStepUp) {
				add(
					spec(
						dx, 1, dz, costs.stepUp,
						walkConditions(dx, 1, dz) + CellCondition(0, 2, 0, CellPredicate.stepUpHeadroom(CoarseVoxel.sides(dx, dz))),
						MovementId.STEP_UP,
						strideCost = costs.cardinalWalk,
					)
				)
			}

			if (options.maxWalkOffDepth >= 1) {
				add(
					spec(
						dx, -1, dz, costs.walkOffCost(1),
						walkConditions(dx, -1, dz) +
								(1 downTo 0).map { y -> CellCondition(dx, y, dz, CellPredicate.entering(behind)) },
						MovementId.WALK_OFF,
					)
				)
			}
		}

		if (options.allowStepUp) {
			add(spec(0, 1, 0, costs.stepUp, listOf(CellCondition(0, 0, 0, CellPredicate.CLIMBING)), MovementId.STEP_UP))
		}

		if (options.allowDiagonal) {
			for ((dx, dz) in DIAGONALS) for (dy in if (options.allowStepUp) 0..1 else 0..0) {
				val rising = dy > 0
				val movement = if (rising) MovementId.STEP_UP else id
				val headroom = if (rising) listOf(STEP_UP_HEADROOM, CellCondition(0, 0, 0, CellPredicate.GROUNDED)) else emptyList()
				val footing = listOf(
					CellCondition(0, 0, 0, CellPredicate.footingReaches(dx, dy, dz, outbound = true)),
					CellCondition(dx, dy, dz, CellPredicate.footingReaches(dx, dy, dz, outbound = false)),
				)
				add(
					spec(
						dx, dy, dz, if (rising) costs.stepUp else costs.diagonalWalk,
						stanceConditions(dx, dy, dz, CoarseVoxel.sides(-dx, -dz)) + headroom + leaving(CoarseVoxel.sides(dx, dz), dy) + listOf(
							CellCondition(dx, dy, 0, CellPredicate.OPEN_COLUMN),
							CellCondition(0, dy, dz, CellPredicate.OPEN_COLUMN),
						) + footing,
						movement,
						strideCost = if (rising) costs.diagonalWalk else null,
					)
				)
				for ((openX, openZ) in listOf(dx to 0, 0 to dz)) {
					val blockedX = dx - openX
					val blockedZ = dz - openZ
					val via = Via(
						dx * 0.5 + (openX - blockedX) * CORNER_CLEARANCE,
						dz * 0.5 + (openZ - blockedZ) * CORNER_CLEARANCE,
					)
					val corner = costs.cardinalWalk * (hypot(dx.toDouble(), dz.toDouble()) + via.detour(dx, dz))
					val turn = stanceConditions(dx, dy, dz, CoarseVoxel.side(-blockedX, -blockedZ)) + headroom +
							leaving(CoarseVoxel.side(openX, openZ), dy)
					add(
						spec(
							dx, dy, dz, if (rising) costs.stepUp + costs.cardinalWalk else corner,
							turn + listOf(
								CellCondition(openX, dy, openZ, CellPredicate.OPEN_COLUMN),
								CellCondition(blockedX, dy, blockedZ, CellPredicate.BLOCKED_COLUMN),
								CellCondition(dx + openX, dy, dz + openZ, CellPredicate.OPEN_COLUMN),
							) + footing,
							movement,
							strideCost = if (rising) corner else null,
							via = via,
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
	) = TemplateSpec(
		dx, dy, dz, movement, cost, conditions, strideCost = strideCost, via = via,
		maxRise = if (dy > 0 && strideCost != null) LaunchMode.MAX_JUMP_RISE else Double.POSITIVE_INFINITY,
	)

	private fun walks(step: CoarseEdge, context: ProposalContext): List<TrajectoryDecision> = buildList {
		val chain = context.steering.chain(context.body.stance, step.to, SteeringField.PURSUIT_CHAIN_LENGTH)
		val styles = if (bendsWithin(chain, WALK_LOOKAHEAD_SEGMENTS)) WALK_STYLES else STRAIGHT_STYLES
		for (sprint in context.constraints.sprintModes) {
			for ((lookAhead, ease) in styles) {
				add(TrajectoryDecision.Walk(sprint, step.to, lookAhead, ease, via = step.via))
			}
		}
	}

	private fun bendsWithin(chain: List<Stance>, segments: Int): Boolean {
		val last = minOf(chain.lastIndex, segments)
		if (last < 2) return false
		val dx = chain[1].x - chain[0].x
		val dz = chain[1].z - chain[0].z
		for (index in 2..last) {
			val ex = chain[index].x - chain[index - 1].x
			val ez = chain[index].z - chain[index - 1].z
			if (dx * ez - dz * ex != 0 || dx * ex + dz * ez <= 0) return true
		}
		return false
	}

	override fun proposals(context: ProposalContext): List<TrajectoryDecision> {
		val steps = context.steps
		val body = context.body
		val proposals = ArrayList<TrajectoryDecision>()

		val climbing = context.view.medium(body.stance.x, body.stance.y, body.stance.z) == Medium.CLIMBABLE
		for (step in steps) {
			if (!walkable(step)) continue

			if (climbing && !(step.movement == MovementId.STEP_UP && step.to.y > body.stance.y)) continue
			proposals += walks(step, context)
		}

		val leading = steps.first()
		if (climbing || !walkable(leading)) return proposals
		val target = leading.to
		val chain = context.steering.chain(body.stance, target, BEARING_LOOKAHEAD + 1)
		val bearing = routeBearing(body, chain)
		val stepBearing = yawTowards(target.center(), body.state).takeIf { abs(Rotation.wrap(it - bearing)) > HEADING_FAN_DEGREES.max() }

		val turning = turnsAhead(chain)
		val hazardous = body.hazardFrame != null
		val fanned = hazardous || turning

		val atRest = body.state.onGround && body.state.velocity.horizontalLength() <= MotionConstraints.STOPPED_SPEED

		fun heading(sprint: Boolean, yaw: Double, delay: Int?, keys: MovementKeys = MovementKeys.FORWARD, air: MovementKeys = keys) {
			proposals += TrajectoryDecision.Heading(sprint, target, yaw, delay, keys, air)
		}

		fun pivot(sprint: Boolean, yaw: Double, delay: Int?) {
			if (atRest && abs(Rotation.wrap(yaw - body.state.rotation.yaw)) > context.constraints.maxYawDegreesPerFrame) {
				proposals += TrajectoryDecision.Heading(sprint, target, yaw, delay, pivot = true)
			}
		}

		for (sprint in context.constraints.sprintModes) {
			heading(sprint, bearing, null)
			stepBearing?.let { heading(sprint, it, null) }
			for (yaw in listOfNotNull(bearing, stepBearing)) for (delay in listOf(null, 0)) pivot(sprint, yaw, delay)
			if (fanned) for (offset in HEADING_FAN_DEGREES) heading(sprint, bearing + offset, null)
			if (turning) for ((keys, facingOffset) in DECOUPLED_FACINGS) heading(sprint, bearing + facingOffset, null, keys)
		}

		for (sprint in context.constraints.sprintModes) {
			for (delay in OFF_AXIS_LAUNCH_DELAYS) if (delay == 0 || hazardous) {
				heading(sprint, bearing, delay)
				stepBearing?.let { heading(sprint, it, delay) }
			}
			if (fanned) for (offset in HEADING_FAN_DEGREES) for (delay in OFF_AXIS_LAUNCH_DELAYS) heading(sprint, bearing + offset, delay)
			if (hazardous) for (delay in OFF_AXIS_LAUNCH_DELAYS) for (air in AIRBORNE_STRAFE_KEYS) heading(sprint, bearing, delay, air = air)
		}

		if (turning && context.constraints.maxYawDegreesPerFrame > GRADUAL_TURN_RATE) {
			proposals += proposals.filterIsInstance<TrajectoryDecision.Heading>().map { it.copy(turnRate = GRADUAL_TURN_RATE) }
		}

		return proposals
	}

	private fun walkable(edge: CoarseEdge): Boolean = when (edge.movement) {
		MovementId.BOUNCE, MovementId.LADDER_CATCH, MovementId.CLIMB, MovementId.NEO -> false
		else -> edge.movement !in MovementId.AQUATIC
	}

	override fun price(decision: TrajectoryDecision, context: DecisionContext): DecisionPrice {
		val grounded = when (context.edge.movement) {
			MovementId.WALK, MovementId.STEP_UP, MovementId.WALK_OFF -> DecisionPrice.FREE
			else -> DecisionPrice(difficulty = GROUNDED_ON_AIR_EDGE_DIFFICULTY)
		}
		val heading = decision as? TrajectoryDecision.Heading ?: return grounded
		if (heading.delayFrames == null) return grounded
		val landable = context.landableAlong(heading.yaw) { landableAlong(context, it) }
		return grounded + DecisionPrice(
			difficulty = if (landable) LAUNCH_HEADING_DIFFICULTY else BLIND_HEADING_DIFFICULTY,
		)
	}

	private fun landableAlong(context: DecisionContext, bearing: Double): Boolean {
		val (unitX, unitZ) = headingOfBearing(bearing)
		val origin = context.body.stance
		val view = context.view
		val corridorFloor = context.steering
			.chain(origin, context.edge.to, SteeringField.PURSUIT_CHAIN_LENGTH)
			.minOf { it.y }
		val lowest = minOf(corridorFloor, origin.y)
		val highest = origin.y + LaunchMode.MAX_JUMP_STEP
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

	override fun descentAllowance(decision: TrajectoryDecision): Double = (decision as? TrajectoryDecision.Heading)?.descent ?: 0.0

	override fun program(context: ProgramContext): ControlProgram {
		val decision = context.decision
		if (decision is TrajectoryDecision.Heading) return HeadingFollowerProgram(decision, context)
		val walk = decision as TrajectoryDecision.Walk
		return SegmentFollowerProgram(
			nodes = context.nodes,
			sprint = walk.sprint,
			lookAheadNodes = walk.lookAheadNodes,
			launch = context.launch,
			turning = context.constraints.turning,
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

	private val STRAIGHT_STYLES = WALK_STYLES.filter { (lookAhead, _) -> lookAhead == 1 }

	private const val WALK_LOOKAHEAD_SEGMENTS = 3

	private val STEP_UP_HEADROOM = CellCondition(0, 2, 0, CellPredicate.CENTER_SLICE)

	private val HEADING_FAN_DEGREES = listOf(-12.0, 12.0)

	private const val HEADING_COMMIT_FRAMES = 12

	private val OFF_AXIS_LAUNCH_DELAYS = listOf(0, 2, 4)

	private val DECOUPLED_FACINGS = listOf(
		MovementKeys.FORWARD_RIGHT to -45.0,
		MovementKeys.FORWARD_LEFT to 45.0,
	)

	private const val DECOUPLE_TURN_DEGREES = 35.0

	private const val GRADUAL_TURN_RATE = 30.0

	private val AIRBORNE_STRAFE_KEYS = listOf(MovementKeys.FORWARD_LEFT, MovementKeys.FORWARD_RIGHT)

	private const val BEARING_LOOKAHEAD = 2

	private const val LAUNCH_REACH_BLOCKS = 4


	private const val LAUNCH_HEADING_DIFFICULTY = 0.45

	private const val BLIND_HEADING_DIFFICULTY = 0.95

	private const val GROUNDED_ON_AIR_EDGE_DIFFICULTY = 0.2
}
