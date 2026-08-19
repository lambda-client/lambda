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
    val stallExpansions: Int = 1200,
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
    /** Steps of value-descending lookahead handed to the steering controller. */
    val chainLength: Int = 6,
    /** Ticks-to-go below which the braking terminal sweep is worth running. */
    val finishValueTicks: Double = 11.0,
    val maxFinishSweeps: Int = 64,
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
        config: WalkingSeedSearchConfig = WalkingSeedSearchConfig(),
        searchConfig: ValueFieldSearchConfig = ValueFieldSearchConfig(),
        /**
         * Called the moment a *safe* partial tape exists — committed motion ending in a
         * certified stop — long before the full plan is known. Anytime execution starts
         * walking on this while the search keeps going; measured at 4 to 160 rollouts
         * against 473 to 2,526 for the complete plan.
         */
        onSafePrefix: ((WalkingSeedSearchResult.Success) -> Unit)? = null,
    ): WalkingSeedSearchResult {
        val unsupported = route.edges.mapTo(HashSet()) { it.kind }
            .filterTo(HashSet()) { it !in SUPPORTED_KINDS }
        if (unsupported.isNotEmpty()) return WalkingSeedSearchResult.UnsupportedRoute(unsupported)

        return Search(
            route, field, initialState, profile, environment, config, searchConfig, onSafePrefix,
        ).run()
    }

    private val SUPPORTED_KINDS = setOf(
        CoarseMoveKind.WALK,
        CoarseMoveKind.STEP_UP,
        CoarseMoveKind.WALK_OFF,
        CoarseMoveKind.JUMP_CANDIDATE,
    )

    /**
     * Re-derives a plan's inputs from a state it was never simulated from.
     *
     * This is what a splice needs and what a raw input tape cannot give. Each decision is
     * handed back to the controller that produced it, and the controller reads the actual
     * body: it steers at the same world targets and presses jump on the same *grounded
     * tick* rather than the same frame number, so a body arriving wide steers back and one
     * arriving late still launches from the ground. Measured on flat ground, a quarter of
     * a block of entry error destroys a raw replay (0 of 7 tails survived) and this
     * absorbs it (7 of 7), arriving slightly sooner than the originals because each
     * controller re-aims from where the body really is.
     *
     * It certifies nothing by itself — the caller still replays the whole spliced tape
     * from frame zero, which is the only thing that ever makes a plan publishable.
     */
    fun rerun(
        plan: TrajectoryPlanDecisions,
        from: MovementSimulationState,
        route: CoarseRoutePlan,
        field: CoarseValueField,
        profile: PlayerPhysicsProfile,
        environment: SnapshotSimulationEnvironment,
        config: WalkingSeedSearchConfig = WalkingSeedSearchConfig(),
        searchConfig: ValueFieldSearchConfig = ValueFieldSearchConfig(),
    ): List<MovementSimulationInput>? = rerunSegments(
        plan, from, route, field, profile, environment, config, searchConfig,
    )?.flatten()

    /**
     * As [rerun], but keeping the frames each decision produced separate.
     *
     * A caller that means to *edit* a plan needs this: splicing a re-run tail back on has
     * to know where each decision now ends, or the result carries boundaries describing
     * the tape it replaced rather than the one it is.
     */
    fun rerunSegments(
        plan: TrajectoryPlanDecisions,
        from: MovementSimulationState,
        route: CoarseRoutePlan,
        field: CoarseValueField,
        profile: PlayerPhysicsProfile,
        environment: SnapshotSimulationEnvironment,
        config: WalkingSeedSearchConfig = WalkingSeedSearchConfig(),
        searchConfig: ValueFieldSearchConfig = ValueFieldSearchConfig(),
    ): List<List<MovementSimulationInput>>? {
        val segments = ArrayList<List<MovementSimulationInput>>()
        var state = from

        for ((index, decision) in plan.decisions.withIndex()) {
            val intended = plan.intended.getOrNull(index)
            // Steering a decision is self-correcting; a *launch* is not. The controller
            // re-aims a walk that starts a little wide, but a jump taken a tenth of a
            // block further along lands a tenth of a block further along, and a one-block
            // pad does not forgive that — measured, re-running launches verbatim was no
            // better than replaying raw keys on rugged ground. So the launch *timing* is
            // re-chosen around the recorded one: a handful of rollouts, against the
            // hundreds that re-searching the tail would cost.
            // Take the repair that makes the most progress, not merely the first that
            // does not fail: a launch that survives but lands short leaves the rest of the
            // plan worse off than the one it replaced, which is how "any valid repair"
            // measured *worse* than no repair at all on small errors.
            val frames = repairedDecisions(decision)
                .mapNotNull { runDecision(it, state, route, field, profile, environment, config, searchConfig) }
                .minByOrNull { candidate -> intendedError(candidate.last().state, intended) } ?: return null
            segments += frames.map { it.input }
            state = frames.last().state
        }

        val terminal = plan.terminal ?: return segments
        val suffix = route.suffix(nearestNodeTo(route, state))
        val evaluator = RolloutEvaluator(
            state, suffix.nodes.map { it.center() }, route.goal.center(),
            config.copy(maxCorridorDeviation = Double.MAX_VALUE),
        )
        var previous = state
        var stopFrame: Int? = null
        val rollout = TrajectoryRolloutEngine.rollout(
            initialState = state,
            profile = profile,
            environment = environment,
            program = CorridorFollowerProgram(suffix.nodes, terminal, config),
            frameCount = config.maxFrames,
        ) { frame ->
            val verdict = evaluator.observe(frame.index, frame.state, previous)
            previous = frame.state
            when (verdict) {
                is RolloutVerdict.Stopped -> { stopFrame = verdict.frame; true }
                is RolloutVerdict.Failed -> true
                is RolloutVerdict.Continue -> false
            }
        }
        val stop = stopFrame ?: return null
        return segments + listOf(rollout.frames.take(stop + 1).map { it.input })
    }

    /** How far a re-run ended from where the plan meant it to; ties break on the recorded order. */
    private fun intendedError(reached: MovementSimulationState, intended: MovementSimulationState?): Double {
        if (intended == null) return 0.0
        val dx = reached.position.x - intended.position.x
        val dy = reached.position.y - intended.position.y
        val dz = reached.position.z - intended.position.z
        val speed = reached.velocity.horizontalLength() - intended.velocity.horizontalLength()
        return hypot(hypot(dx, dz), dy) + abs(speed) * SPEED_MATCH_WEIGHT
    }

    /** A block per tick of speed mismatch is worth about a block of position mismatch. */
    private const val SPEED_MATCH_WEIGHT = 3.0

    /** The decision as recorded, then the same launch a tick or two either side of it. */
    private fun repairedDecisions(decision: TrajectoryDecision): List<TrajectoryDecision> = when (decision) {
        is TrajectoryDecision.Launch -> buildList {
            add(decision)
            for (shift in LAUNCH_REPAIR_SHIFTS) {
                val delay = decision.delayFrames + shift
                if (delay >= 0) add(decision.copy(delayFrames = delay))
            }
        }

        is TrajectoryDecision.Heading -> decision.delayFrames?.let { recorded ->
            buildList {
                add(decision)
                for (shift in LAUNCH_REPAIR_SHIFTS) {
                    val delay = recorded + shift
                    if (delay >= 0) add(decision.copy(delayFrames = delay))
                }
            }
        } ?: listOf(decision)

        is TrajectoryDecision.Walk -> listOf(decision)
    }

    /** One decision, re-simulated from [state] to its own event. */
    private fun runDecision(
        decision: TrajectoryDecision,
        state: MovementSimulationState,
        route: CoarseRoutePlan,
        field: CoarseValueField,
        profile: PlayerPhysicsProfile,
        environment: SnapshotSimulationEnvironment,
        config: WalkingSeedSearchConfig,
        searchConfig: ValueFieldSearchConfig,
    ): List<SimulatedTrajectoryFrame>? {
        val stance = stanceOf(state)
        val heading = if (state.velocity.horizontalLength() <= 1e-6) null else {
            state.velocity.x to state.velocity.z
        }
        val chain = field.chain(stance, decision.step, searchConfig.chainLength, heading)
        val points = chain.map { it.center() }
        val launch = when (decision) {
            is TrajectoryDecision.Launch -> LaunchTrigger(decision.delayFrames)
            is TrajectoryDecision.Heading -> decision.delayFrames?.let { LaunchTrigger(it) }
            is TrajectoryDecision.Walk -> null
        }
        val program = if (decision is TrajectoryDecision.Heading) {
            HeadingFollowerProgram(
                targetYaw = decision.yaw,
                sprint = decision.sprint && decision.keys.sustainsSprint,
                maxYawChange = config.maxYawDegreesPerFrame,
                launch = launch,
                keys = decision.keys,
                airborneKeys = decision.airborneKeys,
            )
        } else {
            SegmentFollowerProgram(
                nodes = points,
                startProgress = 0,
                sprint = decision.sprint,
                lookAheadNodes = (decision as? TrajectoryDecision.Walk)?.lookAheadNodes ?: LOOK_AHEAD_NODES,
                launch = launch,
                maxYawChange = config.maxYawDegreesPerFrame,
                easeTurns = (decision as? TrajectoryDecision.Walk)?.easeTurns == true,
            )
        }

        val evaluator = RolloutEvaluator(
            state, points, route.goal.center(),
            config.copy(maxCorridorDeviation = Double.MAX_VALUE),
        )
        var previous = state
        var airborne = false
        var failed = false
        var eventFrame: Int? = null
        val committed = decision is TrajectoryDecision.Heading && decision.delayFrames == null

        val rollout = TrajectoryRolloutEngine.rollout(
            initialState = state,
            profile = profile,
            environment = environment,
            program = program,
            frameCount = searchConfig.maxTransitionFrames,
        ) { frame ->
            val verdict = evaluator.observe(frame.index, frame.state, previous)
            previous = frame.state
            when (verdict) {
                is RolloutVerdict.Failed -> { failed = true; true }
                is RolloutVerdict.Stopped -> { eventFrame = frame.index; true }
                is RolloutVerdict.Continue -> {
                    if (!frame.state.onGround) { airborne = true; false } else {
                        val now = stanceOf(frame.state)
                        val done = when {
                            launch != null -> launch.hasFired && airborne
                            committed -> frame.index + 1 >= searchConfig.headingCommitFrames
                            else -> now != stance
                        }
                        val moving = frame.state.velocity.horizontalLength() > config.stoppedSpeed
                        if (done && moving && now != stance) { eventFrame = frame.index; true } else false
                    }
                }
            }
        }
        if (failed) return null
        val frame = eventFrame ?: return null
        return rollout.frames.take(frame + 1)
    }

    private fun nearestNodeTo(route: CoarseRoutePlan, state: MovementSimulationState): Int {
        var best = 0
        var bestDistance = Double.MAX_VALUE
        route.nodes.forEachIndexed { index, node ->
            val dx = node.x + 0.5 - state.position.x
            val dz = node.z + 0.5 - state.position.z
            val dy = node.y - state.position.y
            val distance = dx * dx + dy * dy + dz * dz
            if (distance < bestDistance) { bestDistance = distance; best = index }
        }
        return best
    }

    /** The stance a grounded body stands on. Feet Y is the stance's own Y by construction. */
    internal fun stanceOf(state: MovementSimulationState): Stance = Stance(
        floor(state.position.x).toInt(),
        floor(state.position.y + STANCE_LEVEL_EPSILON).toInt(),
        floor(state.position.z).toInt(),
    )

    private class ValueAnchor(
        val state: MovementSimulationState,
        val stance: Stance,
        val elapsed: Int,
        val collisionEvents: Int,
        val launchMargin: Int,
        val inputSwitches: Int,
        val parent: ValueAnchor?,
        val inputs: List<MovementSimulationInput>,
        val boundary: Int,
        /** The decision this anchor was reached by; null for the root. */
        val via: TrajectoryDecision? = null,
    ) {
        /** Generated once, on the first pop; actions are deterministic for a given anchor. */
        var actions: List<TrajectoryDecision>? = null

        /** How many of [actions] have been simulated. The rest are tried only if needed. */
        var cursor: Int = 0

        /** Frame a walk from here met a hazard; gates the air-control family. */
        var hazardFrame: Int? = null

        /** Whether the terminal sweep has already been paid for from this anchor. */
        var sweptToGoal: Boolean = false

        val speed: Double get() = state.velocity.horizontalLength()

        /** The direction the body is actually travelling; null when it carries no momentum. */
        fun heading(): Pair<Double, Double>? =
            if (speed <= 1e-6) null else state.velocity.x to state.velocity.z

        /** States the decisions were simulated to reach, oldest first. */
        fun intendedStates(): List<MovementSimulationState> {
            val chain = ArrayList<MovementSimulationState>()
            var node: ValueAnchor? = this
            while (node?.via != null) {
                chain += node.state
                node = node.parent
            }
            return chain.asReversed()
        }

        /**
         * Frame each decision ends on, oldest first: one entry per decision.
         *
         * [elapsed] at an anchor *is* the end of the decision that reached it, so this is
         * just the chain of them. Distinct from [boundaries], which records where the
         * worker's controllers changed hands and is reported as splice frames.
         */
        fun decisionEnds(): List<Int> {
            val ends = ArrayList<Int>()
            var node: ValueAnchor? = this
            while (node?.via != null) {
                ends += node.elapsed
                node = node.parent
            }
            return ends.asReversed()
        }

        /** Decisions from the search's initial state to this anchor, oldest first. */
        fun decisions(): List<TrajectoryDecision> {
            val chain = ArrayList<TrajectoryDecision>()
            var node: ValueAnchor? = this
            while (node != null) {
                node.via?.let { chain += it }
                node = node.parent
            }
            return chain.asReversed()
        }

        fun prefix(): List<MovementSimulationInput> {
            val chain = ArrayList<List<MovementSimulationInput>>()
            var node: ValueAnchor? = this
            while (node != null) {
                if (node.inputs.isNotEmpty()) chain += node.inputs
                node = node.parent
            }
            chain.reverse()
            return chain.flatten()
        }

        fun boundaries(): List<Int> {
            val frames = ArrayList<Int>()
            var node: ValueAnchor? = this
            while (node?.parent != null) {
                frames += node.boundary
                node = node.parent
            }
            return frames.asReversed().dropLast(1)
        }

        fun depth(): Int {
            var depth = 0
            var node: ValueAnchor? = this
            while (node?.parent != null) {
                depth++
                node = node.parent
            }
            return depth
        }
    }

    private class Solution(
        val inputs: List<MovementSimulationInput>,
        val boundaries: List<Int>,
        val segments: Int,
        val launchMargin: Int,
        val parameters: WalkingSeedParameters,
        val frames: Int,
        val collisionEvents: Int,
        /** What the search decided, so the plan can be re-derived from a nearby state. */
        val decisions: List<TrajectoryDecision> = emptyList(),
        /** Terminal approach parameters, when the tape ended in the braking sweep. */
        val terminal: WalkingSeedParameters? = null,
        /** Frame each decision ends on; what makes a tail of the plan addressable. */
        val decisionEnds: List<Int> = emptyList(),

        /** Where each decision was simulated to end; repairs steer back toward these. */
        val intended: List<MovementSimulationState> = emptyList(),
    ) {
        /**
         * Winner selection currency: arrival time plus a toll per contact.
         *
         * Ranking on frames alone lets a tape that scrapes a wall and saves one tick beat
         * a clean one, which is the "it takes a collision instead of the better jump"
         * report. The toll was established at four frames per bump by the staircase work
         * (it cut a five-riser climb from ten bumps to four) and was lost when the search
         * was extracted out of the legacy sweep. It is a preference among *certified*
         * tapes only, never a safety gate: a necessary scrape — a rising jump grazing the
         * lip it clears — still wins when nothing cleaner certifies.
         */
        val score: Int get() = frames + COLLISION_FRAME_PENALTY * collisionEvents
    }

    /**
     * Bucketed by the stance the body stands on, not by progress along anything.
     *
     * Bucketing by ticks-to-go instead — the route-free analogue of the confined search's
     * route-node index — was tried and is worse: it makes geographically distinct anchors
     * that happen to be equally far from the goal compete for the same three slots, which
     * pruned the only line that certified one corpus case at all.
     */
    private data class AnchorKey(
        val stance: Stance,
        val yawBucket: Int,
        val speedBucket: Int,
    )

    private sealed interface Outcome {
        data class Anchored(val anchor: ValueAnchor) : Outcome
        data class Arrived(val frames: List<SimulatedTrajectoryFrame>, val stopFrame: Int) : Outcome
        data class Rejected(val diagnostic: TrajectoryDiagnostic) : Outcome
    }

    private class Search(
        private val route: CoarseRoutePlan,
        private val field: CoarseValueField,
        private val initialState: MovementSimulationState,
        private val profile: PlayerPhysicsProfile,
        private val environment: SnapshotSimulationEnvironment,
        private val config: WalkingSeedSearchConfig,
        private val searchConfig: ValueFieldSearchConfig,
        private val onSafePrefix: ((WalkingSeedSearchResult.Success) -> Unit)?,
    ) {
        /**
         * The safety gates with the corridor test disabled. Deviation from a chain that
         * is redrawn at every anchor is not a safety property, and vetoing it is exactly
         * the confinement this search exists to remove. Every gate that describes the
         * *world* — falls, grounded collisions, head bonks, unsupported physics — is
         * untouched.
         */
        private val gateConfig = config.copy(maxCorridorDeviation = Double.MAX_VALUE)

        private val goalStance = route.goal
        private val goalPoint = goalStance.center()
        private val attempts = ArrayList<WalkingSeedAttempt>()
        private val dominance = HashMap<AnchorKey, MutableList<ValueAnchor>>()

        private val open = PriorityQueue<OpenEntry>(compareBy { it.order })
        private val routeIndex = route.nodes.withIndex().associate { (index, node) -> node to index }
        private var finishSweeps = 0
        private var best: Solution? = null
        private var expansionsSinceImprovement = 0
        private var bestValue = Double.POSITIVE_INFINITY
        private var deepestProgress = 0

        /** Earliest certified *partial* plan: committed motion plus a stop. */
        private var firstSafe: Solution? = null
        private var firstSafeRollouts = 0

        /** The anchor whose brake was published, and the line the search re-rooted onto. */
        private var safeAnchor: ValueAnchor? = null

        /**
         * Whether the search has re-rooted onto the prefix the body is walking.
         *
         * Once a prefix is published *and* the executor has started it, the frames behind
         * the cursor are history: a plan that would have driven them differently cannot be
         * spliced on, so every branch leaving the committed line is unadoptable however
         * good it is. Left unpruned the search spends its whole budget out there and the
         * full plan arrives after the partial has already braked to a stop -- measured as
         * four separate stop-and-replan legs across the live corpus, on a staircase that
         * should have been one continuous walk.
         *
         * Committing cannot make execution less safe: the published prefix already ends in
         * a certified stop, so the worst case is the behaviour that happens today anyway.
         */
        private var committed = false

        private val startedNanos = System.nanoTime()
        private var rolloutsSpent = 0

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
        private var provenFinish: WalkingSeedParameters? = null


        private class OpenEntry(val order: Double, val bound: Double, val anchor: ValueAnchor)

        fun run(): WalkingSeedSearchResult {
            admit(
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

            var expansions = 0
            while (open.isNotEmpty() && expansions < searchConfig.maxExpansions) {
                val entry = open.poll()
                // Best-first optimality: `elapsed + lowerBound` is admissible, so once the
                // cheapest open anchor cannot beat the certified incumbent, nothing can.
                best?.let { if (entry.bound >= it.frames) return finish(it) }
                if (best != null && expansionsSinceImprovement >= searchConfig.stallExpansions) {
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
                    ?: actions(anchor, anchor.hazardFrame).also { anchor.actions = it }
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
                rolloutsSpent = expansions
                expansionsSinceImprovement++

                val outcome = transition(anchor, action, anchor.hazardFrame)
                when (outcome) {
                    is Outcome.Anchored -> {
                        admit(outcome.anchor)
                        publishPrefix(outcome.anchor, expansions)
                    }
                    is Outcome.Arrived -> retain(
                        Solution(
                            inputs = anchor.prefix() +
                                outcome.frames.take(outcome.stopFrame + 1).map { it.input },
                            boundaries = anchor.boundaries() + anchor.elapsed,
                            segments = anchor.depth() + 1,
                            launchMargin = anchor.launchMargin,
                            parameters = WalkingSeedParameters(
                                action.sprint, LOOK_AHEAD_NODES,
                                config.brakeDistances.first(), null,
                            ),
                            frames = anchor.elapsed + outcome.stopFrame + 1,
                            collisionEvents = anchor.collisionEvents,
                            decisions = anchor.decisions() + action,
                            decisionEnds = anchor.decisionEnds() +
                                (anchor.elapsed + outcome.stopFrame + 1),
                            intended = anchor.intendedStates(),
                        ),
                    )

                    is Outcome.Rejected -> if (action is TrajectoryDecision.Walk) {
                        anchor.hazardFrame = launchSeedFrame(outcome.diagnostic)
                            ?.let { frame -> anchor.hazardFrame?.coerceAtMost(frame) ?: frame }
                            ?: anchor.hazardFrame
                        // The hazard gates which actions are worth offering, so a family
                        // generated before it was known is stale.
                        if (anchor.hazardFrame != null && anchor.cursor >= actions.size) {
                            anchor.actions = actions(anchor, anchor.hazardFrame)
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
                    val penalty = if (outcome is Outcome.Rejected) RETRY_PENALTY_TICKS
                    else searchConfig.siblingPenaltyTicks
                    open += OpenEntry(
                        order = entry.order + penalty,
                        bound = entry.bound,
                        anchor = anchor,
                    )
                }
            }

            best?.let { return finish(it) }

            return WalkingSeedSearchResult.NoSafeStop(
                attempts = attempts.toList(),
                blockedProgress = deepestProgress,
                deadEdge = null,
                remainingStart = route.nodes.getOrNull(deepestProgress),
                remainingGoal = goalStance,
                remainingMoveSummary = "value-field search; %d anchors, best value %.1f ticks"
                    .format(dominance.values.sumOf { it.size }, bestValue),
            )
        }

        /**
         * The action family out of one anchor: the cheapest few coarse steps by
         * `edge + value`, each in the walk styles and as a launch lattice.
         *
         * This is the direct replacement for `route.edges[nodeIndex]`. The route offered
         * one committed step; the field offers the [ValueFieldSearchConfig.branchingSteps]
         * best and lets the physics decide between them.
         */
        private fun actions(anchor: ValueAnchor, hazardFrame: Int?): List<TrajectoryDecision> {
            val steps = field.steps(
                anchor.stance, searchConfig.branchingSteps, searchConfig.branchMarginTicks,
                anchor.heading(),
            )
            if (steps.isEmpty()) return emptyList()

            val actions = ArrayList<TrajectoryDecision>()
            val walks = ArrayList<TrajectoryDecision>()
            val launches = ArrayList<TrajectoryDecision>()
            for (sprint in config.sprintModes) {
                for (step in steps) {
                    for ((lookAhead, ease) in WALK_STYLES) {
                        walks += TrajectoryDecision.Walk(sprint, step.to, lookAhead, ease)
                    }
                }
            }
            // The off-lattice family, fanned around the bearing the value field is
            // descending toward. Offered from the best step only: a fan around a
            // second-choice direction is a fan around the wrong idea, and the second
            // choice gets its own fan once it has earned an anchor of its own.
            val bearing = descentBearing(anchor, steps.first().to)
            val target = steps.first().to
            val jumpFirst = steps.first().kind == CoarseMoveKind.JUMP_CANDIDATE
            for (sprint in config.sprintModes) {
                // The committed run along the bearing is always worth having: holding one
                // heading is what lets the body build speed instead of re-deciding every
                // block. Deviating from it is not. A fanned offset is a detour that must
                // later be corrected, and on a straight fourteen-block run the fan alone
                // cost 167 degrees of turning and 11% of extra distance. It earns its
                // place only where the straight line has been *shown* to fail, or where
                // the route is turning anyway.
                actionsForOffsets(anchor, sprint, target, bearing, walks)
                // Strafe is not a way to deviate — binary keys deviate by a blunt 45
                // degrees, where the yaw fan aims in twelves, and offering it that way
                // measured worse. Its value is *decoupling*: hold the same line of travel
                // while the body faces somewhere else, pre-aiming for the turn that comes
                // next. So it is offered only where the route actually turns; on a
                // straight run it is a strictly worse way to go forwards, and under a
                // fixed expansion budget every action offered costs search depth
                // elsewhere. Blanket-offering these measured worse on the corpus.
                if (turnsAhead(anchor, steps.first().to)) {
                    for ((keys, facingOffset) in DECOUPLED_FACINGS) {
                        walks += TrajectoryDecision.Heading(
                            sprint, target, bearing + facingOffset, facingOffset,
                            delayFrames = null, keys = keys,
                        )
                    }
                }
            }

            // No launches once the goal is inside braking range. A jump cannot help a
            // body stop, it commits ticks of airborne time it cannot steer out of, and
            // the terminal sweep owns the arrival anyway — so every launch offered here
            // is a rollout spent proving it made things worse. This is the visible
            // "jumping around for ages before it stops".
            if (field.guide(anchor.stance) <= searchConfig.finishValueTicks) return actions

            // A gap taken off-axis is the case the grid cannot describe at all: leaving a
            // pad at an angle to line the body up for the jump after it.
            for (sprint in config.sprintModes) {
                // Launches along the bearing are always available — on flat ground a
                // sprint-jump chain is genuinely faster than running. Launches at an
                // *angle* are the same trap as the walk offsets and were left ungated:
                // on a straight fourteen-block run the search took a -12 degree hop at
                // frame 7, ended up a block and a half off the line, and spent 130
                // degrees of turning at the far end coming back to the goal. Angled hops
                // are for terrain that turns, not for open ground.
                for (delay in OFF_AXIS_LAUNCH_DELAYS) {
                    launches += TrajectoryDecision.Heading(sprint, target, bearing, 0.0, delayFrames = delay)
                }
                if (anchor.hazardFrame != null || turnsAhead(anchor, target)) {
                    for (offset in searchConfig.headingFanDegrees) {
                        if (offset == 0.0) continue
                        for (delay in OFF_AXIS_LAUNCH_DELAYS) {
                            launches += TrajectoryDecision.Heading(
                                sprint, target, bearing + offset, offset, delayFrames = delay,
                            )
                        }
                    }
                }
                // Air control is offered only once a plain launch has been seen to fail:
                // the arc is otherwise fully committed at takeoff, and 0.026 a tick of
                // in-flight steering is exactly what rescues a jump that lands short or a
                // little wide. Offering it everywhere spends the budget proving that a
                // jump which was already going to land lands anyway.
                if (hazardFrame != null) {
                    for (delay in OFF_AXIS_LAUNCH_DELAYS) {
                        for (air in AIRBORNE_KEYS.drop(1)) {
                            launches += TrajectoryDecision.Heading(
                                sprint, target, bearing, 0.0, delayFrames = delay,
                                keys = MovementKeys.FORWARD, airborneKeys = air,
                            )
                        }
                    }
                }
            }

            // Launches are offered on the two most promising steps only: a launch is
            // seven simulations, and a third-choice direction that also needs a jump is
            // reachable through the anchor its own best step creates.
            for (sprint in config.sprintModes) {
                for (step in steps.take(LAUNCH_STEPS)) {
                    if (!canReach(anchor, step.to, sprint)) continue
                    for (delay in launchDelays(anchor, step)) {
                        launches += TrajectoryDecision.Launch(sprint, step.to, delay)
                    }
                }
            }
            // Order decides everything now. The search dives — it simulates an anchor's
            // *first* action and follows the successor — so whatever leads this list is
            // what actually gets tried, and the rest are fallbacks reached only if the
            // line stalls. Putting every walk ahead of every launch made the search walk
            // into a gap forever and never once press jump on a parkour it used to cross.
            // The coarse layer already knows which it is: if the step it wants is a jump
            // candidate, a launch is the primary action and walking is the fallback.
            actions += if (jumpFirst) launches + walks else walks + launches
            return actions
        }

        /** Whether the value-descending line turns materially within the next two steps. */
        private fun turnsAhead(anchor: ValueAnchor, firstStep: Stance): Boolean {
            val chain = field.chain(anchor.stance, firstStep, BEARING_LOOKAHEAD + 1, anchor.heading())
            if (chain.size < 3) return false
            val first = bearingBetween(chain[0].center(), chain[1].center())
            val second = bearingBetween(chain[1].center(), chain[chain.lastIndex].center())
            return abs(Rotation.wrap(second - first)) >= DECOUPLE_TURN_DEGREES
        }

        /**
         * The direction the value field is descending in, as a continuous bearing from
         * where the body actually stands — not a bearing between two block centres.
         *
         * Aimed a couple of steps out rather than at the immediate neighbour, so the fan
         * is spread around the line the route is going, not around one lattice step.
         */
        private fun descentBearing(anchor: ValueAnchor, firstStep: Stance): Double {
            val chain = field.chain(anchor.stance, firstStep, BEARING_LOOKAHEAD, anchor.heading())
            val aim = chain[minOf(chain.lastIndex, BEARING_LOOKAHEAD)].center()
            return bearingBetween(
                HorizontalPoint(anchor.state.position.x, anchor.state.position.y, anchor.state.position.z),
                aim,
            )
        }

        /**
         * Whether a jump from this body could reach [target] at all.
         *
         * Seven launch rollouts per step were being spent proving arcs that fall short by
         * a block, which the measured reach model answers for free. The entry speed is the
         * best the body could have by takeoff — its current speed, or the gait's
         * equilibrium if it is still accelerating — so the filter is optimistic and can
         * only discard jumps that are physically out of range, never ones the simulator
         * would have certified.
         */
        private fun canReach(anchor: ValueAnchor, target: Stance, sprint: Boolean): Boolean {
            val entrySpeed = maxOf(anchor.speed, if (sprint) SPRINT_TOP_SPEED else WALK_TOP_SPEED)
            val distance = hypot(
                target.x + 0.5 - anchor.state.position.x,
                target.z + 0.5 - anchor.state.position.z,
            )
            val rise = target.y - anchor.stance.y
            return distance <= JumpArcProbe.maxReach(entrySpeed, rise) + REACH_SLACK_BLOCKS
        }

        /**
         * The committed straight run, plus fanned offsets only where they can help.
         *
         * Splitting these apart is the difference between a tape that holds its line on
         * open ground and one that weaves across it.
         */
        private fun actionsForOffsets(
            anchor: ValueAnchor,
            sprint: Boolean,
            target: Stance,
            bearing: Double,
            into: MutableList<TrajectoryDecision>,
        ) {
            into += TrajectoryDecision.Heading(sprint, target, bearing, 0.0, delayFrames = null)
            if (anchor.hazardFrame == null && !turnsAhead(anchor, target)) return
            for (offset in searchConfig.headingFanDegrees) {
                if (offset == 0.0) continue
                into += TrajectoryDecision.Heading(sprint, target, bearing + offset, offset, delayFrames = null)
            }
        }

        private fun launchDelays(anchor: ValueAnchor, edge: CoarseEdge): List<Int> {
            val hint = edge.jumpHint ?: return searchConfig.launchDelays.sortedDescending()
            val from = edge.from.center()
            val to = edge.to.center()
            val along = alongEdge(from, to, anchor.state.position.x, anchor.state.position.z)
            val perTick = alongEdge(
                from, to,
                from.x + anchor.state.velocity.x,
                from.z + anchor.state.velocity.z,
            )
            return searchConfig.launchDelays.sortedBy { delay ->
                abs(along + delay * perTick - hint.launchOffsetBlocks)
            }
        }

        /**
         * One local transition, simulated once and stopped at its next event.
         *
         * The event is "the body is grounded and moving on a *different* stance than it
         * started on" — a physical fact about where the body is, not a projection onto a
         * planned node. A launch additionally has to actually leave the ground first.
         */
        private fun transition(anchor: ValueAnchor, action: TrajectoryDecision, hazardFrame: Int?): Outcome {
            val chain = field.chain(
                anchor.stance, action.step, searchConfig.chainLength, anchor.heading(),
            )
            val points = chain.map { it.center() }
            val launch = when (action) {
                is TrajectoryDecision.Launch -> LaunchTrigger(action.delayFrames)
                is TrajectoryDecision.Heading -> action.delayFrames?.let { LaunchTrigger(it) }
                is TrajectoryDecision.Walk -> null
            }
            val program = if (action is TrajectoryDecision.Heading) {
                HeadingFollowerProgram(
                    targetYaw = action.yaw,
                    sprint = action.sprint && action.keys.sustainsSprint,
                    maxYawChange = config.maxYawDegreesPerFrame,
                    launch = launch,
                    keys = action.keys,
                    airborneKeys = action.airborneKeys,
                )
            } else {
                SegmentFollowerProgram(
                    nodes = points,
                    startProgress = 0,
                    sprint = action.sprint,
                    lookAheadNodes = (action as? TrajectoryDecision.Walk)?.lookAheadNodes ?: LOOK_AHEAD_NODES,
                    launch = launch,
                    maxYawChange = config.maxYawDegreesPerFrame,
                    easeTurns = (action as? TrajectoryDecision.Walk)?.easeTurns == true,
                )
            }
            val evaluator = RolloutEvaluator(anchor.state, points, goalPoint, gateConfig)
            var previous = anchor.state
            var airborne = false
            var failure: TrajectoryDiagnostic? = null
            var stopFrame: Int? = null
            var eventFrame: Int? = null
            var eventStance = anchor.stance

            val rollout = TrajectoryRolloutEngine.rollout(
                initialState = anchor.state,
                profile = profile,
                environment = environment,
                program = program,
                frameCount = searchConfig.maxTransitionFrames,
            ) { frame ->
                val verdict = evaluator.observe(frame.index, frame.state, previous)
                previous = frame.state
                when (verdict) {
                    is RolloutVerdict.Failed -> {
                        failure = verdict.diagnostic
                        true
                    }

                    is RolloutVerdict.Stopped -> {
                        stopFrame = verdict.frame
                        true
                    }

                    is RolloutVerdict.Continue -> {
                        if (!frame.state.onGround) {
                            airborne = true
                            false
                        } else {
                            val stance = stanceOf(frame.state)
                            val done = when {
                                launch != null -> launch.hasFired && airborne
                                // A held heading is a commitment, not a per-block choice.
                                action is TrajectoryDecision.Heading ->
                                    frame.index + 1 >= searchConfig.headingCommitFrames
                                else -> stance != anchor.stance
                            }
                            val moving = frame.state.velocity.horizontalLength() > config.stoppedSpeed
                            if (done && moving && stance != anchor.stance) {
                                eventFrame = frame.index
                                eventStance = stance
                                true
                            } else {
                                false
                            }
                        }
                    }
                }
            }

            record(anchor, action, rollout, failure, stopFrame != null)

            stopFrame?.let { return Outcome.Arrived(rollout.frames, it) }
            failure?.let { return Outcome.Rejected(it) }
            val frame = eventFrame ?: return Outcome.Rejected(
                evaluate(rollout, points, goalPoint, gateConfig).diagnostic
                    ?: TrajectoryDiagnostic.NoStop(rollout.frames.size, 0.0, anchor.speed),
            )

            val frames = rollout.frames.take(frame + 1)
            // A landing on ground the coarse layer does not model as standable — or that
            // the value field cannot price — has no value and no usable successors.
            // Anchoring there strands the search on a state it can neither rank nor
            // continue, and ranking it by a straight-line guess is what sends the search
            // off in the wrong direction.
            if (!field.isStance(eventStance) || !field.isMapped(eventStance)) {
                return Outcome.Rejected(
                    TrajectoryDiagnostic.FellBelowRoute(frame, 0.0)
                )
            }

            return Outcome.Anchored(
                ValueAnchor(
                    state = frames.last().state,
                    stance = eventStance,
                    elapsed = anchor.elapsed + frames.size,
                    collisionEvents = anchor.collisionEvents + collisionEvents(anchor.state, frames),
                    launchMargin = anchor.launchMargin + launchMargin(frames, hazardFrame),
                    inputSwitches = anchor.inputSwitches + inputSwitches(anchor.inputs.lastOrNull(), frames),
                    parent = anchor,
                    inputs = frames.map { it.input },
                    boundary = anchor.elapsed + frames.size,
                    via = action,
                ),
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

            var bestFinish: Triple<TrajectoryRank, List<SimulatedTrajectoryFrame>, WalkingSeedParameters>? = null
            // Cheapest first: the brake that already worked, then the full grid only if it
            // did not. On an easy arrival this is one rollout instead of dozens.
            val grid = buildList {
                provenFinish?.let { add(it) }
                for (sprint in config.sprintModes) {
                    for (brake in config.brakeDistances) {
                        for (lead in leads) {
                            add(WalkingSeedParameters(sprint, LOOK_AHEAD_NODES, brake, lead))
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
                        attempts += WalkingSeedAttempt(
                            parameters = parameters,
                            simulatedFrames = rollout.frames.size,
                            finalGoalError = hypot(
                                rollout.finalState.position.x - goalPoint.x,
                                rollout.finalState.position.z - goalPoint.z,
                            ),
                            finalHorizontalSpeed = rollout.finalState.velocity.horizontalLength(),
                            diagnostic = evaluation.diagnostic,
                            blockedProgress = progressOf(anchor.stance),
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
                            decisions = anchor.decisions(),
                            decisionEnds = anchor.decisionEnds(),
                            intended = anchor.intendedStates(),
                            terminal = parameters,
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
                decisions = anchor.decisions(),
                decisionEnds = anchor.decisionEnds(),
                intended = anchor.intendedStates(),
                terminal = winner.third,
            )
        }

        /**
         * The anytime unit: once enough motion is committed, find out whether the body
         * can simply stop there. A prefix that ends in a certified stop is safe to
         * execute on its own, so it can be published while the search carries on
         * improving what comes after it.
         *
         * Published at most once per search, and only once the search has been running
         * long enough that standing still is the worse option. Publishing re-roots the
         * search onto this line, so it is a commitment, not a hint.
         */
        private fun publishPrefix(anchor: ValueAnchor, expansions: Int) {
            val publish = onSafePrefix ?: return
            // Inside the finish horizon the terminal sweep owns the approach, and braking
            // to a stop there is not a partial plan at all: it is a worse complete one.
            // Three of the four stop-and-replan legs in the live corpus were this -- a
            // prefix that braked to rest *past* the goal, so the body then had to plan a
            // second leg to walk back the block it had just overshot.
            val remaining = field.guide(anchor.stance)
            if (!remaining.isFinite() || remaining <= searchConfig.finishValueTicks) return

            if (safeAnchor != null) return
            if (anchor.elapsed < searchConfig.safePrefixFrames) return
            if ((System.nanoTime() - startedNanos) / 1_000_000 < searchConfig.safePrefixDelayMillis) return
            val braked = brakeFrom(anchor) ?: return
            val certified = certify(braked) as? WalkingSeedSearchResult.Success ?: return
            if (firstSafe == null) {
                firstSafe = braked
                firstSafeRollouts = expansions
            }
            safeAnchor = anchor
            reRootOnto(anchor)
            publish(certified)
        }

        /**
         * Drops everything the body can no longer be steered onto.
         *
         * Done the moment a prefix is *published*, not when the executor starts running
         * it. Waiting for the cursor loses the race: on a staircase the complete plan was
         * found in the same tick the partial was installed, on a branch the body could no
         * longer join, and was refused. Re-rooting here means every solution the search
         * finds afterwards shares the walked tape by construction, so there is nothing
         * left for adoption to reject.
         */
        private fun reRootOnto(anchor: ValueAnchor) {
            val retained = open.filterTo(ArrayList()) { it.anchor.descendsFrom(anchor) }
            open.clear()
            open.addAll(retained)
            committed = true
        }

        private fun ValueAnchor.descendsFrom(other: ValueAnchor): Boolean {
            var node: ValueAnchor? = this
            while (node != null) {
                if (node === other) return true
                node = node.parent
            }
            return false
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
                parameters = WalkingSeedParameters(false, LOOK_AHEAD_NODES, config.brakeDistances.first(), null),
                frames = anchor.elapsed + frames.size,
                collisionEvents = anchor.collisionEvents + collisionEvents(anchor.state, frames),
                decisions = anchor.decisions(),
                decisionEnds = anchor.decisionEnds(),
                intended = anchor.intendedStates(),
            )
        }

        private fun certify(solution: Solution): WalkingSeedSearchResult {
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
                return WalkingSeedSearchResult.UnstableReplay(
                    "value-field tape did not reproduce: ${certified.termination}"
                )
            }
            return WalkingSeedSearchResult.Success(
                sourceRoute = route,
                tape = tape,
                rollout = certified,
                parameters = solution.parameters.copy(
                    gapLaunchFrames = certified.frames.filter { it.input.jump }.map { it.index },
                ),
                dependencies = route.dependencies + tracked.dependencies(),
                attempts = attempts.toList(),
                controlSegments = solution.segments,
                spliceFrames = solution.boundaries.filter { it in 1 until tape.frameCount },
                launchMarginFrames = solution.launchMargin,
                planDecisions = TrajectoryPlanDecisions(
                    decisions = solution.decisions,
                    intended = solution.intended,
                    boundaries = solution.decisionEnds,
                    terminal = solution.terminal,
                ),
                safePrefix = firstSafe?.let {
                    WalkingSeedSearchResult.SafePrefix(it.frames, firstSafeRollouts, rolloutsSpent)
                },
            )
        }

        private fun retain(solution: Solution) {
            val incumbent = best
            if (incumbent == null || solution.score < incumbent.score) {
                best = solution
                expansionsSinceImprovement = 0
            }
        }

        private fun finish(solution: Solution): WalkingSeedSearchResult = certify(solution)

        private fun admit(anchor: ValueAnchor) {
            // The body's own stance can be unmapped (it may stand somewhere the coarse
            // layer never labelled). The root still has to be expanded, so it falls back
            // to the admissible bound; every later anchor is refused instead of guessed.
            val guide = field.guide(anchor.stance)
                .takeIf { it.isFinite() }
                ?: if (anchor.parent == null) field.lowerBound(anchor.stance) else return
            if (guide < bestValue) bestValue = guide
            deepestProgress = maxOf(deepestProgress, progressOf(anchor.stance))

            val key = AnchorKey(
                stance = anchor.stance,
                yawBucket = yawBucket(anchor),
                speedBucket = speedBucket(anchor),
            )
            // Past the commitment point only the walked line can still be published.
            if (committed) safeAnchor?.let { if (!anchor.descendsFrom(it)) return }

            val bucket = dominance.getOrPut(key) { ArrayList() }
            if (bucket.any { it.dominates(anchor) }) return
            bucket.removeAll { anchor.dominates(it) }
            if (bucket.size >= searchConfig.frontierPerKey) {
                val worst = bucket.maxByOrNull { it.elapsed } ?: return
                if (worst.elapsed <= anchor.elapsed) return
                bucket.remove(worst)
            }
            bucket += anchor
            // Incumbent cut. `guide` estimates the remaining travel closely (it is the
            // coarse layer's measured cost-to-go), so an anchor already projected to
            // finish later than a certified tape is not worth a rollout. The admissible
            // bound below still governs *termination*; this only declines to open a
            // branch the field says is already beaten — without it the free search keeps
            // fanning out across open ground long after it has a good tape, which is the
            // whole of the planning-latency regression.
            best?.let { if (anchor.elapsed + guide >= it.frames) return }

            open += OpenEntry(
                order = anchor.elapsed + searchConfig.tailWeight * momentumAdjusted(anchor, guide),
                // Only the credit may touch an admissible bound, and only as its constant
                // maximum: subtracting a constant from a lower bound leaves a lower bound,
                // while charging this anchor's turn cost to one would not.
                bound = anchor.elapsed +
                    (field.lowerBound(anchor.stance) - MOMENTUM_CREDIT_MAX_TICKS).coerceAtLeast(0.0),
                anchor = anchor,
            )
        }

        private fun ValueAnchor.dominates(other: ValueAnchor): Boolean =
            elapsed <= other.elapsed &&
                speed >= other.speed - SPEED_DOMINANCE_SLACK &&
                collisionEvents <= other.collisionEvents &&
                inputSwitches <= other.inputSwitches

        /**
         * The stance value, corrected for what this body is actually doing.
         *
         * The coarse value prices a stationary body, so without this an anchor sprinting
         * at the goal, one stopped on the same block, and one sprinting *away* all score
         * the same — and a frontier that cannot separate them stops being best-first.
         * Ordering only; [MomentumValue] explains why the bound may not use it.
         */
        private fun momentumAdjusted(anchor: ValueAnchor, guide: Double): Double {
            val next = field.steps(anchor.stance, 1, heading = anchor.heading())
                .firstOrNull()?.to?.center() ?: return guide
            val alignment = headingAlignment(
                anchor.state.velocity.x, anchor.state.velocity.z,
                next.x - anchor.state.position.x, next.z - anchor.state.position.z,
            )
            val headingError = Math.toDegrees(kotlin.math.acos(alignment.coerceIn(-1.0, 1.0)))
            return guide -
                momentumCredit(anchor.speed, alignment) +
                momentumTurnCost(anchor.speed, headingError, config.maxYawDegreesPerFrame)
        }

                private fun yawBucket(anchor: ValueAnchor): Int = floor(
            ((anchor.state.rotation.yaw % 360.0) + 360.0) % 360.0 / searchConfig.yawBucketDegrees
        ).toInt()

        private fun speedBucket(anchor: ValueAnchor): Int =
            floor(anchor.speed / searchConfig.speedBucketBlocks).toInt()

        /** Best-effort route index for reporting only; the search never steers by it. */
        private fun progressOf(stance: Stance): Int = routeIndex[stance] ?: deepestProgress

        private fun record(
            anchor: ValueAnchor,
            action: TrajectoryDecision,
            rollout: TrajectoryRollout,
            failure: TrajectoryDiagnostic?,
            stopped: Boolean,
        ) {
            PlanningDebugChannel.publishAttempt(rollout, stopped, failure)
            attempts += WalkingSeedAttempt(
                parameters = WalkingSeedParameters(
                    sprint = action.sprint,
                    lookAheadNodes = (action as? TrajectoryDecision.Walk)?.lookAheadNodes ?: LOOK_AHEAD_NODES,
                    brakeDistance = config.brakeDistances.first(),
                    stepUpJumpLeadDistance = null,
                    gapLaunchFrames = if (action is TrajectoryDecision.Walk) emptyList() else {
                        rollout.frames.filter { frame -> frame.input.jump }.map { frame -> frame.index }
                    },
                ),
                simulatedFrames = rollout.frames.size,
                finalGoalError = hypot(
                    rollout.finalState.position.x - goalPoint.x,
                    rollout.finalState.position.z - goalPoint.z,
                ),
                finalHorizontalSpeed = rollout.finalState.velocity.horizontalLength(),
                diagnostic = failure,
                blockedProgress = progressOf(anchor.stance),
            )
        }
    }

    private val WALK_STYLES = listOf(1 to false, 2 to false, 1 to true)

    private const val LOOK_AHEAD_NODES = 1

    /** Frames a single contact is worth in winner selection; never a safety gate. */
    private const val COLLISION_FRAME_PENALTY = 4

    /** Walk equilibrium (b/t); the slower of the two measured launch families. */
    private const val WALK_TOP_SPEED = 0.2159

    /**
     * Reach headroom before a launch is refused without simulating it. The model is
     * measured from stance centres and the body may launch from anywhere in its block, so
     * this covers that offset and keeps the filter on the permissive side of any error.
     */
    private const val REACH_SLACK_BLOCKS = 0.75

    /**
     * Ticks of remaining travel that a block per tick of carried speed is worth when
     * choosing between repairs. The same trade the search's own ranking makes.
     */
    private const val PROGRESS_SPEED_WEIGHT = 10.7

    /** Launch timings tried around a recorded one when re-deriving a plan. */
    private val LAUNCH_REPAIR_SHIFTS = listOf(1, -1, 2, -2)

    /** Grounded ticks after the anchor at which an off-axis launch may fire. */
    private val OFF_AXIS_LAUNCH_DELAYS = listOf(0, 2, 4)

    /**
     * Facings the body may carry while still travelling along the descent bearing.
     *
     * A strafe key moves travel 45 degrees off the facing, so facing 45 degrees the other
     * way puts travel back on the bearing. The pair is the manoeuvre, not either half.
     */
    private val DECOUPLED_FACINGS = listOf(
        MovementKeys.FORWARD_RIGHT to -45.0,
        MovementKeys.FORWARD_LEFT to 45.0,
    )

    /** Ticks a released body needs to come to rest from a sprint, with headroom. */
    private const val BRAKE_TAIL_FRAMES = 24

    /** A just-failed anchor retries almost immediately; its alternatives are the point. */
    private const val RETRY_PENALTY_TICKS = 0.05

    /** Heading change over the next two steps that makes a pre-aimed facing worth trying. */
    private const val DECOUPLE_TURN_DEGREES = 35.0

    /** Air-control choices during a launch: hold the line, or drift either way. */
    private val AIRBORNE_KEYS = listOf(
        MovementKeys.FORWARD, MovementKeys.FORWARD_LEFT, MovementKeys.FORWARD_RIGHT,
    )

    /** Stances ahead the descent bearing is aimed at, so the fan spreads around the line. */
    private const val BEARING_LOOKAHEAD = 2

    /** Coarse steps that also get a launch lattice; the rest are reached through anchors. */
    private const val LAUNCH_STEPS = 2

    /** Long enough for the brake schedule to see the goal from the finish horizon. */
    private const val FINISH_CHAIN_LENGTH = 24

    /** Feet Y sits exactly on the stance level; the epsilon absorbs float noise only. */
    private const val STANCE_LEVEL_EPSILON = 1e-6

    private const val SPEED_DOMINANCE_SLACK = 0.01
}
