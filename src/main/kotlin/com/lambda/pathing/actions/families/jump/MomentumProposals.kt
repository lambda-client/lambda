package com.lambda.pathing.actions.families.jump

import com.lambda.pathing.core.Stance
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.actions.CoarseMoveRates
import com.lambda.pathing.actions.ProposalContext
import com.lambda.pathing.actions.Proposals
import com.lambda.pathing.actions.TrajectoryDecision
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Momentum proposers, offered only when [ProposalContext.momentum] is set (the improver's
 * vocabulary; the search measured them as stall trades, docs/decisions/movement-tuning.md):
 * the chained sprint-jump gait and skips across cells the route merely walks. Targets come
 * from the steering chain, furthest reachable first; landing short is not a failure, the
 * rollout anchors wherever the body comes down.
 */
internal object MomentumProposals {

    fun proposals(context: ProposalContext): Proposals {
        if (!context.momentum) return Proposals.EMPTY
        val gait = gaitHop(context)
        val skips = skipProposals(context)
        if (gait == null) return skips
        return Proposals(launches = listOf(gait) + skips.launches)
    }

    /**
     * The chained sprint-jump gait: one natural-distance hop along a straight, level
     * chain stretch, as a solution-less [TrajectoryDecision.Launch] (open-loop, forward
     * held, jump on the first grounded tick). The landing anchor is grounded and fast,
     * so the next poll proposes the next hop; no chaining machinery is needed.
     */
    private fun gaitHop(context: ProposalContext): TrajectoryDecision? {
        val body = context.body
        if (!body.state.onGround) return null
        if (body.speed < GAIT_MIN_SPEED) return null
        val heading = body.heading() ?: return null
        val first = context.steps.firstOrNull()?.to ?: return null
        val chain = context.steering.chain(body.stance, first, GAIT_LOOKAHEAD, heading)
        val headingLength = hypot(heading.first, heading.second)
        var target: Stance? = null
        for (index in 1 until chain.size) {
            val cell = chain[index]
            // The level bound guards every crossed cell; alignment only the target cell
            // (chains zigzag half a block). See docs/decisions/movement-tuning.md (gait).
            if (abs(cell.y - body.stance.y) > 1) break
            val towardX = (cell.x - body.stance.x).toDouble()
            val towardZ = (cell.z - body.stance.z).toDouble()
            val distance = hypot(towardX, towardZ)
            if (distance > GAIT_MAX_HOP_BLOCKS) break
            if (distance < GAIT_MIN_HOP_BLOCKS) continue
            // Flown-over cells may rise a block; the landing may not (an uphill landing
            // spends the arc's tail on the climb and arrives slow).
            if (cell.y > body.stance.y) continue
            val alignment = (heading.first * towardX + heading.second * towardZ) /
                (headingLength * distance)
            if (alignment >= GAIT_MIN_ALIGNMENT) target = cell
        }
        val hop = target ?: return null
        return TrajectoryDecision.Launch(sprint = true, step = hop, delayFrames = 0, solution = null)
    }

    private fun skipProposals(context: ProposalContext): Proposals {
        val body = context.body
        if (!body.state.onGround) return Proposals.EMPTY
        if (body.speed < SKIP_MIN_SPEED) return Proposals.EMPTY
        val first = context.steps.firstOrNull()?.to ?: return Proposals.EMPTY
        val chain = context.steering.chain(body.stance, first, SKIP_LOOKAHEAD, body.heading())
        if (chain.size <= SKIP_MIN_CELLS) return Proposals.EMPTY
        val reachable = maxOf(body.speed, BallisticProfile.VANILLA.cruiseSpeed(sprint = true))
        // MOVING guides on both sides: the skipper is moving and lands moving, and the
        // blended guide mixed classes inconsistently across the comparison.
        val here = context.movingGuideTicks(body.stance)
        val heading = body.heading() ?: return Proposals.EMPTY
        for (index in chain.lastIndex downTo SKIP_MIN_CELLS) {
            val target = chain[index]
            if (target.y > body.stance.y) continue
            if (body.stance.y - target.y > SKIP_MAX_DROP) continue
            val towardX = (target.x - body.stance.x).toDouble()
            val towardZ = (target.z - body.stance.z).toDouble()
            val distance = hypot(towardX, towardZ)
            if (distance < CoarseMoveRates.MIN_JUMP_DISTANCE) continue
            // The body flies where its momentum points; a skip against the grain is a
            // turn first, and turns are the walk proposer's business.
            val alignment = (heading.first * towardX + heading.second * towardZ) /
                (hypot(heading.first, heading.second) * distance)
            if (alignment < SKIP_MIN_ALIGNMENT) continue
            val solution = LaunchSolver.best(
                body.stance, target,
                modes = JumpTemplates.MODES,
                maxEntrySpeed = { reachable },
            ) ?: continue
            // A skip must merge two gaps into one flight, cut the walked path, or convert
            // a real drop; flat jump spam loses on open ground. See
            // docs/decisions/movement-tuning.md (skip admission).
            var walked = 0.0
            var skipsGap = false
            for (step in 1..index) {
                val stride = hypot(
                    (chain[step].x - chain[step - 1].x).toDouble(),
                    (chain[step].z - chain[step - 1].z).toDouble(),
                )
                walked += stride
                if (stride > SKIP_GAP_STRIDE_BLOCKS) skipsGap = true
            }
            val falls = body.stance.y - target.y >= SKIP_MIN_FALL_BLOCKS
            val cuts = walked - distance >= SKIP_MIN_CUT_BLOCKS
            if (!skipsGap && !falls && !cuts) continue
            // And the model must still claim a saving on its own terms.
            if (here.isFinite()) {
                val there = context.movingGuideTicks(target)
                if (there.isFinite()) {
                    val flightTicks = solution.airTicks + SKIP_LAUNCH_PREP_TICKS
                    if (here - there < flightTicks + SKIP_MIN_SAVING_TICKS) continue
                }
            }
            return Proposals(
                launches = listOf(
                    TrajectoryDecision.Launch(solution.sprint, target, delayFrames = 0, solution),
                ),
            )
        }
        return Proposals.EMPTY
    }

    /** Below this the body has no momentum worth spending on a skip. */
    private const val SKIP_MIN_SPEED = 0.15

    /** Chain cells considered ahead; a skip past five is outside the solver's reach anyway. */
    private const val SKIP_LOOKAHEAD = 5

    /** A skip must clear at least two chain edges, or it is just the jump the edge already offers. */
    private const val SKIP_MIN_CELLS = 2

    private const val SKIP_MAX_DROP = 3

    /** Blocks of walked path a level skip must cut away to be worth a flight. */
    private const val SKIP_MIN_CUT_BLOCKS = 1.0

    private const val SKIP_MIN_FALL_BLOCKS = 2

    /** A chain stride longer than a walkable step: the ground between is a gap. */
    private const val SKIP_GAP_STRIDE_BLOCKS = 1.5

    /** cos(30 degrees): the flight must go where the momentum already points. */
    private const val SKIP_MIN_ALIGNMENT = 0.866

    /** Ticks of ground run a level launch typically needs before it is airborne. */
    private const val SKIP_LAUNCH_PREP_TICKS = 2

    private const val SKIP_MIN_SAVING_TICKS = 2.0

    /** The gait maintains speed; it does not create it. Near-sprint bodies only. */
    private const val GAIT_MIN_SPEED = 0.2

    private const val GAIT_LOOKAHEAD = 5

    /** Shorter than this is a step, not a hop. */
    private const val GAIT_MIN_HOP_BLOCKS = 2.5

    /** A level sprint hop's reliable reach; beyond it the arc needs solving, not assuming. */
    private const val GAIT_MAX_HOP_BLOCKS = 3.8

    /** cos(26 degrees): the hop's aim may wobble with the chain; real corners belong to walks. */
    private const val GAIT_MIN_ALIGNMENT = 0.9
}
