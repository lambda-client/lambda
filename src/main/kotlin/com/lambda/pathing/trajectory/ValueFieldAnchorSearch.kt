/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.coarse.CoarseEdge
import com.lambda.pathing.coarse.CoarseMoveKind
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.coarse.JumpArcProbe
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.debug.PlanningDebugChannel
import net.minecraft.util.math.Vec3d
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

data class ValueFieldSearchConfig(
    /** Rollouts the search may spend. One expansion is now one simulated action. */
    val maxExpansions: Int = 8000,
    /**
     * Expansions allowed since the incumbent last improved.
     *
     * The admissible bound is a straight-line heuristic, far too loose to prune a
     * frontier this wide, so without this the search always runs to [maxExpansions] —
     * tens of thousands of rollouts to confirm a five-block walk it certified almost
     * immediately. Stalling out instead makes the cost scale with how hard the route
     * actually is, which is what made a trivial live scenario cost a second of planning.
     */
    /**
     * Expansions without improving the incumbent before the search gives up.
     *
     * The binding constraint on quality, and the only knob that moved it: raising this
     * from 1200 to 3000 took the corpus from 2147 frames to 2134 and then saturated, while
     * [maxExpansions] made no difference at any value. The search was not running out of
     * room to explore, it was concluding too early that it had finished.
     *
     * The cost is latency on plans that would have stopped sooner, which is exactly what
     * the anytime prefix exists to absorb: the body is already walking a certified tape
     * while the rest is searched.
     */
    val stallExpansions: Int = 3000,
    val maxTransitionFrames: Int = 40,
    val launchDelays: List<Int> = listOf(0, 1, 2, 3, 4, 5, 6),
    /** Coarse steps offered out of one anchor. The route offered exactly one. */
    val branchingSteps: Int = 3,
    /**
     * How much worse than the best step an alternative may be priced and still be
     * simulated — roughly one block of detour.
     *
     * This was 24 ticks, chosen under the old expand-everything search where a wide
     * branch set was harmless because everything got simulated anyway. Under a search
     * that *dives*, a permissive margin is actively harmful: on a due-north route it
     * offered a north-*west* step as a branch, the dive followed it, and the winning tape
     * began by turning 48 degrees off the line and drifting a block and a half wide
     * before correcting — 167 degrees of total turning and 11% of extra distance on a
     * straight walk. At four ticks the same route turns zero degrees.
     */
    val branchMarginTicks: Double = 4.0,
    /**
     * Weight on the remaining-travel estimate when ordering the frontier.
     *
     * At 1.0 the search is breadth-like: it fans out across open ground and only reaches
     * the goal — and therefore its first certified tape — late, which is pure planning
     * latency. Weighting the tail drives it goalward first, so a tape exists early and
     * the incumbent cut can prune everything the field says cannot beat it. Ordering
     * only; termination still rests on the admissible bound.
     */
    val tailWeight: Double = 1.0,
    /**
     * Headings offered around the value-descent bearing, in degrees.
     *
     * Just the bearing itself. A *committed* heading is what stopped the body re-deciding
     * every block and let it build speed, and that was worth 17 frames on the walk-off
     * scenario. Fanned *offsets* are a different thing and measured actively harmful: on a
     * straight fourteen-block run they turned a tape that should hold one heading into 227
     * degrees of total turning and 4% of extra distance, because a committed 24-degree
     * deviation on open ground is a detour that then has to be corrected. Off-lattice
     * freedom belongs where the line genuinely needs it — see [DECOUPLED_FACINGS], which
     * is offered only where the route turns.
     */
    val headingFanDegrees: List<Double> = listOf(0.0, -12.0, 12.0),
    /**
     * Frames a held heading is committed for before the search may choose again.
     *
     * Without a commitment window every transition ends at the first stance change,
     * three or four ticks in, so the search re-picks a heading every block and the body
     * spends the whole tape turning: the walk-off probe whipped its yaw −13, −43, −72,
     * −42, −19 over five consecutive anchors and never exceeded 0.16 b/t where the
     * route-confined engine held 0.23. Momentum only pays if a decision is held long
     * enough to build it. This is also most of the search cost — a committed transition
     * covers several blocks for the price of one expansion.
     */
    val headingCommitFrames: Int = 12,
    /**
     * How much worse a sibling action is ranked than continuing down the line just taken.
     *
     * Must exceed the drift in `elapsed + value` across one good transition, or the search
     * goes straight back to breadth-first: a sibling costing epsilon more than its parent
     * would always outrank a child that has spent four honest ticks getting somewhere.
     * Roughly one transition's worth of ticks says "carry on unless this line is stalling".
     */
    val siblingPenaltyTicks: Double = 3.0,
    /**
     * Motion committed before the search looks for somewhere to stop.
     *
     * The anytime bootstrap: enough ticks that the body has something useful to be doing
     * while the rest is still being searched, short enough that it is found almost at once.
     */
    val safePrefixFrames: Int = 20,
    /**
     * How long the search may run before it is worth committing to a partial plan.
     *
     * Publishing a prefix is not free: the body starts walking it, so every branch that
     * leaves it becomes unadoptable and the search has to finish inside the one it chose.
     * When the full plan lands in the same tick as the prefix -- measured on a staircase,
     * where a 93-frame complete tape was refused as "diverges behind the cursor" moments
     * after a 33-frame partial was installed -- that trade is pure loss, four times over.
     * So the body waits, briefly. Only a search that is genuinely slow buys anything by
     * starting to walk.
     */
    val safePrefixDelayMillis: Long = 250,
    /**
     * Motion each commitment adds once the body is walking, and how little runway is left
     * before one is made.
     *
     * The search is not restarted between commitments -- it re-roots onto what has been
     * committed and carries on with the tree it already has. Restarting instead, searching
     * a long lookahead and throwing all but the first twenty frames of it away, made each
     * step slow enough that the body reached its brake and stopped: a walk that visibly
     * ran, halted, waited, and ran again.
     *
     * Committing is deferred until the runway runs low precisely because the search keeps
     * improving until then. The latest possible commitment is the best informed one.
     */
    val horizonCommitFrames: Int = 20,
    val horizonRunwayFrames: Int = 30,
    /** Whether commitments keep being made as the body walks, rather than only the first. */
    val commitContinuously: Boolean = false,
    /**
     * How far past the committed end the search may expand, or zero for no limit.
     *
     * The trajectory layer solves *local* movement; D* has already solved the global
     * problem, and the value field carries that answer to every stance. Searching on to
     * the goal re-solves it, and spends a fixed budget proving a future that will be
     * re-decided long before the body arrives.
     *
     * With a limit, anchors that reach it are parked rather than expanded. The parked set
     * *is* the population of candidate continuations: several genuinely different ways to
     * spend the next stretch, all kept alive, none of them committed to. They resume when
     * the horizon advances -- which happens when motion is committed and the whole window
     * slides forward.
     */
    val localHorizonFrames: Int = 0,
    /**
     * Frames from the committed end within which an anchor fans out instead of diving.
     *
     * The search dives by design: it expands one action and follows the successor, so
     * siblings are only reached when the good line stalls. That is what makes it fast, and
     * it is fatal to keeping a population -- measured, the candidate set was 180 anchors
     * that between them offered exactly *one* distinct next stance. A choice between one
     * option is not a choice, and committing "the best candidate" was committing to the
     * only thing on offer.
     *
     * Near the committed end that trade is wrong. Those are the frames about to be locked
     * in, the ones worth having alternatives for, and breadth there is cheap because the
     * window is bounded. Beyond it the search dives as before.
     */
    val rootFanFrames: Int = 0,
    /**
     * Expansions the search must spend between commitments.
     *
     * A quality floor. Without one, how good a commitment is depends entirely on how much
     * the search happened to explore before the body forced the decision -- which depends
     * on machine load, so the same build produced anywhere from 188 to 238 frames on the
     * same scenario across consecutive runs, and twice the collisions. Refusing to commit
     * until a minimum has been explored bounds that: the worst commitment the planner can
     * make is now a function of this number rather than of how busy the machine was.
     *
     * The body can still catch up while the floor is unmet, and then it stops on the brake
     * it is holding. That is the trade, and it is why the runway exists.
     */
    val minCommitExpansions: Int = 0,
    /**
     * Most motion a single publication may add once the body is walking.
     *
     * Finding a complete solution is not a reason to commit all of it. The search reaches
     * the goal as soon as the goal falls inside the local window, and publishing the whole
     * remainder then hands the body several seconds of future in one go -- measured, a
     * jump from 104 committed frames to 242 -- with nothing left open to improve. Holding
     * the solution and committing toward it in the ordinary way keeps the tail improvable
     * right up until it is walked.
     */
    val maxFinalCommitFrames: Int = 0,
    /** Steps of value-descending lookahead handed to the steering controller. */
    val chainLength: Int = 6,
    /** Ticks-to-go below which the braking terminal sweep is worth running. */
    val finishValueTicks: Double = 11.0,
    val maxFinishSweeps: Int = 64,
    /**
     * Whether launches may also choose how much speed to shed before taking off.
     *
     * Off for the first plan. It is a real capability -- one corpus route came in fifteen
     * frames shorter with it -- but every extra launch variant is search depth spent
     * elsewhere under a fixed expansion budget, and measured across the corpus the first
     * plan came out *longer* every way it was narrowed. Refinement is not on that budget:
     * it runs while the body already walks a certified tape, so it can afford the wider
     * vocabulary that the opening search cannot.
     */
    val offerBrakeTicks: Boolean = false,
    val frontierPerKey: Int = 3,
    val speedBucketBlocks: Double = 0.05,
    val yawBucketDegrees: Double = 20.0,
) {
    init {
        require(maxExpansions > 0)
        require(stallExpansions > 0)
        require(maxTransitionFrames > 0)
        require(launchDelays.isNotEmpty() && launchDelays.all { it >= 0 })
        require(branchingSteps > 0)
        require(chainLength > 0)
        require(siblingPenaltyTicks >= 0.0)
        require(safePrefixFrames > 0)
        require(safePrefixDelayMillis >= 0)
        require(horizonCommitFrames > 0)
        require(horizonRunwayFrames >= 0)
        require(headingFanDegrees.all { it.isFinite() })
        require(headingCommitFrames > 0)
        require(tailWeight >= 1.0)
        require(finishValueTicks >= 0.0)
        require(maxFinishSweeps >= 0)
        require(frontierPerKey > 0)
        require(speedBucketBlocks > 0.0)
        require(yawBucketDegrees > 0.0)
    }
}

/**
 * Kinodynamic anchor search steered by the coarse **value field** rather than by one
 * extracted route.
 *
 * [MotionAnchorSearch] is the same shape of search confined to a polyline: it steers at
 * `route.nodes`, indexes its state by route progress, and hard-rejects any rollout that
 * strays past `maxCorridorDeviation`. That makes the coarse layer's *greedy tie-breaks*
 * binding on the trajectory, which is not what the coarse layer knows. It knows the
 * cost-to-go field; the single chain is one readout of it.
 *
 * Here an anchor is identified by the **stance it stands on**, its successors are the
 * cheapest few coarse steps out of that stance by `edge + value`, and the controller
 * steers at a short lookahead chain redrawn from wherever the body actually is. There is
 * no corridor gate: leaving the greedy line is priced by the value field (going the wrong
 * way raises the tail), not vetoed. Everything else is unchanged — the simulator is still
 * the only thing that certifies anything, and the published tape is still one replay from
 * frame zero.
 *
 * The route is still accepted, but only for reporting and the coarse-feedback channel;
 * nothing in the search steers by it.
 */
object ValueFieldAnchorSearch {
    fun search(
        route: CoarseRoutePlan,
        field: CoarseValueField,
        initialState: MovementSimulationState,
        profile: PlayerPhysicsProfile,
        environment: SnapshotSimulationEnvironment,
        config: MotionConstraints = MotionConstraints(),
        searchConfig: ValueFieldSearchConfig = ValueFieldSearchConfig(),
        /**
         * Called the moment a *safe* partial tape exists — committed motion ending in a
         * certified stop — long before the full plan is known. Anytime execution starts
         * walking on this while the search keeps going; measured at 4 to 160 rollouts
         * against 473 to 2,526 for the complete plan.
         */
        onSafePrefix: ((MotionPlanResult.Success) -> Unit)? = null,
        /**
         * Frame the executor is about to press, or null when nothing is running.
         *
         * Read only to decide when the runway is short enough that another commitment is
         * due. The search never steers by it: what it commits is always a descendant of
         * what it has already committed, so the tape behind the cursor is identical by
         * construction.
         */
        cursorFrame: (() -> Int?)? = null,
        clock: SearchClock = SystemSearchClock(),
    ): MotionPlanResult {
        val unsupported = route.edges.mapTo(HashSet()) { it.kind }
            .filterTo(HashSet()) { it !in SUPPORTED_KINDS }
        if (unsupported.isNotEmpty()) return MotionPlanResult.UnsupportedRoute(unsupported)

        return Search(
            route, field, initialState, profile, environment, config, searchConfig,
            onSafePrefix, cursorFrame, clock,
        ).run()
    }

    /**
     * Bench-only view of the candidate population at each commitment.
     *
     * The design rests on there being *several genuinely different* ways to spend the next
     * stretch. Best-first search with dominance pruning is built to collapse alternatives,
     * so a population that has quietly become one line wearing many hats would look
     * identical from the outside while giving up everything this is for. Counting distinct
     * *lines* -- candidates that commit to different next stances -- is what tells them
     * apart; counting candidates alone would not.
     */
    val candidateCensus = java.util.concurrent.ConcurrentLinkedQueue<IntArray>()

    /** How often the live population is handed to the renderer, in expansions. */
    private const val CANDIDATE_PUBLISH_INTERVAL = 32

    /** More than this and the picture stops being readable. */
    internal const val MAX_SHOWN_CANDIDATES = 12

    /** Bounded, because a walk makes a commitment every second or so and never stops. */
    internal const val MAX_CENSUS_ENTRIES = 256

    /** Enough to outrank any descendant, so a near-root anchor offers every action. */
    private const val ROOT_FAN_BONUS = 1_000_000.0

    /** Skipping more of a plan than this means it is no longer that plan. */
    private const val MAX_SKIPPED_DECISIONS = 4

    private val SUPPORTED_KINDS = setOf(
        CoarseMoveKind.WALK,
        CoarseMoveKind.STEP_UP,
        CoarseMoveKind.WALK_OFF,
        CoarseMoveKind.JUMP_CANDIDATE,
    )

    /** The stance a grounded body stands on. Feet Y is the stance's own Y by construction. */
    internal fun stanceOf(state: MovementSimulationState): Stance = Stance(
        floor(state.position.x).toInt(),
        floor(state.position.y + STANCE_LEVEL_EPSILON).toInt(),
        floor(state.position.z).toInt(),
    )




    private class Search(
        private val route: CoarseRoutePlan,
        private val field: CoarseValueField,
        private val initialState: MovementSimulationState,
        private val profile: PlayerPhysicsProfile,
        private val environment: SnapshotSimulationEnvironment,
        private val config: MotionConstraints,
        private val searchConfig: ValueFieldSearchConfig,
        private val onSafePrefix: ((MotionPlanResult.Success) -> Unit)?,
        private val cursorFrame: (() -> Int?)?,
        private val clock: SearchClock,
    ) {
        /**
         * The safety gates with the corridor test disabled. Deviation from a chain that
         * is redrawn at every anchor is not a safety property, and vetoing it is exactly
         * the confinement this search exists to remove. Every gate that describes the
         * *world* — falls, grounded collisions, head bonks, unsupported physics — is
         * untouched.
         */
        private val gateConfig = config

        private val vocabulary = ActionSet(field, config, searchConfig)

        private val goalStance = route.goal
        private val goalPoint = goalStance.center()
        private val attempts = ArrayList<PlanAttempt>()

        private val routeIndex = route.nodes.withIndex().associate { (index, node) -> node to index }

        private val frontier: Frontier = Frontier(
            field, config, searchConfig, routeIndex,
            branchKey = { if (searchConfig.rootFanFrames > 0) horizon.branchOf(it) else null },
            reachable = { horizon.canReach(it) },
            incumbentFrames = { best?.frames },
        )

        private val horizon: HorizonController = HorizonController(
            searchConfig, field, frontier, clock, cursorFrame, onSafePrefix,
            expansions = { expansions },
            incumbent = { best },
            brakeFrom = ::brakeFrom,
            certify = ::certify,
        )
        private var finishSweeps = 0
        private var best: Solution? = null
        private var expansionsSinceImprovement = 0






        /** A field rather than a local, because commitment reads it from outside the loop. */
        private var expansions = 0


        private val rollouts = AnchorRollout(
            field, config, searchConfig, environment, profile, initialState, goalPoint,
            gateConfig, attempts, frontier::progressOf,
        )


        /**
         * Whether the executor has ever been seen running this plan.
         *
         * A null cursor means both "not installed yet" and "walk over", and only the
         * history tells them apart.
         */
        private var walking = false



        /**
         * Brake parameters that last produced a certified arrival.
         *
         * The terminal sweep is a grid — every brake distance times every step-up lead
         * times both gaits — and on a short route nearly every anchor is inside the finish
         * horizon, so running the whole grid from each of them dominated the entire search:
         * a five-block straight walk spent most of its rollouts re-deriving the same brake.
         * Once one set is known to work, it is tried alone, and the grid is only re-opened
         * when it fails.
         */
        private var provenFinish: TerminalApproach? = null



        fun run(): MotionPlanResult {
            frontier.admit(
                ValueAnchor(
                    state = initialState,
                    stance = stanceOf(initialState),
                    elapsed = 0,
                    collisionEvents = 0,
                    launchMargin = 0,
                    inputSwitches = 0,
                    parent = null,
                    inputs = emptyList(),
                    boundary = 0,
                )
            )

            horizon.begin()
            while (!frontier.isExhausted && expansions < searchConfig.maxExpansions) {
                // Lock in only when the body is about to run out of what it has. Every
                // expansion spent before that is one more spent improving the choice.
                val root = horizon.safeAnchor
                if (root != null) {
                    val executing = cursorFrame?.invoke()
                    if (executing != null) {
                        walking = true
                        val runway = root.elapsed - executing
                        if (runway <= searchConfig.horizonRunwayFrames) {
                            horizon.commitFromCandidates(
                                urgent = runway <= searchConfig.horizonCommitFrames,
                                // Walk the winning line down in small steps rather than
                                // saving it all for one publication at the end. The endgame
                                // used to arrive as a single ~80-frame commitment -- the
                                // whole local window -- because nothing committed toward
                                // the solution until the search had already given up.
                                along = best?.takeIf { !readyToFinish(it) }?.anchor,
                            )
                        }
                    } else if (walking) {
                        // The walk is over -- the goal, a stop, or a cancellation. Nothing
                        // this search decides can be executed any more, and grinding on to
                        // a budget set never to be reached is how a finished walk kept a
                        // worker busy for minutes.
                        //
                        // Only once the body has actually been seen walking. A null cursor
                        // also means "not installed yet", and the manager installs on its
                        // next client tick -- so reading it straight after the first
                        // commitment always sees null. Treating that as the end abandoned
                        // every walk one commitment in, and the body stopped and replanned
                        // instead: 78 halts across the live corpus, none of them real.
                        return finish(best ?: return abandoned())
                    }
                }
                // The first commitment, weighed like every other one. Waiting for a
                // population to exist costs a fraction of a second of standing still and
                // buys a first line that was actually compared -- committing the first
                // anchor to appear instead stranded two of six corpus routes in a dead end
                // that pruning then made permanent.
                if (horizon.safeAnchor == null && frontier.hasParked) horizon.commitFromCandidates(urgent = false)

                if (!frontier.hasOpen) {
                    // Nothing left to grow without more room. Committing is the only thing
                    // that makes room, so it happens whether or not the floor is met.
                    val toward = best?.takeIf { !readyToFinish(it) }?.anchor
                    if (!frontier.hasParked && toward == null) break
                    if (!horizon.commitFromCandidates(urgent = true, along = toward)) break
                }
                if (expansions % CANDIDATE_PUBLISH_INTERVAL == 0) horizon.publishCandidates()
                val entry = frontier.poll()

                // Past the local horizon: park it as a candidate rather than expanding it.
                // Growing it further would be planning a future the body has not committed
                // to reaching, which is exactly the work this layer should not be doing.
                //
                // Except within braking distance of the goal. That is the one place the
                // global problem becomes local again -- the field says there is nothing
                // left to route, only an arrival to execute -- and parking there means the
                // walk can never finish, only run out of candidates short of the goal.
                if (entry.anchor.elapsed >= horizon.horizonEnd &&
                    field.guide(entry.anchor.stance) > searchConfig.finishValueTicks
                ) {
                    frontier.park(entry)
                    continue
                }
                // Best-first optimality: `elapsed + lowerBound` is admissible, so once the
                // cheapest open anchor cannot beat the certified incumbent, nothing can.
                best?.let { if (entry.bound >= it.frames && readyToFinish(it)) return finish(it) }
                if (best != null && readyToFinish(best!!) &&
                    expansionsSinceImprovement >= searchConfig.stallExpansions
                ) {
                    return finish(best!!)
                }

                val anchor = entry.anchor
                val remaining = field.guide(anchor.stance)

                // The terminal sweep is the most expensive thing here — a whole brake grid
                // of full-length rollouts — so it is paid for once per anchor, on the pop
                // that first reaches it, and never from an anchor the field already says
                // cannot beat the incumbent.
                if (!anchor.sweptToGoal &&
                    remaining <= searchConfig.finishValueTicks &&
                    finishSweeps < searchConfig.maxFinishSweeps &&
                    best.let { it == null || anchor.elapsed + remaining < it.frames }
                ) {
                    anchor.sweptToGoal = true
                    finishSweeps++
                    finishFrom(anchor)?.let { retain(it) }
                }

                val actions = anchor.actions
                    ?: vocabulary.actions(anchor, anchor.hazardFrame).also { anchor.actions = it }
                if (anchor.cursor >= actions.size) continue

                // *One* action, not the whole family. Expanding every action before looking
                // at any result is what made a five-block straight walk cost 4,498 rollouts:
                // each of forty actions produced a slightly different yaw and speed, landed
                // in its own dominance bucket, and survived, so the frontier fanned out at
                // every block instead of collapsing. Simulating the best action alone lets
                // its successor — which is better than this anchor's remaining siblings
                // whenever the line is good — be popped next, and the search dives. Siblings
                // are only reached when the good line stalls, which is exactly where
                // branching is worth paying for.
                val action = actions[anchor.cursor++]
                expansions++
                clock.onExpansion()
                expansionsSinceImprovement++

                val outcome = rollouts.transition(anchor, action, anchor.hazardFrame)
                when (outcome) {
                    is Outcome.Anchored -> {
                        frontier.admit(outcome.anchor)
                        horizon.publishPrefix(outcome.anchor, expansions)
                    }
                    is Outcome.Arrived -> retain(
                        Solution(
                            inputs = anchor.prefix() +
                                outcome.frames.take(outcome.stopFrame + 1).map { it.input },
                            boundaries = anchor.boundaries() + anchor.elapsed,
                            segments = anchor.depth() + 1,
                            launchMargin = anchor.launchMargin,
                            parameters = TerminalApproach(
                                action.sprint, LOOK_AHEAD_NODES,
                                config.brakeDistances.first(), null,
                            ),
                            frames = anchor.elapsed + outcome.stopFrame + 1,
                            collisionEvents = anchor.collisionEvents,
                            anchor = anchor,
                        ),
                    )

                    is Outcome.Rejected -> if (action is TrajectoryDecision.Walk) {
                        anchor.hazardFrame = launchSeedFrame(outcome.diagnostic)
                            ?.let { frame -> anchor.hazardFrame?.coerceAtMost(frame) ?: frame }
                            ?: anchor.hazardFrame
                        // The hazard gates which actions are worth offering, so a family
                        // generated before it was known is stale.
                        if (anchor.hazardFrame != null && anchor.cursor >= actions.size) {
                            anchor.actions = vocabulary.actions(anchor, anchor.hazardFrame)
                        }
                    }
                }

                // Re-open this anchor for its next action. How urgently depends on what
                // just happened: while the line is alive, another action from here is a
                // fallback and can wait behind the successors it just produced; the moment
                // one *fails*, the next action from here is the whole point and must not
                // queue behind the entire frontier. Without this split a dive that hits a
                // wall recovers so slowly it exhausts its budget — a corpus case that used
                // to certify refused outright.
                if (anchor.cursor < (anchor.actions?.size ?: 0)) {
                    val committedElapsed = horizon.safeAnchor?.elapsed ?: 0
                    val nearRoot = searchConfig.rootFanFrames > 0 &&
                        anchor.elapsed - committedElapsed <= searchConfig.rootFanFrames
                    // Near the committed end the anchor is re-queued *ahead* of its own
                    // descendants, not merely without a penalty. Zero penalty does not fan:
                    // ordering is elapsed plus cost-to-go, which stays flat or falls along a
                    // good line, so each new successor outranks the parent and the dive
                    // simply continues. Only an explicit bonus brings the parent back to
                    // offer its next action, and since its action list is finite this
                    // terminates in a full fan rather than starving anything.
                    val penalty = when {
                        outcome is Outcome.Rejected -> RETRY_PENALTY_TICKS
                        nearRoot -> -ROOT_FAN_BONUS
                        else -> searchConfig.siblingPenaltyTicks
                    }
                    frontier.offer(
                        Frontier.OpenEntry(
                            order = entry.order + penalty,
                            bound = entry.bound,
                            anchor = anchor,
                        )
                    )
                }
            }

            best?.let { solution ->
                // Reaching the goal is not a reason to hand the body everything between
                // here and it. The loop's own finish checks are guarded, but this exit is
                // taken when the frontier simply runs out -- and unguarded it published a
                // 109-frame jump at the end of a walk whose other commitments were ten or
                // twenty. Commit along the winning line first, in the ordinary small
                // steps, and publish only what is left.
                while (!readyToFinish(solution) &&
                    horizon.commitFromCandidates(urgent = true, along = solution.anchor)
                ) {
                    // committing
                }
                return finish(solution)
            }

            return MotionPlanResult.NoSafeStop(
                attempts = attempts.toList(),
                blockedProgress = frontier.deepestProgress,
                deadEdge = null,
                remainingStart = route.nodes.getOrNull(frontier.deepestProgress),
                remainingGoal = goalStance,
            )
        }

        /**
         * The terminal sweep: the legacy corridor follower with its brake, step-up lead
         * schedule and terminal reacquisition, run on a value-descending chain that
         * actually reaches the goal.
         */
        private fun finishFrom(anchor: ValueAnchor): Solution? {
            val chain = field.chain(anchor.stance, null, FINISH_CHAIN_LENGTH)
            if (!field.reachesGoal(chain)) return null
            val points = chain.map { it.center() }
            val leads: List<Double?> = if (chain.zipWithNext().any { (from, to) -> to.y > from.y }) {
                config.stepUpJumpLeadDistances
            } else {
                listOf(null)
            }

            var bestFinish: Triple<TrajectoryRank, List<SimulatedTrajectoryFrame>, TerminalApproach>? = null
            // Cheapest first: the brake that already worked, then the full grid only if it
            // did not. On an easy arrival this is one rollout instead of dozens.
            val grid = buildList {
                provenFinish?.let { add(it) }
                for (sprint in config.sprintModes) {
                    for (brake in config.brakeDistances) {
                        for (lead in leads) {
                            add(TerminalApproach(sprint, LOOK_AHEAD_NODES, brake, lead))
                        }
                    }
                }
            }
            for (parameters in grid) {
                run {
                    run {
                        val evaluator = RolloutEvaluator(anchor.state, points, goalPoint, gateConfig)
                        var previous = anchor.state
                        var stopFrame: Int? = null
                        var failed = false
                        val rollout = TrajectoryRolloutEngine.rollout(
                            initialState = anchor.state,
                            profile = profile,
                            environment = environment,
                            program = CorridorFollowerProgram(chain, parameters, config),
                            frameCount = config.maxFrames,
                        ) { frame ->
                            val verdict = evaluator.observe(frame.index, frame.state, previous)
                            previous = frame.state
                            when (verdict) {
                                is RolloutVerdict.Stopped -> {
                                    stopFrame = verdict.frame
                                    true
                                }

                                is RolloutVerdict.Failed -> {
                                    failed = true
                                    true
                                }

                                is RolloutVerdict.Continue -> false
                            }
                        }
                        val evaluation = evaluate(rollout, points, goalPoint, gateConfig)
                        PlanningDebugChannel.publishAttempt(rollout, stopFrame != null, evaluation.diagnostic)
                        attempts += PlanAttempt(
                            parameters = parameters,
                            simulatedFrames = rollout.frames.size,
                            finalGoalError = hypot(
                                rollout.finalState.position.x - goalPoint.x,
                                rollout.finalState.position.z - goalPoint.z,
                            ),
                            finalHorizontalSpeed = rollout.finalState.velocity.horizontalLength(),
                            diagnostic = evaluation.diagnostic,
                            blockedProgress = frontier.progressOf(anchor.stance),
                        )
                        val stop = stopFrame ?: return@run
                        if (failed) return@run
                        val frames = rollout.frames.take(stop + 1)
                        val rank = TrajectoryRank(
                            certifiedAndSafe = true,
                            certifiedHorizon = route.nodes.lastIndex,
                            elapsedPlusTail = (anchor.elapsed + frames.size).toDouble(),
                            collisionEvents = anchor.collisionEvents + collisionEvents(anchor.state, frames),
                            launchMargin = anchor.launchMargin,
                            inputSwitches = anchor.inputSwitches +
                                inputSwitches(anchor.inputs.lastOrNull(), frames),
                        )
                        val incumbent = bestFinish
                        if (incumbent == null || rank < incumbent.first) {
                            bestFinish = Triple(rank, frames, parameters)
                        }
                        // The proven brake certified again: no reason to re-derive the grid.
                        if (parameters == provenFinish) return Solution(
                            inputs = anchor.prefix() + frames.map { it.input },
                            boundaries = anchor.boundaries() + anchor.elapsed,
                            segments = anchor.depth() + 1,
                            launchMargin = anchor.launchMargin,
                            parameters = parameters,
                            frames = anchor.elapsed + frames.size,
                            collisionEvents = rank.collisionEvents,
                            anchor = anchor,
                        )
                    }
                }
            }

            val winner = bestFinish ?: return null
            provenFinish = winner.third
            return Solution(
                inputs = anchor.prefix() + winner.second.map { it.input },
                boundaries = anchor.boundaries() + anchor.elapsed,
                segments = anchor.depth() + 1,
                launchMargin = anchor.launchMargin,
                parameters = winner.third,
                frames = anchor.elapsed + winner.second.size,
                collisionEvents = winner.first.collisionEvents,
                anchor = anchor,
            )
        }

        /**
         * Certifies "carry on to this anchor, then stop", or null if the body cannot stop
         * there safely.
         *
         * Cheap by construction — one rollout of released keys — because the whole point
         * is that a safe answer must be available long before an optimal one.
         */
        private fun brakeFrom(anchor: ValueAnchor): Solution? {
            val evaluator = RolloutEvaluator(anchor.state, listOf(anchor.stance.center()), goalPoint, gateConfig)
            var previous = anchor.state
            var failed = false
            val rollout = TrajectoryRolloutEngine.rollout(
                initialState = anchor.state,
                profile = profile,
                environment = environment,
                program = BrakeToStopProgram(anchor.state.rotation.yaw),
                frameCount = BRAKE_TAIL_FRAMES,
            ) { frame ->
                val verdict = evaluator.observe(frame.index, frame.state, previous)
                previous = frame.state
                if (verdict is RolloutVerdict.Failed) { failed = true; true } else false
            }
            if (failed) return null
            val stopped = rollout.frames.indexOfFirst {
                it.state.onGround && it.state.velocity.horizontalLength() <= config.stoppedSpeed
            }
            if (stopped < 0) return null
            val frames = rollout.frames.take(stopped + 1)
            // A partial plan hands the runtime a resting body and an unfinished journey,
            // so where it stops has to be somewhere the planner can start again from. A
            // brake that ends straddling two blocks, or on ground the coarse layer does
            // not model as standable, produces a body that has moved and can no longer be
            // routed — observed live as "no coarse route to the goal" one tape after a
            // staircase. Stopping a little earlier is always available; stopping somewhere
            // unplannable is not recoverable.
            val resting = stanceOf(frames.last().state)
            if (!field.isStance(resting) || !field.isMapped(resting)) return null
            return Solution(
                inputs = anchor.prefix() + frames.map { it.input },
                boundaries = anchor.boundaries() + anchor.elapsed,
                segments = anchor.depth() + 1,
                launchMargin = anchor.launchMargin,
                parameters = TerminalApproach(false, LOOK_AHEAD_NODES, config.brakeDistances.first(), null),
                frames = anchor.elapsed + frames.size,
                collisionEvents = anchor.collisionEvents + collisionEvents(anchor.state, frames),
                anchor = anchor,
            )
        }

        private fun certify(solution: Solution): MotionPlanResult {
            val tape = InputTape(solution.inputs)
            val tracked = environment.trackingView()
            val certified = TrajectoryRolloutEngine.rollout(
                initialState = initialState,
                profile = profile,
                environment = tracked,
                program = tape,
                frameCount = tape.frameCount,
            )
            if (!certified.completed || certified.frames.size != tape.frameCount) {
                return MotionPlanResult.UnstableReplay(
                    "value-field tape did not reproduce: ${certified.termination}"
                )
            }
            return MotionPlanResult.Success(
                sourceRoute = route,
                tape = tape,
                rollout = certified,
                parameters = solution.parameters,
                dependencies = route.dependencies + tracked.dependencies(),
                attempts = attempts.toList(),
                controlSegments = solution.segments,
                spliceFrames = solution.boundaries.filter { it in 1 until tape.frameCount },
                launchMarginFrames = solution.launchMargin,
            )
        }

        private fun retain(solution: Solution) {
            val incumbent = best
            if (incumbent == null || solution.score < incumbent.score) {
                best = solution
                expansionsSinceImprovement = 0
            }
        }

        /**
         * Whether a complete solution may be published now, or should be committed toward.
         *
         * A solution found while the body is still far from it would be published whole,
         * which is a commitment the size of the entire remaining walk. Committing toward it
         * first leaves the tail open to improvement until it is nearly walked, and costs
         * nothing: the solution is held, not discarded.
         */
        private fun readyToFinish(solution: Solution): Boolean {
            if (!searchConfig.commitContinuously || searchConfig.maxFinalCommitFrames <= 0) return true
            val committedElapsed = horizon.safeAnchor?.elapsed ?: return true
            return solution.frames - committedElapsed <= searchConfig.maxFinalCommitFrames
        }

        private fun finish(solution: Solution): MotionPlanResult = certify(solution)

        /**
         * What to return when the walk ended before the search did.
         *
         * Not a failure: whatever was committed was published as it was committed, and
         * every one of those tapes ended in a certified stop. There is simply nothing left
         * for this search to say.
         */
        private fun abandoned(): MotionPlanResult = MotionPlanResult.NoSafeStop(
            attempts = attempts.toList(),
            blockedProgress = frontier.deepestProgress,
            deadEdge = null,
            remainingStart = route.nodes.getOrNull(frontier.deepestProgress),
            remainingGoal = goalStance,
        )

    }

    internal const val LOOK_AHEAD_NODES = 1

    /** Frames a single contact is worth in winner selection; never a safety gate. */
    internal const val COLLISION_FRAME_PENALTY = 4

    /**
     * Ticks of remaining travel that a block per tick of carried speed is worth when
     * choosing between repairs. The same trade the search's own ranking makes.
     */
    private const val PROGRESS_SPEED_WEIGHT = 10.7

    /** Launch timings tried around a recorded one when re-deriving a plan. */
    private val LAUNCH_REPAIR_SHIFTS = listOf(1, -1, 2, -2)

    /** Delays kept around the one the jump hint's geometry actually asks for. */
    private const val SOLVED_LAUNCH_DELAYS = 7

    /** Ticks a released body needs to come to rest from a sprint, with headroom. */
    private const val BRAKE_TAIL_FRAMES = 24

    /** A just-failed anchor retries almost immediately; its alternatives are the point. */
    private const val RETRY_PENALTY_TICKS = 0.05

    /** Long enough for the brake schedule to see the goal from the finish horizon. */
    private const val FINISH_CHAIN_LENGTH = 24

    /** Feet Y sits exactly on the stance level; the epsilon absorbs float noise only. */
    private const val STANCE_LEVEL_EPSILON = 1e-6

}
