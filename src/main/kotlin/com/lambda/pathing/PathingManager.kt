package com.lambda.pathing

import com.lambda.Lambda.LOG
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
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.debug.executionRejectionReport
import com.lambda.pathing.execution.ExecutionDeviation
import com.lambda.pathing.execution.ExecutionInputResult
import com.lambda.pathing.execution.ExecutionStateTolerance
import com.lambda.pathing.execution.ExecutionObservationResult
import com.lambda.pathing.execution.TrajectoryExecutionCursor
import com.lambda.pathing.trajectory.PublishedPath
import com.lambda.pathing.trajectory.TrajectoryPlan
import com.lambda.pathing.world.PathingWorld
import com.lambda.threading.runSafeAutomated
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.CommunicationUtils.warn
import com.lambda.util.player.MovementUtils.moveYaw
import com.lambda.util.player.MovementUtils.update
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.world.ChunkPacketLoadContext
import kotlin.math.abs
import kotlin.math.max
import net.minecraft.util.math.Vec3d

object PathingManager : Manager<PathingRequest>(0) {
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

    val renderConfig: PathingRenderConfig
        get() = activeWalk?.request?.pathingRenderConfig ?: lastRenderConfig

    private val trail = ArrayDeque<Vec3d>()

    val liveTrail: List<Vec3d> get() = synchronized(trail) { ArrayList(trail) }

    private var activeWalk: Walk? = null

    private var planningGeneration = 0L
    private var journey: PlanningJourney? = null

    private val flight = FlightPermissionHold()

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
        activeWalk?.request === request -> status is Status.Complete || status is Status.Failed
        else -> true
    }

    private fun releaseWalk(keepJourney: Boolean = false) {
        flight.release()
        activeWalk?.cancelPlanning()
        activeWalk = null
        if (!keepJourney) {
            journey?.cancel()
            journey = null
        }
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

        val walk = Walk(request)
        activeWalk = walk
        lastRenderConfig = request.pathingRenderConfig

        unsteerable(request)?.let { return fail(it, request.goal) }
        planTrajectory(walk)
    }

    private fun SafeContext.planTrajectory(walk: Walk) {
        if (player.velocity.horizontalLength() > SETTLED_SPEED) {
            walk.settleTicks = 0
            walk.tickInput = ALIGNMENT_INPUT
            status = Status.Settling(walk.request.goal.let { "(${it.x}, ${it.y}, ${it.z})" })
            return
        }
        capturePlan(walk)
    }

    private fun SafeContext.settle(walk: Walk) {
        walk.tickInput = ALIGNMENT_INPUT
        if (player.velocity.horizontalLength() <= SETTLED_SPEED && player.isOnGround) {
            return capturePlan(walk)
        }
        if (++walk.settleTicks > MAX_SETTLE_TICKS) {
            fail("could not settle to a stable start (%.3f b/t after %d ticks)".format(
                player.velocity.horizontalLength(), walk.settleTicks,
            ))
        }
    }

    private fun SafeContext.capturePlan(walk: Walk) {
        walk.tickInput = ALIGNMENT_INPUT

        walk.cancelPlanning()
        walk.pendingNextLeg = null
        walk.handoffBaseFrames = 0
        walk.pipelinedTape = null
        val request = walk.request
        val session = PlanningSession(++planningGeneration, request)
        walk.planningSession = session

        walk.planningYaw = player.moveYaw.toDouble()
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
        val currentJourney = journey?.takeIf { it.compatibleWith(preparation) } ?: run {
            journey?.cancel()
            val pathingWorld = PathingWorld(preparation.bounds, player.entityWorld, player)
            InterestPrimer.primeJourney(pathingWorld, preparation.start, preparation.finalGoal)
            PlanningJourney(
                goal = preparation.finalGoal,
                moveOptions = preparation.moveOptions,
                profile = preparation.profile,
                cancellation = PlanningCancellation(),
                world = pathingWorld,
                coarseState = TrajectoryPlanner.coarseState(preparation, pathingWorld.snapshot),
            ).also { journey = it }
        }
        if (!walk.clearedDemotions) {
            walk.clearedDemotions = true
            currentJourney.coarseState.planner.clearDemotions()
        }
        launchPlanning(walk, session, preparation, currentJourney)
        advanceSnapshotCapture(walk)
    }

    private fun SafeContext.advanceSnapshotCapture(walk: Walk) {
        if (status is Status.Planning) holdPlanningYaw(walk)
        if (activeWalk !== walk) return
        val activeJourney = journey ?: return

        InterestPrimer.primeBody(activeJourney.world, player.blockPos)
        val configured = walk.request.pathingConfig.snapshotCaptureBudgetMillis

        val budget = if (walk.cursor == null) maxOf(configured, IDLE_CAPTURE_BUDGET_MILLIS) else configured
        activeJourney.world.advance(budget)
    }

    private fun SafeContext.launchPlanning(
        walk: Walk,
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
                        if (activeWalk === walk && walk.planningSession === session) {
                            if (session.parked) parkNextLeg(walk, improvement)
                            else if (walk.cursor != null) adopt(walk, improvement)
                        }
                    }
                },
                onSafePrefix = { prefix ->

                    mc.execute {
                        if (activeWalk === walk && walk.planningSession === session) {
                            if (session.parked) parkNextLeg(walk, prefix)
                            else if (walk.cursor == null && walk.pendingPath == null) begin(walk, prefix)
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

            if (activeWalk !== walk || walk.planningSession !== session) return@whenCompleteAsync
            if (failure != null) {
                LOG.error("Pathing worker failed while expanding the trajectory", failure)
                fail("trajectory expansion crashed: ${failure.rootMessage()}")
                return@whenCompleteAsync
            }
            val completed = checkNotNull(result)
            when (completed) {
                is PathPlanResult.Planned ->
                    if (session.parked) parkNextLeg(walk, completed.path)
                    else if (walk.cursor != null) adopt(walk, completed.path) else begin(walk, completed.path)
                is PathPlanResult.Failed ->

                    if (session.parked) LOG.info(
                        "Pipelined next leg found nothing ({}); the tape end will replan", completed.failure.message,
                    )
                    else if (session.pipelined && activeWalk === walk && walk.cursor == null) {

                        LOG.info(
                            "Pipelined leg failed after hand-off ({}); replanning from the body",
                            completed.failure.message,
                        )
                        walk.planningSession = null
                        planTrajectory(walk)
                    }
                    else fail(completed.failure.message)
                PathPlanResult.Cancelled -> if (!session.parked) fail("planning was cancelled")
            }
            if (walk.planningSession === session) {
                walk.planningSession = null
            }

        }, mc)
    }

    private fun SafeContext.begin(walk: Walk, path: PublishedPath) {
        walk.planningSession?.let { session ->
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
            if (walk.planningSession?.generation != path.planningGeneration) {
                return keepRunning(path, "publication was certified from a stance the walk has left")
            }
            return fail("moved %.2f blocks while planning".format(drift))
        }

        val yawDrift = abs(Rotation.wrap(player.moveYaw - path.plan.initialState.rotation.yaw))
        if (yawDrift > START_YAW_TOLERANCE) {
            published = path
            walk.pendingPath = path
            walk.alignmentTicks = 0
            walk.tickInput = ALIGNMENT_INPUT
            status = Status.Aligning(walk.leg + 1, yawDrift)
            info(
                "Certified trajectory; aligning movement yaw by %.1f° before replay.".format(yawDrift),
                PATHING_SOURCE,
            )
            return
        }

        install(walk, path)
    }

    private fun SafeContext.adopt(walk: Walk, path: PublishedPath) {
        when (val verdict = ImprovementArbiter.judge(
            running = published,
            cursorFrame = walk.cursor?.nextFrame,
            awaitingObservation = walk.awaitingObservation,
            offered = path,
        )) {
            ImprovementArbiter.Verdict.BeginFresh -> begin(walk, path)

            is ImprovementArbiter.Verdict.Keep -> keepRunning(path, verdict.reason)

            ImprovementArbiter.Verdict.DeferForObservation -> walk.pendingImprovement = path

            is ImprovementArbiter.Verdict.Adopt -> {
                val frame = verdict.frame
                executionEnvironmentDeviation(path, nextFrame = frame)?.let { deviation ->
                    return keepRunning(path, "publication environment changed: $deviation")
                }
                published = path
                recordExecuted(path)
                journey?.world?.let { InterestPrimer.primeRoute(it, path.route) }
                walk.cursor = TrajectoryExecutionCursor(
                    path.plan, PlayerPhysicsProfile.capture(player),
                    tolerance = ExecutionStateTolerance().let {
                        it.copy(position = it.position + it.positionPerFrame * walk.handoffBaseFrames)
                    },
                ).apply { resumeAt(frame) }
                walk.planningSession?.updateExecutionFrame(frame)
                status = Status.Executing(frame, path.plan.tape.frameCount, walk.leg)
                adopted++
            }
        }
    }

    private fun recordExecuted(path: PublishedPath) {
        synchronized(executedPaths) {
            if (executedPaths.size == MAX_RETAINED_PUBLICATIONS) executedPaths.removeFirst()
            executedPaths.addLast(path)
        }
    }

    private fun parkNextLeg(walk: Walk, path: PublishedPath) {
        val parked = walk.pendingNextLeg
        if (parked == null || path.planningGeneration > parked.planningGeneration ||
            (path.planningGeneration == parked.planningGeneration &&
                path.publicationSequence > parked.publicationSequence)
        ) {
            walk.pendingNextLeg = path
        }
    }

    private fun SafeContext.pipelineNextLeg(walk: Walk) {
        val running = published ?: return
        if (!running.partial) return
        val currentJourney = journey ?: return
        walk.pipelinedTape = running.plan.id.value

        val terminal = running.plan.frames.last().state

        val request = walk.request
        val session = PlanningSession(
            ++planningGeneration, request, pipelined = true,
        )
        session.parked = true
        walk.planningSession = session
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
                walk.planningSession = null
                return
            }
        }
        if (!currentJourney.compatibleWith(preparation)) {
            walk.planningSession = null
            return
        }
        LOG.info(
            "Searching the next leg from the running tape's terminal stance {}", preparation.start,
        )
        launchPlanning(walk, session, preparation, currentJourney)
    }

    private fun keepRunning(rejected: PublishedPath, reason: String) {
        rejectedImprovements++
        LOG.info("Pathing kept the running tape: $reason (${rejected.plan.tape.frameCount} frames offered)")
    }

    private fun SafeContext.install(walk: Walk, path: PublishedPath) {
        executionEnvironmentDeviation(path, nextFrame = 0)?.let { deviation ->
            return fail("certified plan became stale before execution: $deviation")
        }
        walk.request.runSafeAutomated {
            rotationRequest { yaw(path.plan.initialState.rotation.yaw) }.submit()
        }
        journey?.world?.let { InterestPrimer.primeRoute(it, path.route) }
        walk.planningYaw = null
        walk.pendingPath = null
        walk.alignmentTicks = 0
        published = path
        recordExecuted(path)
        walk.cursor = TrajectoryExecutionCursor(
            path.plan, PlayerPhysicsProfile.capture(player),
            tolerance = ExecutionStateTolerance().let {
                it.copy(position = it.position + it.positionPerFrame * walk.handoffBaseFrames)
            },
        )
        walk.planningSession?.updateExecutionFrame(0)
        walk.awaitingObservation = false
        walk.leg++
        status = Status.Executing(0, path.plan.tape.frameCount, walk.leg)
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

    private fun SafeContext.fail(reason: String, goal: Stance? = activeWalk?.request?.goal ?: published?.finalGoal) {
        val position = player.blockPos?.let { "(${it.x}, ${it.y}, ${it.z})" } ?: "unknown position"
        val destination = goal?.let { " toward $it" } ?: ""
        val walked = activeWalk?.leg ?: 0
        releaseWalk(keepJourney = true)
        status = Status.Failed(reason)
        warn("Stopped ${if (walked == 0) "before" else "during"} continuous replay at $position$destination: $reason", PATHING_SOURCE)
    }

    private fun SafeContext.recover(reason: String, reuseCoarseState: Boolean = true) {
        val walk = activeWalk ?: return fail(reason)
        if (!reuseCoarseState) {
            journey?.cancel()
            journey = null
        }
        walk.resetForReplan()
        walk.tickInput = ALIGNMENT_INPUT
        recoveries++
        status = Status.Settling(walk.request.goal.let { "(${it.x}, ${it.y}, ${it.z})" })
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
            val walk = activeWalk ?: return@listen
            advanceSnapshotCapture(walk)
            if (activeWalk !== walk) return@listen
            if (status is Status.Settling) return@listen settle(walk)
            if (status is Status.Planning) return@listen
            if (status is Status.Aligning) return@listen align(walk)

            tickExecution(walk)
        }

        listen<MovementEvent.InputUpdate>({ Int.MAX_VALUE }) { event ->
            val walk = activeWalk
            val input = walk?.tickInput ?: return@listen flight.release()
            flight.hold()

            if (walk.awaitingObservation) {
                val path = published ?: return@listen fail("lost the certified plan before input application")
                val active = walk.cursor ?: return@listen fail("lost the execution cursor before input application")
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

    private fun SafeContext.tickExecution(walk: Walk) {
        val active = walk.cursor ?: return
        val path = published ?: return

        walk.planningSession?.updateExecutionFrame(active.nextFrame)

        val frame = active.nextFrame

        if (walk.awaitingObservation && !finishObservedTick(walk, active, path, frame)) return

        walk.pendingImprovement?.let { improvement ->
            walk.pendingImprovement = null
            adopt(walk, improvement)
        }

        if (walk.planningSession == null && walk.pendingNextLeg == null && walk.pendingImprovement == null &&
            !walk.awaitingObservation && walk.cursor != null && published?.partial == true &&
            published?.plan?.id?.value != walk.pipelinedTape
        ) {
            pipelineNextLeg(walk)
        }

        val current = published ?: return
        val running = walk.cursor ?: return

        if (running.nextFrame == 0) {
            val targetYaw = current.plan.initialState.rotation.yaw
            val yawError = abs(Rotation.wrap(player.moveYaw - targetYaw))
            if (yawError > START_YAW_TOLERANCE) {
                walk.request.runSafeAutomated { rotationRequest { yaw(targetYaw) }.submit() }
                walk.tickInput = ALIGNMENT_INPUT
                if (++walk.alignmentTicks > MAX_ALIGNMENT_TICKS) {
                    fail("could not hold the launch yaw before the first frame (%.1f degrees off)".format(yawError))
                }
                return
            }
        }

        val observed = observe(current.plan, running.nextFrame)
        executionEnvironmentDeviation(current, nextFrame = running.nextFrame)?.let { deviation ->
            return reject(running.nextFrame, deviation, observed, afterInput = false)
        }
        when (val next = running.nextInput(observed)) {
            is ExecutionInputResult.Apply -> {
                walk.tickInput = next.input
                walk.awaitingObservation = true
                status = Status.Executing(next.frame, current.plan.tape.frameCount, walk.leg)

                next.input.rotation?.let { rotation ->
                    walk.request.runSafeAutomated { rotationRequest { yaw(rotation.yaw) }.submit() }
                }
            }

            ExecutionInputResult.Complete -> finishTrajectory(walk, current)

            is ExecutionInputResult.Rejected -> reject(
                next.frame, next.deviation, observed, afterInput = false,
            )
        }
    }

    private fun SafeContext.finishObservedTick(
        walk: Walk,
        active: TrajectoryExecutionCursor,
        path: PublishedPath,
        frame: Int,
    ): Boolean {
        val observed = observe(path.plan, frame + 1)
        executionEnvironmentDeviation(path, nextFrame = frame)?.let { deviation ->
            reject(frame, deviation, observed, afterInput = true)
            return false
        }
        val result = active.observeAfterTick(observed)
        if (result is ExecutionObservationResult.Rejected) {
            reject(result.frame, result.deviation, observed, afterInput = true)
            return false
        }
        walk.awaitingObservation = false

        if (frame < path.plan.frames.size) {
            val predicted = path.plan.frames[frame].state.position
            synchronized(trail) {
                if (trail.size == MAX_RETAINED_TRAIL_POINTS) trail.removeFirst()
                trail.addLast(player.pos)
            }
            maxDeviation = max(maxDeviation, player.pos.distanceTo(predicted))
        }

        if (result === ExecutionObservationResult.Complete) {
            finishTrajectory(walk, path)
            return false
        }
        return true
    }

    private fun holdPlanningYaw(walk: Walk) {
        val yaw = walk.planningYaw ?: return
        walk.request.runSafeAutomated { rotationRequest { yaw(yaw) }.submit() }
    }


    private fun SafeContext.align(walk: Walk) {
        val path = walk.pendingPath ?: return fail("lost the certified plan while aligning")
        val positionDrift = player.pos.distanceTo(path.plan.initialState.position)
        if (positionDrift > START_DRIFT_TOLERANCE) {
            return fail("moved %.2f blocks while aligning to the plan".format(positionDrift))
        }

        val targetYaw = path.plan.initialState.rotation.yaw
        walk.request.runSafeAutomated { rotationRequest { yaw(targetYaw) }.submit() }
        val yawDrift = abs(Rotation.wrap(player.moveYaw - targetYaw))

        val settled = player.velocity.horizontalLength() <= SETTLED_SPEED && player.isOnGround
        if (yawDrift <= START_YAW_TOLERANCE && settled) {
            install(walk, path)
            return
        }

        status = Status.Aligning(walk.leg + 1, yawDrift)
        if (++walk.alignmentTicks > MAX_ALIGNMENT_TICKS) {
            fail("could not align movement yaw to the plan (%.1f degrees remain)".format(yawDrift))
        }
    }

    private fun SafeContext.finishTrajectory(walk: Walk, path: PublishedPath) {
        walk.cursor = null
        walk.planningSession?.updateExecutionFrame(null)
        walk.tickInput = null
        walk.awaitingObservation = false

        if (path.partial) {
            walk.pendingImprovement = null
            info(
                "Safe partial tape complete (${path.plan.tape.frameCount} frames); " +
                    "continuing toward ${path.finalGoal}.",
                PATHING_SOURCE,
            )
            val next = walk.pendingNextLeg
            walk.pendingNextLeg = null
            val terminal = path.plan.frames.last().state.position
            if (next != null &&
                next.plan.initialState.position.distanceTo(terminal) <= START_DRIFT_TOLERANCE
            ) {

                walk.planningSession?.parked = false
                published = next
                walk.pendingPath = next
                walk.handoffBaseFrames += path.plan.tape.frameCount
                walk.alignmentTicks = 0
                walk.tickInput = ALIGNMENT_INPUT
                status = Status.Aligning(walk.leg + 1, 0.0)
                return
            }
            if (next != null) {

                LOG.info(
                    "Discarding a pipelined leg rooted {} blocks from the tape terminal",
                    "%.2f".format(next.plan.initialState.position.distanceTo(terminal)),
                )
            }
            val successor = walk.planningSession
            if (successor != null && successor.parked) {

                successor.parked = false
                status = Status.Planning("(${path.finalGoal.x}, ${path.finalGoal.y}, ${path.finalGoal.z})")
                return
            }
            walk.cancelPlanning()
            planTrajectory(walk)
            return
        }
        walk.cancelPlanning()

        status = Status.Complete(path.plan.tape.frameCount, walk.leg)
        activeWalk = null
        flight.release()
        info(
            "Reached ${path.finalGoal} after ${walk.leg} certified trajectory leg(s); " +
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

    private const val MAX_ALIGNMENT_TICKS = 40

    private const val SETTLED_SPEED = 1e-6

    private const val MAX_SETTLE_TICKS = 40

    private const val IDLE_CAPTURE_BUDGET_MILLIS = 15.0

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
