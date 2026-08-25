/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing

import com.lambda.Lambda.LOG
import com.lambda.Lambda.mc
import com.lambda.config.automation.AutomationConfig
import com.lambda.config.blocks.PathingRenderConfig
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.managers.Manager
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.interaction.managers.rotating.RotationMode
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.debug.executionRejectionReport
import com.lambda.pathing.execution.ExecutionDeviation
import com.lambda.pathing.execution.ExecutionInputResult
import com.lambda.pathing.execution.ExecutionStateTolerance
import com.lambda.pathing.execution.ExecutionObservationResult
import com.lambda.pathing.execution.TrajectoryExecutionCursor
import com.lambda.pathing.movement.TerminalApproach
import com.lambda.pathing.trajectory.TrajectoryPlan
import com.lambda.pathing.world.InterestTier
import com.lambda.pathing.world.PathingWorld
import com.lambda.threading.runSafeAutomated
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.CommunicationUtils.warn
import com.lambda.util.player.MovementUtils.moveYaw
import com.lambda.util.player.MovementUtils.update
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import com.lambda.util.world.ChunkPacketLoadContext
import kotlin.math.abs
import kotlin.math.max
import net.minecraft.util.math.Vec3d

object PathingManager : Manager<PathingRequest>(0) {
    data class PublishedPath(
        val route: CoarseRoutePlan,
        val plan: TrajectoryPlan,
        val profile: PlayerPhysicsProfile,
        val parameters: TerminalApproach,
        val safeAnchorStance: Stance,
        val safeAnchorFrame: Int,
        val remainingGuideTicks: Double,
        val attempts: Int,
        val planMillis: Long,
        val finalGoal: Stance,
        val controlSegments: Int = 1,
        val spliceFrames: List<Int> = emptyList(),
        val launchMarginFrames: Int = 0,
        val partial: Boolean = false,
        val planningGeneration: Long = 0L,
        val publicationSequence: Int = 0,
    )

    sealed interface Status {
        data object Idle : Status

        data class Settling(val goal: String) : Status
        data class Planning(val goal: String) : Status
        data class Aligning(val leg: Int, val yawError: Double) : Status
        data class Executing(val frame: Int, val frames: Int, val leg: Int) : Status

        data class Complete(val frames: Int, val legs: Int) : Status
        data class Failed(val reason: String) : Status
    }

    @Volatile
    var status: Status = Status.Idle
        private set

    @Volatile
    var published: PublishedPath? = null
        private set

    @Volatile
    var maxDeviation: Double = 0.0
        private set

    @Volatile
    var adopted: Int = 0
        private set

    @Volatile
    var recoveries: Int = 0
        private set

    private val executedPaths = ArrayDeque<PublishedPath>()

    val executed: List<PublishedPath> get() = synchronized(executedPaths) { ArrayList(executedPaths) }

    @Volatile
    var rejectedImprovements: Int = 0
        private set

    @Volatile
    private var lastRenderConfig: PathingRenderConfig = AutomationConfig.DEFAULT.pathingRenderConfig

    /**
     * The render config of the walk's automation context, read live.
     *
     * Resolved through the active request so a setting changed mid-walk takes effect
     * immediately, exactly like every other automation config; the last context is kept
     * so trails and debug views drawn after completion keep their colors.
     */
    val renderConfig: PathingRenderConfig
        get() = activeRequest?.pathingRenderConfig ?: lastRenderConfig

    private val trail = ArrayDeque<Vec3d>()

    val liveTrail: List<Vec3d> get() = synchronized(trail) { ArrayList(trail) }

    private var activeRequest: PathingRequest? = null

    private var planningGeneration = 0L
    private var planningSession: PlanningSession? = null
    private var journey: PlanningJourney? = null

    private var leg = 0

    private var planningYaw: Double? = null

    private var pendingPath: PublishedPath? = null
    private var alignmentTicks = 0
    private var settleTicks = 0
    private var cursor: TrajectoryExecutionCursor? = null

    private var tickInput: MovementSimulationInput? = null
    private var awaitingObservation = false

    private var pendingImprovement: PublishedPath? = null

    /** Successor leg published by a parked session while the current tape still replays. */
    private var pendingNextLeg: PublishedPath? = null

    /**
     * Frames the previous leg replayed before a pipelined hand-off.
     *
     * Replay deviation accrues per frame and does not reset at a leg boundary the body
     * never re-observes from ground truth: the successor's root is a prediction carrying
     * the previous tape's accumulated drift, so its cursor starts with the tolerance the
     * previous cursor ended with instead of a fresh one it can never meet.
     */
    private var handoffBaseFrames = 0

    /**
     * The tape a pipeline was already attempted for. One attempt per tape: a pipelined
     * search that failed would otherwise be relaunched every tick against the same root
     * and fail the same way, in a hot loop, until the tape ran out.
     */
    private var pipelinedTape: Long? = null

    /**
     * The player's own flight permission, held while a tape is suppressing it.
     *
     * Null when nothing is suppressed, so the restore is idempotent and a cancel from any
     * path puts back exactly what the player had.
     */
    private var heldFlightPermission: Boolean? = null

    /** Live planner state for stuck-walk diagnosis; safe to read from any thread. */
    fun diagnostics(): String = buildString {
        append("status=").append(status)
        published?.let { p ->
            append(" tape=").append(p.plan.tape.frameCount)
            append(" routeGoal=").append(p.route.goal)
            append(" finalGoal=").append(p.finalGoal)
            append(" partial=").append(p.partial)
            append(" seq=").append(p.publicationSequence)
        }
        journey?.world?.let { w ->
            append(" revision=").append(w.revision)
            append(" pendingInterest=").append(w.pendingInterest)
        }
    }

    fun isFinished(request: PathingRequest): Boolean = when {
        queuedRequest === request -> false
        activeRequest === request -> status is Status.Complete || status is Status.Failed
        else -> true
    }

    /**
     * Takes creative flight away for the duration of a tape.
     *
     * Vanilla turns a second jump press within seven ticks into a flight toggle, and gates
     * the whole thing on `allowFlying`. A tape that presses jump twice in that window --
     * ordinary for consecutive hops -- therefore takes off in creative, which is not
     * something the simulator models or should have to: it is a creative-only interaction
     * with a key the planner uses for its own purposes.
     *
     * Suppressing the permission rather than pacing the jumps is deliberate. Fast repeated
     * jumps are genuinely the right move sometimes, and in survival -- where the tool
     * actually gets used -- `allowFlying` is already false and this does nothing at all.
     *
     * A body that is *already* flying is left alone: taking the permission away mid-flight
     * would strand it, and a plan does not run from the air anyway.
     *
     * @see net.minecraft.client.network.ClientPlayerEntity.tickMovement
     */
    private fun holdFlightPermission() {
        val abilities = mc.player?.abilities ?: return
        if (abilities.flying || !abilities.allowFlying) return
        heldFlightPermission = true
        abilities.allowFlying = false
    }

    private fun releaseFlightPermission() {
        val held = heldFlightPermission ?: return
        heldFlightPermission = null
        mc.player?.abilities?.allowFlying = held
    }

    /**
     * Stops the walk, optionally keeping the journey.
     *
     * The journey -- captured world, coarse D* field, optimistic frontier -- is scoped to
     * a *goal*, not to a request. Tearing it down on every stop made the next walk toward
     * the same goal re-expand tens of thousands of nodes for terrain nothing invalidated;
     * a kept journey is re-validated (goal, move options, physics profile) before reuse
     * and rebuilt only when it genuinely no longer applies. The capture is owned by the
     * journey, so it is cancelled with it and only with it.
     */
    private fun releaseWalk(keepJourney: Boolean = false) {
        releaseFlightPermission()
        val planning = planningSession
        planningSession = null
        planning?.cancel()
        if (!keepJourney) {
            journey?.cancel()
            journey = null
        }
        activeRequest = null
        cursor = null
        tickInput = null
        awaitingObservation = false
        planningYaw = null
        pendingPath = null
        pendingImprovement = null
        pendingNextLeg = null
        handoffBaseFrames = 0
        pipelinedTape = null
        alignmentTicks = 0
        settleTicks = 0
        leg = 0
    }

    fun cancel() {
        releaseWalk(keepJourney = true)
        if (status is Status.Settling || status is Status.Planning ||
            status is Status.Aligning || status is Status.Executing
        ) {
            status = Status.Idle
        }
    }

    fun clear() {
        releaseWalk()
        status = Status.Idle
        published = null
        maxDeviation = 0.0
        adopted = 0
        recoveries = 0
        rejectedImprovements = 0
        synchronized(executedPaths) { executedPaths.clear() }
        synchronized(trail) { trail.clear() }
        PlanningDebugChannel.reset()
    }

    override fun AutomatedSafeContext.handleRequest(request: PathingRequest) {
        if (!request.fresh) return

        // A fresh walk toward the goal the journey already serves keeps the coarse
        // state; `capturePlan` still re-validates options and physics before trusting
        // it. World-mutation revisions stay monotonic while anything derived from them
        // survives.
        val sameGoal = journey?.goal == TrajectoryPlanner.resolveGoalStance(player, request.goal)
        releaseWalk(keepJourney = sameGoal)
        status = Status.Idle
        published = null
        maxDeviation = 0.0
        adopted = 0
        recoveries = 0
        rejectedImprovements = 0
        synchronized(executedPaths) { executedPaths.clear() }
        synchronized(trail) { trail.clear() }
        PlanningDebugChannel.reset()

        activeRequest = request
        lastRenderConfig = request.pathingRenderConfig

        unsteerable(request)?.let { return fail(it, request.goal) }
        planTrajectory(request)
    }

    private fun SafeContext.planTrajectory(request: PathingRequest) {
        if (player.velocity.horizontalLength() > SETTLED_SPEED) {
            settleTicks = 0
            tickInput = ALIGNMENT_INPUT
            status = Status.Settling("(${request.goal.x}, ${request.goal.y}, ${request.goal.z})")
            return
        }
        capturePlan(request)
    }

    private fun SafeContext.settle(request: PathingRequest) {
        tickInput = ALIGNMENT_INPUT
        if (player.velocity.horizontalLength() <= SETTLED_SPEED && player.isOnGround) {
            return capturePlan(request)
        }
        if (++settleTicks > MAX_SETTLE_TICKS) {
            fail("could not settle to a stable start (%.3f b/t after %d ticks)".format(
                player.velocity.horizontalLength(), settleTicks,
            ))
        }
    }

    private fun SafeContext.capturePlan(request: PathingRequest) {
        tickInput = ALIGNMENT_INPUT

        val previousSession = planningSession
        planningSession = null
        previousSession?.cancel()
        // Any fresh planning re-roots at the live body; a leg parked for the old tape's
        // terminal stance no longer describes anything the walk will reach, and the
        // deviation budget restarts from observed ground truth.
        pendingNextLeg = null
        handoffBaseFrames = 0
        pipelinedTape = null
        val session = PlanningSession(++planningGeneration, request)
        planningSession = session

        planningYaw = player.moveYaw.toDouble()
        status = Status.Planning("(${request.goal.x}, ${request.goal.y}, ${request.goal.z})")
        PlanningDebugChannel.begin(
            request.pathingRenderConfig.enabled &&
                (request.pathingRenderConfig.renderPlanning || request.pathingRenderConfig.renderGraph),
            PlanningDebugChannel.GraphViewLimits(
                radius = request.pathingRenderConfig.graphRadius,
                cells = request.pathingRenderConfig.graphCellBudget,
                edges = request.pathingRenderConfig.graphEdgeBudget,
            ),
        )

        val preparation = when (val prepared = TrajectoryPlanner.prepare(
            player = player,
            goal = request.goal,
            config = request.pathingConfig,
            turnSpeed = request.rotationConfig.turnSpeed,
            cancellation = session.cancellation,
        )) {
            is PlanningPreparationResult.Ready -> prepared.preparation
            is PlanningPreparationResult.Failed -> return fail(prepared.failure.message)
            PlanningPreparationResult.Cancelled -> return fail("planning was cancelled")
        }
        val currentJourney = journey?.takeIf {
            it.goal == preparation.finalGoal && it.moveOptions == preparation.moveOptions &&
                it.profile.isCompatibleWith(preparation.profile) &&
                it.coarseState.horizonChunks == preparation.planningHorizonChunks
        } ?: run {
            journey?.cancel()
            val journeyCancellation = PlanningCancellation()
            val pathingWorld = PathingWorld(preparation.bounds, player.entityWorld, player)
            primeJourneyInterest(pathingWorld, preparation)
            PlanningJourney(
                goal = preparation.finalGoal,
                moveOptions = preparation.moveOptions,
                profile = preparation.profile,
                cancellation = journeyCancellation,
                world = pathingWorld,
                coarseState = TrajectoryPlanner.coarseState(preparation, pathingWorld.snapshot),
            ).also { journey = it }
        }
        launchPlanning(request, session, preparation, currentJourney)
        advanceSnapshotCapture(request)
    }

    private fun SafeContext.advanceSnapshotCapture(request: PathingRequest) {
        if (status is Status.Planning) holdPlanningYaw(request)
        if (activeRequest !== request) return
        val activeJourney = journey ?: return
        // The body's neighbourhood is standing interest: knowledge follows the walk.
        val pos = player.blockPos
        activeJourney.world.interestBlocks(
            pos.x - BODY_INTEREST_BLOCKS, pos.y - BODY_INTEREST_Y_BLOCKS, pos.z - BODY_INTEREST_BLOCKS,
            pos.x + BODY_INTEREST_BLOCKS, pos.y + BODY_INTEREST_Y_BLOCKS, pos.z + BODY_INTEREST_BLOCKS,
            InterestTier.BODY,
        )
        val configured = request.pathingConfig.snapshotCaptureBudgetMillis
        // The configured budget protects frame time while a tape is replaying; while
        // the body stands waiting for its plan, start latency is the constraint.
        val budget = if (cursor == null) maxOf(configured, IDLE_CAPTURE_BUDGET_MILLIS) else configured
        activeJourney.world.advance(budget)
    }

    /** Capture keeps ahead of the walk: corridor interest along the running route. */
    private fun primeRouteInterest(path: PublishedPath) {
        val world = journey?.world ?: return
        path.route.nodes.forEach { node ->
            world.interestBlocks(
                node.x - ROUTE_INTEREST_BLOCKS, node.y - ROUTE_INTEREST_Y_BLOCKS, node.z - ROUTE_INTEREST_BLOCKS,
                node.x + ROUTE_INTEREST_BLOCKS, node.y + ROUTE_INTEREST_Y_BLOCKS, node.z + ROUTE_INTEREST_BLOCKS,
                InterestTier.CORRIDOR,
            )
        }
    }

    /** Knowledge the first route needs: the goal area and a corridor toward it. */
    private fun primeJourneyInterest(world: PathingWorld, preparation: TrajectoryPlanningPreparation) {
        val goal = preparation.finalGoal
        world.interestBlocks(
            goal.x - GOAL_INTEREST_BLOCKS, goal.y - GOAL_INTEREST_BLOCKS, goal.z - GOAL_INTEREST_BLOCKS,
            goal.x + GOAL_INTEREST_BLOCKS, goal.y + GOAL_INTEREST_BLOCKS, goal.z + GOAL_INTEREST_BLOCKS,
            InterestTier.CORRIDOR,
        )
        val start = preparation.start
        val dx = goal.x - start.x
        val dy = goal.y - start.y
        val dz = goal.z - start.z
        val length = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz), 1)
        var step = CORRIDOR_SAMPLE_BLOCKS
        while (step < length) {
            val x = start.x + dx * step / length
            val y = start.y + dy * step / length
            val z = start.z + dz * step / length
            world.interestBlocks(
                x - CORRIDOR_INTEREST_BLOCKS, y - CORRIDOR_INTEREST_Y_BLOCKS, z - CORRIDOR_INTEREST_BLOCKS,
                x + CORRIDOR_INTEREST_BLOCKS, y + CORRIDOR_INTEREST_Y_BLOCKS, z + CORRIDOR_INTEREST_BLOCKS,
                InterestTier.CORRIDOR,
            )
            step += CORRIDOR_SAMPLE_BLOCKS
        }
    }

    private fun SafeContext.launchPlanning(
        request: PathingRequest,
        session: PlanningSession,
        preparation: TrajectoryPlanningPreparation,
        journey: PlanningJourney,
    ) {
        val planning = try {
            TrajectoryPlanner.planAsync(
                preparation = preparation,
                world = journey.world,
                cursorFrame = session::executionFrame,
                onImprovement = { improvement ->
                    mc.execute {
                        if (planningSession === session && activeRequest === request) {
                            if (session.parked) parkNextLeg(improvement)
                            else if (cursor != null) adopt(improvement)
                        }
                    }
                },
                onSafePrefix = { prefix ->

                    mc.execute {
                        if (planningSession === session && activeRequest === request) {
                            if (session.parked) parkNextLeg(prefix)
                            else if (cursor == null && pendingPath == null) begin(prefix)
                        }
                    }
                },
                cancellation = session.cancellation,
                planningGeneration = session.generation,
                snapshotRevision = journey.world.revision,
                coarseState = journey.coarseState,
            )
        } catch (failure: Exception) {
            fail("could not start trajectory planning: ${failure.message}")
            return
        }
        session.attach(planning)

        planning.whenCompleteAsync({ result, failure ->

            if (planningSession !== session || activeRequest !== request) return@whenCompleteAsync
            if (failure != null) {
                LOG.error("Pathing worker failed while expanding the trajectory", failure)
                fail("trajectory expansion crashed: ${failure.rootMessage()}")
                return@whenCompleteAsync
            }
            val completed = checkNotNull(result)
            when (completed) {
                is PathPlanResult.Planned ->
                    if (session.parked) parkNextLeg(completed.path)
                    else if (cursor != null) adopt(completed.path) else begin(completed.path)
                is PathPlanResult.Failed ->
                    // A parked failure is not the walk's failure: the running tape is
                    // still valid, and the tape end falls back to an ordinary replan.
                    if (session.parked) LOG.info(
                        "Pipelined next leg found nothing ({}); the tape end will replan", completed.failure.message,
                    )
                    else if (session.pipelined && activeRequest === request && cursor == null) {
                        // The hand-off was already waiting on this session; a root
                        // guessed from the tape terminal owes the walk a real attempt
                        // from the live body before anything is declared unwalkable.
                        LOG.info(
                            "Pipelined leg failed after hand-off ({}); replanning from the body",
                            completed.failure.message,
                        )
                        planningSession = null
                        planTrajectory(request)
                    }
                    else fail(completed.failure.message)
                PathPlanResult.Cancelled -> if (!session.parked) fail("planning was cancelled")
            }
            if (planningSession === session) {
                planningSession = null
            }
            // The successor leg is NOT pipelined from here: a final improvement may
            // still sit in pendingImprovement, and a root taken now would go stale the
            // tick it is adopted. The tick handler starts the pipeline once the
            // improvement stream has fully drained and the tape can no longer grow.
        }, mc)
    }


    private class PlanningJourney(
        val goal: Stance,
        val moveOptions: com.lambda.pathing.coarse.SimpleMoveOptions,
        val profile: PlayerPhysicsProfile,
        val cancellation: PlanningCancellation,
        val world: PathingWorld,
        val coarseState: CoarsePlanningState,
    ) {
        fun cancel() {
            cancellation.cancel()
            world.close()
        }
    }

    private fun SafeContext.begin(path: PublishedPath) {
        planningSession?.let { session ->
            if (path.planningGeneration != session.generation) {
                return keepRunning(path, "publication belongs to stale planning generation ${path.planningGeneration}")
            }
        }
        executionEnvironmentDeviation(path, nextFrame = 0)?.let { deviation ->
            return fail("certified plan became stale before execution: $deviation")
        }
        if (!path.partial && path.route.goal != path.finalGoal) {
            return fail("planner attempted to publish a partial route ending at ${path.route.goal}")
        }

        val drift = player.pos.distanceTo(path.plan.initialState.position)
        if (drift > START_DRIFT_TOLERANCE) {
            if (planningSession?.generation != path.planningGeneration) {
                return keepRunning(path, "publication was certified from a stance the walk has left")
            }
            return fail("moved %.2f blocks while planning".format(drift))
        }

        val yawDrift = abs(Rotation.wrap(player.moveYaw - path.plan.initialState.rotation.yaw))
        if (yawDrift > START_YAW_TOLERANCE) {
            published = path
            pendingPath = path
            alignmentTicks = 0
            tickInput = ALIGNMENT_INPUT
            status = Status.Aligning(leg + 1, yawDrift)
            info(
                "Certified trajectory; aligning movement yaw by %.1f° before replay.".format(yawDrift),
                PATHING_SOURCE,
            )
            return
        }

        install(path)
    }

    private fun SafeContext.adopt(path: PublishedPath) {
        val running = published
        val active = cursor
        if (running == null || active == null) return begin(path)
        if (path.planningGeneration != running.planningGeneration) {
            return keepRunning(path, "publication belongs to a different planning generation")
        }
        if (path.publicationSequence <= running.publicationSequence) {
            return keepRunning(path, "publication is older than the running tape")
        }
        if (awaitingObservation) {
            pendingImprovement = path
            return
        }

        val frame = active.nextFrame
        if (frame > path.plan.tape.frameCount) return keepRunning(path, "improvement is shorter than the walk so far")

        if (!running.partial && !path.partial && path.plan.tape.frameCount >= running.plan.tape.frameCount) {
            return keepRunning(path, "improvement is not shorter than the running tape")
        }
        if (running.partial && path.partial && path.safeAnchorFrame <= running.safeAnchorFrame) {
            return keepRunning(path, "partial publication commits no new anchor")
        }
        if (path.partial && path.safeAnchorFrame <= frame) {
            return keepRunning(path, "partial publication has no unexecuted progress anchor")
        }
        val diverges = (0 until frame).any { running.plan.tape[it] != path.plan.tape[it] }
        if (diverges) return keepRunning(path, "improvement diverges behind the cursor")

        executionEnvironmentDeviation(path, nextFrame = frame)?.let { deviation ->
            return keepRunning(path, "publication environment changed: $deviation")
        }
        published = path
        recordExecuted(path)
        primeRouteInterest(path)
        cursor = TrajectoryExecutionCursor(
            path.plan, PlayerPhysicsProfile.capture(player),
            tolerance = ExecutionStateTolerance().let {
                it.copy(position = it.position + it.positionPerFrame * handoffBaseFrames)
            },
        ).apply { resumeAt(frame) }
        planningSession?.updateExecutionFrame(frame)
        status = Status.Executing(frame, path.plan.tape.frameCount, leg)
        adopted++
    }

    private fun recordExecuted(path: PublishedPath) {
        synchronized(executedPaths) {
            if (executedPaths.size == MAX_RETAINED_PUBLICATIONS) executedPaths.removeFirst()
            executedPaths.addLast(path)
        }
    }

    /** Keeps the newest successor-leg publication for the hand-off at the tape end. */
    private fun parkNextLeg(path: PublishedPath) {
        val parked = pendingNextLeg
        if (parked == null || path.planningGeneration > parked.planningGeneration ||
            (path.planningGeneration == parked.planningGeneration &&
                path.publicationSequence > parked.publicationSequence)
        ) {
            pendingNextLeg = path
        }
    }

    /**
     * Starts searching the leg after the running tape while the body is still replaying.
     *
     * A partial tape ends in a certified stable stop whose state is known exactly, so
     * the successor search is rooted there instead of waiting for the body to arrive,
     * settle, and recapture. Its publications are parked -- they extend a tape that is
     * not running yet -- and the moment the running tape completes they install in its
     * place, turning the between-legs pause from settle+capture+plan into one install.
     */
    private fun SafeContext.pipelineNextLeg(request: PathingRequest) {
        val running = published ?: return
        if (!running.partial) return
        val currentJourney = journey ?: return
        pipelinedTape = running.plan.id.value
        // A certified stop still carries up to 0.012 b/t of residual speed, and the live
        // body both sheds it (vanilla clamps sub-0.003 velocities to zero) and slides a
        // few milliblocks doing so. The worker settles this root to that fixed point
        // before searching (settleInitial below); the install then waits for the body
        // to match it. Settling must not happen here: a snapshot miss blocks on the
        // capture, and the capture advances on this very thread.
        val terminal = running.plan.frames.last().state

        val session = PlanningSession(
            ++planningGeneration, request, pipelined = true,
        )
        session.parked = true
        planningSession = session
        val preparation = when (val prepared = TrajectoryPlanner.prepare(
            player = player,
            goal = request.goal,
            config = request.pathingConfig,
            turnSpeed = request.rotationConfig.turnSpeed,
            cancellation = session.cancellation,
            initialOverride = terminal,
            settleInitial = true,
        )) {
            is PlanningPreparationResult.Ready -> prepared.preparation
            else -> {
                planningSession = null
                return
            }
        }
        val compatible = currentJourney.goal == preparation.finalGoal &&
            currentJourney.moveOptions == preparation.moveOptions &&
            currentJourney.profile.isCompatibleWith(preparation.profile) &&
            currentJourney.coarseState.horizonChunks == preparation.planningHorizonChunks
        if (!compatible) {
            planningSession = null
            return
        }
        LOG.info(
            "Searching the next leg from the running tape's terminal stance {}", preparation.start,
        )
        launchPlanning(request, session, preparation, currentJourney)
    }

    private fun keepRunning(rejected: PublishedPath, reason: String) {
        rejectedImprovements++
        LOG.info("Pathing kept the running tape: $reason (${rejected.plan.tape.frameCount} frames offered)")
    }

    private fun SafeContext.install(path: PublishedPath) {
        executionEnvironmentDeviation(path, nextFrame = 0)?.let { deviation ->
            return fail("certified plan became stale before execution: $deviation")
        }
        primeRouteInterest(path)
        planningYaw = null
        pendingPath = null
        alignmentTicks = 0
        published = path
        recordExecuted(path)
        cursor = TrajectoryExecutionCursor(
            path.plan, PlayerPhysicsProfile.capture(player),
            tolerance = ExecutionStateTolerance().let {
                it.copy(position = it.position + it.positionPerFrame * handoffBaseFrames)
            },
        )
        planningSession?.updateExecutionFrame(0)
        awaitingObservation = false
        leg++
        status = Status.Executing(0, path.plan.tape.frameCount, leg)
        info(
            "Certified continuous trajectory: ${path.route.nodes.first()} -> ${path.route.goal}, " +
                "${path.plan.tape.frameCount} frames, ${path.attempts} attempts, " +
                "${path.planMillis} ms, ${path.controlSegments} continuous segment(s)" +
                path.spliceFrames.takeIf { it.isNotEmpty() }
                    ?.let { ", predicted splice frames ${it.joinToString()}" }.orEmpty(),
            PATHING_SOURCE,
        )
    }

    private fun unsteerable(request: PathingRequest): String? {
        val config = request.rotationConfig
        if (config.rotationMode == RotationMode.Silent) {
            return "rotation mode Silent cannot steer movement: it nulls movementYaw, " +
                "so the body would follow the camera instead of the plan. Use Sync or Lock."
        }
        return null
    }

    private fun SafeContext.fail(reason: String, goal: Stance? = activeRequest?.goal ?: published?.finalGoal) {
        val position = player.blockPos?.let { "(${it.x}, ${it.y}, ${it.z})" } ?: "unknown position"
        val destination = goal?.let { " toward $it" } ?: ""
        val walked = leg
        releaseWalk(keepJourney = true)
        status = Status.Failed(reason)
        warn("Stopped ${if (walked == 0) "before" else "during"} continuous replay at $position$destination: $reason", PATHING_SOURCE)
    }

    /**
     * Stops replay before another certified input and starts a fresh generation from
     * rest. Neutral input is applied while settling; no tape certified against the old
     * world/profile can publish into the replacement session.
     */
    private fun SafeContext.recover(reason: String, reuseCoarseState: Boolean = true) {
        val request = activeRequest ?: return fail(reason)
        val planning = planningSession
        planningSession = null
        planning?.cancel()
        if (!reuseCoarseState) {
            journey?.cancel()
            journey = null
        }
        cursor = null
        awaitingObservation = false
        pendingPath = null
        pendingImprovement = null
        pendingNextLeg = null
        handoffBaseFrames = 0
        pipelinedTape = null
        planningYaw = null
        alignmentTicks = 0
        settleTicks = 0
        tickInput = ALIGNMENT_INPUT
        recoveries++
        status = Status.Settling("(${request.goal.x}, ${request.goal.y}, ${request.goal.z})")
        warn("Replanning after the certified environment changed: $reason", PATHING_SOURCE)
    }

    init {
        listenUnsafe<ConnectionEvent.Disconnect> { clear() }
        listenUnsafe<WorldEvent.BlockUpdate.Client> { event ->
            if (event.world.isClient && event.oldState != event.newState &&
                !ChunkPacketLoadContext.isActive()
            ) {
                journey?.world?.onBlockChanged(event.pos)
            }
        }
        listenUnsafe<WorldEvent.ChunkEvent.Load> { event ->
            journey?.world?.onChunkEvent(event.chunk.pos.x, event.chunk.pos.z)
        }
        listen<TickEvent.Pre> {
            val request = activeRequest ?: return@listen
            advanceSnapshotCapture(request)
            if (activeRequest !== request) return@listen
            if (status is Status.Settling) return@listen settle(request)
            if (status is Status.Planning) return@listen
            if (status is Status.Aligning) return@listen align(request)

            val active = cursor ?: return@listen
            val path = published ?: return@listen

            planningSession?.updateExecutionFrame(active.nextFrame)

            val frame = active.nextFrame

            if (awaitingObservation) {
                val observed = observe(path.plan, frame + 1)
                executionEnvironmentDeviation(path, nextFrame = frame)?.let { deviation ->
                    return@listen reject(frame, deviation, observed, afterInput = true)
                }
                val result = active.observeAfterTick(observed)
                if (result is ExecutionObservationResult.Rejected) {
                    return@listen reject(result.frame, result.deviation, observed, afterInput = true)
                }
                awaitingObservation = false

                if (frame < path.plan.frames.size) {
                    val predicted = path.plan.frames[frame].state.position
                    synchronized(trail) {
                        if (trail.size == MAX_RETAINED_TRAIL_POINTS) trail.removeFirst()
                        trail.addLast(player.pos)
                    }
                    maxDeviation = max(maxDeviation, player.pos.distanceTo(predicted))
                }

                if (result === ExecutionObservationResult.Complete) {
                    return@listen finishTrajectory(path)
                }
            }

            pendingImprovement?.let { improvement ->
                pendingImprovement = null
                adopt(improvement)
            }

            // With the session finished and every improvement drained, the running tape
            // can no longer grow -- its terminal is final, and the successor leg can be
            // searched from it while the body is still replaying.
            if (planningSession == null && pendingNextLeg == null && pendingImprovement == null &&
                !awaitingObservation && cursor != null && published?.partial == true &&
                published?.plan?.id?.value != pipelinedTape
            ) {
                pipelineNextLeg(request)
            }

            val current = published ?: return@listen
            val running = cursor ?: return@listen

            val observed = observe(current.plan, running.nextFrame)
            executionEnvironmentDeviation(current, nextFrame = running.nextFrame)?.let { deviation ->
                return@listen reject(running.nextFrame, deviation, observed, afterInput = false)
            }
            when (val next = running.nextInput(observed)) {
                is ExecutionInputResult.Apply -> {
                    tickInput = next.input
                    awaitingObservation = true
                    status = Status.Executing(next.frame, current.plan.tape.frameCount, leg)

                    next.input.rotation?.let { rotation ->
                        request.runSafeAutomated { rotationRequest { yaw(rotation.yaw) }.submit() }
                    }
                }

                ExecutionInputResult.Complete -> finishTrajectory(current)

                is ExecutionInputResult.Rejected -> reject(
                    next.frame, next.deviation, observed, afterInput = false,
                )
            }
        }

        listen<MovementEvent.InputUpdate>({ Int.MAX_VALUE }) { event ->
            val input = tickInput ?: return@listen releaseFlightPermission()
            holdFlightPermission()

            if (awaitingObservation) {
                val path = published ?: return@listen fail("lost the certified plan before input application")
                val active = cursor ?: return@listen fail("lost the execution cursor before input application")
                executionEnvironmentDeviation(path, nextFrame = active.nextFrame)?.let { deviation ->
                    val observed = observe(path.plan, active.nextFrame)
                    return@listen reject(active.nextFrame, deviation, observed, afterInput = false)
                }
            }
            event.input.update(
                forward = input.forward,
                strafe = input.strafe,
                jump = input.jump,
                sneak = input.sneak,
                sprint = input.sprint,
            )
        }
    }

    private fun holdPlanningYaw(request: PathingRequest) {
        val yaw = planningYaw ?: return
        request.runSafeAutomated { rotationRequest { yaw(yaw) }.submit() }
    }

    private fun SafeContext.align(request: PathingRequest) {
        val path = pendingPath ?: return fail("lost the certified plan while aligning")
        val positionDrift = player.pos.distanceTo(path.plan.initialState.position)
        if (positionDrift > START_DRIFT_TOLERANCE) {
            return fail("moved %.2f blocks while aligning to the plan".format(positionDrift))
        }

        val targetYaw = path.plan.initialState.rotation.yaw
        request.runSafeAutomated { rotationRequest { yaw(targetYaw) }.submit() }
        val yawDrift = abs(Rotation.wrap(player.moveYaw - targetYaw))
        // The body must also have shed its residual stop velocity: frame zero is
        // compared per-axis at replay tolerance, and a pipelined leg is rooted at the
        // settled terminal, not at the certified stop's last few milliblocks of drift.
        val settled = player.velocity.horizontalLength() <= SETTLED_SPEED && player.isOnGround
        if (yawDrift <= START_YAW_TOLERANCE && settled) {
            install(path)
            return
        }

        status = Status.Aligning(leg + 1, yawDrift)
        if (++alignmentTicks > MAX_ALIGNMENT_TICKS) {
            fail("could not align movement yaw to the plan (%.1f degrees remain)".format(yawDrift))
        }
    }

    private fun SafeContext.finishTrajectory(path: PublishedPath) {
        cursor = null
        planningSession?.updateExecutionFrame(null)
        tickInput = null
        awaitingObservation = false

        if (path.partial) {
            val request = activeRequest
            if (request != null) {
                pendingImprovement = null
                info(
                    "Safe partial tape complete (${path.plan.tape.frameCount} frames); " +
                        "continuing toward ${path.finalGoal}.",
                    PATHING_SOURCE,
                )
                val next = pendingNextLeg
                pendingNextLeg = null
                val terminal = path.plan.frames.last().state.position
                if (next != null &&
                    next.plan.initialState.position.distanceTo(terminal) <= START_DRIFT_TOLERANCE
                ) {
                    // The successor session, if still refining, resumes ordinary
                    // publication against the tape installed here. Installation goes
                    // through the align path unconditionally: the parked root has its
                    // residual velocity zeroed, so the body must settle (and face the
                    // plan) before frame zero is compared against it.
                    planningSession?.parked = false
                    published = next
                    pendingPath = next
                    handoffBaseFrames += path.plan.tape.frameCount
                    alignmentTicks = 0
                    tickInput = ALIGNMENT_INPUT
                    status = Status.Aligning(leg + 1, 0.0)
                    return
                }
                if (next != null) {
                    // The tape outgrew the pipelined root (a late improvement moved the
                    // terminal); the parked leg and its session describe a stance the
                    // body is not at. Fall through to an ordinary replan.
                    LOG.info(
                        "Discarding a pipelined leg rooted {} blocks from the tape terminal",
                        "%.2f".format(next.plan.initialState.position.distanceTo(terminal)),
                    )
                }
                val successor = planningSession
                if (successor != null && successor.parked) {
                    // The pipelined search is still running; its next publication
                    // begins the leg the ordinary way now that the cursor is gone.
                    successor.parked = false
                    status = Status.Planning("(${path.finalGoal.x}, ${path.finalGoal.y}, ${path.finalGoal.z})")
                    return
                }
                val finished = planningSession
                planningSession = null
                finished?.cancel()
                planTrajectory(request)
                return
            }
        }
        val planning = planningSession
        planningSession = null
        planning?.cancel()
        // The journey outlives the arrival on purpose: walking back, or on toward the
        // same goal, is common, and the graph it would rebuild is exactly the terrain
        // just walked. A request for a different goal retires it.
        status = Status.Complete(path.plan.tape.frameCount, leg)
        activeRequest = null
        releaseFlightPermission()
        info(
            "Reached ${path.finalGoal} after $leg certified trajectory leg(s); " +
                "max replay deviation %.2e".format(maxDeviation) +
                ", adopted $adopted improvement(s)" +
                (if (recoveries > 0) ", recovered $recoveries time(s)" else "") +
                (if (rejectedImprovements > 0) ", rejected $rejectedImprovements" else "") +
                ", ${path.publicationSequence.coerceAtLeast(1)} publication(s) in the final leg",
            PATHING_SOURCE,
        )
    }

    private fun SafeContext.reject(
        frame: Int,
        deviation: ExecutionDeviation,
        observed: MovementSimulationState,
        afterInput: Boolean,
    ) {
        val report = executionRejectionReport(published, frame, deviation, observed, afterInput)
        when (deviation) {
            is ExecutionDeviation.WorldChanged -> recover(report, reuseCoarseState = true)
            is ExecutionDeviation.PhysicsProfile -> recover(report, reuseCoarseState = false)
            // Replay-state divergence (a sprint flag, a stray milliblock) means the
            // simulator was wrong about this frame, not that the walk is impossible.
            // Reality wins: replan from where the body actually is, and only give up
            // when divergence keeps happening -- a hard fail on first divergence turned
            // one simulator fidelity edge into a dead walk.
            else ->
                if (recoveries < MAX_STATE_RECOVERIES) recover(report, reuseCoarseState = true)
                else fail(report)
        }
    }

    private fun SafeContext.executionEnvironmentDeviation(
        path: PublishedPath,
        nextFrame: Int,
    ): ExecutionDeviation? {
        val liveProfile = PlayerPhysicsProfile.capture(player)
        if (!liveProfile.isCompatibleWith(path.profile)) {
            return ExecutionDeviation.PhysicsProfile(path.profile, liveProfile)
        }
        val mutation = journey?.world?.changedSince(
            snapshotRevision = path.plan.snapshotRevision,
            dependedSections = path.plan.dependencySectionsFrom(nextFrame),
            dependedChunks = path.plan.dependencyChunksFrom(nextFrame),
        ) ?: return null
        return ExecutionDeviation.WorldChanged(path.plan.snapshotRevision, mutation)
    }

    private fun SafeContext.observe(plan: TrajectoryPlan, frame: Int) =
        MovementSimulationState.from(player, isJumping = frame > 0 && frame <= plan.tape.frameCount && plan.tape[frame - 1].jump)

    private const val START_DRIFT_TOLERANCE = 0.35
    private const val START_YAW_TOLERANCE = 1.0

    /**
     * Covers both the yaw turn and the residual-velocity settle a pipelined hand-off
     * waits for; on slime the stop's last 3e-4 b/t decays at only ~0.73 per tick, which
     * alone costs ~18 ticks to reach [SETTLED_SPEED].
     */
    private const val MAX_ALIGNMENT_TICKS = 40

    private const val SETTLED_SPEED = 1e-6

    private const val MAX_SETTLE_TICKS = 40

    /**
     * Enough for a whole 3x3x3 prefetch burst in one tick; the configured time budget
     * in [PathingConfig.snapshotCaptureBudgetMillis] is what actually bounds the work.
     */
    private const val SNAPSHOT_CAPTURE_CELLS_PER_TICK = 131_072

    /** Capture budget while nothing is replaying: the body is standing anyway. */
    private const val IDLE_CAPTURE_BUDGET_MILLIS = 15.0

    /** Standing interest half-extent around the body, in blocks. */
    private const val BODY_INTEREST_BLOCKS = 24
    private const val BODY_INTEREST_Y_BLOCKS = 16

    /** Half-extent of the goal-neighbourhood interest declared at journey start. */
    private const val GOAL_INTEREST_BLOCKS = 16

    private const val CORRIDOR_SAMPLE_BLOCKS = 24
    private const val CORRIDOR_INTEREST_BLOCKS = 8
    private const val CORRIDOR_INTEREST_Y_BLOCKS = 12

    private const val ROUTE_INTEREST_BLOCKS = 8
    private const val ROUTE_INTEREST_Y_BLOCKS = 8

    /** Replans allowed on replay-state divergence before the walk is declared dead. */
    private const val MAX_STATE_RECOVERIES = 8

    private const val MAX_RETAINED_PUBLICATIONS = 256
    private const val MAX_RETAINED_TRAIL_POINTS = 4_096

    private const val PATHING_SOURCE = "Pathing"

    private val ALIGNMENT_INPUT = MovementSimulationInput()

    private fun Throwable.rootMessage(): String {
        var root = this
        while (root.cause != null && root.cause !== root) root = root.cause!!
        return root.message ?: root::class.simpleName ?: "unknown error"
    }
}
