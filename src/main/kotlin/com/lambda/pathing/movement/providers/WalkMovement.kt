package com.lambda.pathing.movement.providers

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.movement.TemplateSpec
import com.lambda.pathing.movement.CellCondition
import com.lambda.pathing.movement.CellPredicate
import com.lambda.pathing.movement.CompletionContext
import com.lambda.pathing.movement.ControlProgram
import com.lambda.pathing.movement.DecisionContext
import com.lambda.pathing.movement.DecisionPrice
import com.lambda.pathing.movement.HeadingFollowerProgram
import com.lambda.pathing.movement.Movement
import com.lambda.pathing.movement.MovementContext
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.MovementKeys
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.bearingBetween
import com.lambda.pathing.core.headingOfBearing
import com.lambda.pathing.movement.ProgramContext
import com.lambda.pathing.movement.SegmentFollowerProgram
import com.lambda.pathing.movement.TrajectoryDecision
import com.lambda.pathing.core.center
import com.lambda.pathing.movement.ProposalContext
import com.lambda.pathing.movement.Proposals
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

        for (step in steps) {
            walks += decisions(DecisionContext(body, step, context.constraints, context.view))
        }

        val target = steps.first().to
        val bearing = routeBearing(context, target)

        // Both of these cost a steering-chain descent, and the loops below asked for them
        // once per sprint mode and again inside the offset fan. Neither depends on gait.
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

    /**
     * A steering heading is free; a launch heading is a gamble priced by where it lands.
     *
     * The distinction is [TrajectoryDecision.Heading.delayFrames]. With no delay the body
     * stays on the ground and merely turns, which is the cheapest thing it can do. With
     * one it leaves the ground on a free bearing, and the evaluator then holds the whole
     * flight above the steering chain's lowest node -- so a bearing with nothing to land
     * on at corridor height is a dozen frames of physics for a foregone refusal. Three
     * quarters of these were measured rejected, but removing them outright measured worse
     * than leaving them in: the search simply spent the budget colliding instead. Pricing
     * lets the cheap ones stay ahead without closing the door on the rest.
     */
    override fun price(decision: TrajectoryDecision, context: DecisionContext): DecisionPrice {
        // Walking an edge the coarse graph classified as a gap or a fall is offered on the
        // chance the geometry is kinder than the template, not because walking will do it.
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

    /**
     * Whether a body launching along [bearing] has a landing the corridor would accept.
     *
     * Samples the ray out to the reach of a sprint jump, between the steering chain's
     * lowest node and one block of jump rise, and asks only whether *some* stance exists
     * there -- not which one, and not that the arc lands on it. Unknown terrain answers
     * yes: an unstreamed section has to stay affordable so the rollout can report it
     * blocked and world capture can fill it in.
     */
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
            lookAheadNodes = (decision as? TrajectoryDecision.Walk)?.lookAheadNodes ?: DEFAULT_LOOK_AHEAD,
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

    fun stanceConditions(dx: Int, dy: Int, dz: Int) = listOf(
        CellCondition(dx, dy - 1, dz, CellPredicate.SUPPORT),
        CellCondition(dx, dy, dz, CellPredicate.CENTER_SLICE),
        CellCondition(dx, dy + 1, dz, CellPredicate.CENTER_HEAD),
    )

    val CARDINALS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

    val DIAGONALS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)

    /**
     * Follow styles offered per coarse step, as (lookAheadNodes, easeTurns).
     *
     * Collapsing these to one was tried: decisions fell 7% and simulated frames rose 8.5%,
     * because the search is bounded by its expansion budget rather than by the work in
     * front of it, so anything taken away is simply spent elsewhere. Left as they are --
     * the lever that matters is the budget, not the vocabulary.
     */
    private val WALK_STYLES = listOf(1 to false, 2 to false, 1 to true)

    private const val DEFAULT_LOOK_AHEAD = 1

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

    /** Reach of a sprint jump, rounded up: past this a launch heading has no landing. */
    private const val LAUNCH_REACH_BLOCKS = 4

    private const val LAUNCH_MAX_RISE = 1

    /**
     * A launch heading onto real ground: speculative even at its best.
     *
     * Priced above the cold temperature on purpose. A heading launch aims on a free
     * bearing rather than a solved arc, and the fan of them is large -- thirty-odd per
     * anchor once the offsets and delays multiply out. Leaving them affordable from the
     * start let them crowd out the solved jumps entirely. They are what a stall buys.
     */
    private const val LAUNCH_HEADING_DIFFICULTY = 0.45

    /** A launch heading with nothing to land on: kept, but last in line. */
    private const val BLIND_HEADING_DIFFICULTY = 0.95

    /** Keeping the feet down on an edge that needs air under them. */
    private const val GROUNDED_ON_AIR_EDGE_DIFFICULTY = 0.2
}
