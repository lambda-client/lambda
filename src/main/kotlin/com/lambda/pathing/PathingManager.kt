/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing

import com.lambda.Lambda.mc
import com.lambda.Lambda.LOG
import com.lambda.config.automation.AutomationConfig
import com.lambda.config.blocks.PathingRenderConfig
import com.lambda.context.AutomatedSafeContext
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
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
import com.lambda.pathing.execution.ExecutionObservationResult
import com.lambda.pathing.execution.TrajectoryExecutionCursor
import com.lambda.pathing.trajectory.TrajectoryPlan
import com.lambda.pathing.trajectory.TerminalApproach
import com.lambda.threading.runSafeAutomated
import com.lambda.util.player.MovementUtils.moveYaw
import com.lambda.util.player.MovementUtils.update
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.CommunicationUtils.warn
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.Vec3d
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max

object PathingManager : Manager<PathingRequest>(0) {
    data class PublishedPath(
        val route: CoarseRoutePlan,
        val plan: TrajectoryPlan,
        val profile: PlayerPhysicsProfile,
        val parameters: TerminalApproach,
        val attempts: Int,
        val planMillis: Long,
        val finalGoal: Stance,
        val controlSegments: Int = 1,
        val spliceFrames: List<Int> = emptyList(),
        val launchMarginFrames: Int = 0,
        val partial: Boolean = false,
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
    var firstFullPath: PublishedPath? = null
        private set

    private val executedPaths = ArrayList<PublishedPath>()

    val executed: List<PublishedPath> get() = synchronized(executedPaths) { ArrayList(executedPaths) }

    @Volatile
    var rejectedImprovements: Int = 0
        private set

    @Volatile
    var renderConfig: PathingRenderConfig = AutomationConfig.DEFAULT.pathingRenderConfig
        private set

    private val trail = ArrayList<Vec3d>()

    val liveTrail: List<Vec3d> get() = synchronized(trail) { ArrayList(trail) }

    private var activeRequest: PathingRequest? = null

    private var leg = 0

    private var planningYaw: Double? = null

    private var pendingPath: PublishedPath? = null
    private var alignmentTicks = 0
    private var settleTicks = 0
    private var cursor: TrajectoryExecutionCursor? = null

    private var tickInput: MovementSimulationInput? = null
    private var awaitingObservation = false

    private var pendingImprovement: PublishedPath? = null

    fun isFinished(request: PathingRequest): Boolean =
        activeRequest !== request || status is Status.Complete || status is Status.Failed

    private fun releaseWalk() {
        activeRequest = null
        cursor = null
        tickInput = null
        awaitingObservation = false
        planningYaw = null
        pendingPath = null
        pendingImprovement = null
        alignmentTicks = 0
        settleTicks = 0
        leg = 0
    }

    fun cancel() {
        releaseWalk()
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
        rejectedImprovements = 0
        firstFullPath = null
        synchronized(executedPaths) { executedPaths.clear() }
        synchronized(trail) { trail.clear() }
        PlanningDebugChannel.reset()
    }

    override fun AutomatedSafeContext.handleRequest(request: PathingRequest) {
        if (!request.fresh) return

        clear()

        activeRequest = request
        renderConfig = request.pathingRenderConfig

        unsteerable(request)?.let { return fail(it, request.goal) }
        planTrajectory(request)
    }

    private fun planTrajectory(request: PathingRequest) {
        val player = mc.player ?: return fail("no player")
        if (player.velocity.horizontalLength() > SETTLED_SPEED) {
            settleTicks = 0
            tickInput = ALIGNMENT_INPUT
            status = Status.Settling("(${request.goal.x}, ${request.goal.y}, ${request.goal.z})")
            return
        }
        capturePlan(request, player)
    }

    private fun settle(request: PathingRequest) {
        val player = mc.player ?: return fail("no player")
        tickInput = ALIGNMENT_INPUT
        if (player.velocity.horizontalLength() <= SETTLED_SPEED && player.isOnGround) {
            return capturePlan(request, player)
        }
        if (++settleTicks > MAX_SETTLE_TICKS) {
            fail("could not settle to a stable start (%.3f b/t after %d ticks)".format(
                player.velocity.horizontalLength(), settleTicks,
            ))
        }
    }

    private fun capturePlan(request: PathingRequest, player: ClientPlayerEntity) {
        tickInput = null

        planningYaw = player.moveYaw.toDouble()
        status = Status.Planning("(${request.goal.x}, ${request.goal.y}, ${request.goal.z})")
        PlanningDebugChannel.begin(
            request.pathingRenderConfig.enabled && request.pathingRenderConfig.renderPlanning,
        )

        val planning = try {
            TrajectoryPlanner.planAsync(
                player, request.goal, request.pathingConfig,
                turnSpeed = request.rotationConfig.turnSpeed,
                cursorFrame = { cursor?.nextFrame },
                onImprovement = { improvement ->
                    mc.execute {
                        if (activeRequest === request && cursor != null) adopt(improvement)
                    }
                },
                onSafePrefix = { prefix ->

                    mc.execute {
                        if (activeRequest === request && cursor == null && pendingPath == null) {
                            begin(prefix)
                        }
                    }
                },
            )
        } catch (failure: Exception) {
            fail("could not capture a world snapshot: ${failure.message}")
            return
        }

        planning.whenCompleteAsync({ result, failure ->

            if (activeRequest !== request) return@whenCompleteAsync
            if (failure != null) {
                LOG.error("Pathing worker failed while expanding the trajectory", failure)
                fail("trajectory expansion crashed: ${failure.rootMessage()}")
                return@whenCompleteAsync
            }
            when (val completed = checkNotNull(result)) {
                is PathPlanResult.Planned ->
                    if (cursor != null) adopt(completed.path) else begin(completed.path)
                is PathPlanResult.NoRoute -> fail(completed.reason)
            }
        }, mc)
    }

    private fun begin(path: PublishedPath) {
        val player = mc.player ?: return fail("no player")
        if (!path.partial && path.route.goal != path.finalGoal) {
            return fail("planner attempted to publish a partial route ending at ${path.route.goal.short()}")
        }

        val drift = player.pos.distanceTo(path.plan.initialState.position)
        if (drift > START_DRIFT_TOLERANCE) {
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

    private fun adopt(path: PublishedPath) {
        val running = published
        val active = cursor
        if (running == null || active == null) return begin(path)
        if (awaitingObservation) {
            pendingImprovement = path
            return
        }

        val frame = active.nextFrame
        if (frame > path.plan.tape.frameCount) return keepRunning(path, "improvement is shorter than the walk so far")

        if (!running.partial && !path.partial && path.plan.tape.frameCount >= running.plan.tape.frameCount) {
            return keepRunning(path, "improvement is not shorter than the running tape")
        }
        val diverges = (0 until frame).any { running.plan.tape[it] != path.plan.tape[it] }
        if (diverges) return keepRunning(path, "improvement diverges behind the cursor")

        published = path
        recordExecuted(path)
        cursor = TrajectoryExecutionCursor(path.plan, path.profile).apply { resumeAt(frame) }
        status = Status.Executing(frame, path.plan.tape.frameCount, leg)
        adopted++
        info(
            "Improved the trajectory while walking: frame $frame of " +
                "${running.plan.tape.frameCount} -> ${path.plan.tape.frameCount} frames" +
                (if (running.partial) " (was a safe partial plan)" else ""),
            PATHING_SOURCE,
        )
    }

    private fun recordExecuted(path: PublishedPath) {
        if (!path.partial && firstFullPath == null) firstFullPath = path
        synchronized(executedPaths) { executedPaths += path }
    }

    private fun keepRunning(rejected: PublishedPath, reason: String) {
        rejectedImprovements++
        LOG.info("Pathing kept the running tape: $reason (${rejected.plan.tape.frameCount} frames offered)")
    }

    private fun install(path: PublishedPath) {
        planningYaw = null
        pendingPath = null
        alignmentTicks = 0
        published = path
        recordExecuted(path)
        cursor = TrajectoryExecutionCursor(path.plan, path.profile)
        awaitingObservation = false
        leg = 1
        status = Status.Executing(0, path.plan.tape.frameCount, leg)
        info(
            "Certified continuous trajectory: ${path.route.nodes.first().short()} -> ${path.route.goal.short()}, " +
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

    private fun fail(reason: String, goal: Stance? = activeRequest?.goal ?: published?.finalGoal) {
        val position = mc.player?.blockPos?.let { "(${it.x}, ${it.y}, ${it.z})" } ?: "unknown position"
        val destination = goal?.let { " toward ${it.short()}" } ?: ""
        val walked = leg
        releaseWalk()
        status = Status.Failed(reason)
        warn(
            "Stopped ${if (walked == 0) "before" else "during"} continuous replay at " +
                "$position$destination: $reason",
            PATHING_SOURCE,
        )
    }

    init {
        listenUnsafe<ConnectionEvent.Disconnect> { clear() }

        listen<TickEvent.Pre> {
            val request = activeRequest ?: return@listen
            if (status is Status.Settling) return@listen settle(request)
            if (status is Status.Planning) return@listen holdPlanningYaw(request)
            if (status is Status.Aligning) return@listen align(request)

            val active = cursor ?: return@listen
            val path = published ?: return@listen

            val revision = path.plan.snapshotRevision
            val frame = active.nextFrame

            if (awaitingObservation) {
                val observed = observe(path.plan, frame + 1)
                val result = active.observeAfterTick(observed, revision)
                if (result is ExecutionObservationResult.Rejected) {
                    return@listen reject(result.frame, result.deviation, observed, afterInput = true)
                }
                awaitingObservation = false

                if (frame < path.plan.frames.size) {
                    val predicted = path.plan.frames[frame].state.position
                    synchronized(trail) { trail += player.pos }
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
            val current = published ?: return@listen
            val running = cursor ?: return@listen

            val observed = observe(current.plan, running.nextFrame)
            when (val next = running.nextInput(observed, revision)) {
                is ExecutionInputResult.Apply -> {
                    tickInput = next.input
                    awaitingObservation = true
                    status = Status.Executing(next.frame, current.plan.tape.frameCount, leg)

                    next.input.rotation?.let { rotation ->
                        request.runSafeAutomated { rotationRequest { yaw(rotation.yaw) }.submit() }
                    }
                }

                ExecutionInputResult.Complete -> finishTrajectory(path)

                is ExecutionInputResult.Rejected -> reject(
                    next.frame, next.deviation, observed, afterInput = false,
                )
            }
        }

        listen<MovementEvent.InputUpdate> { event ->
            val input = tickInput ?: return@listen
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

    private fun align(request: PathingRequest) {
        val player = mc.player ?: return fail("no player")
        val path = pendingPath ?: return fail("lost the certified plan while aligning")
        val positionDrift = player.pos.distanceTo(path.plan.initialState.position)
        if (positionDrift > START_DRIFT_TOLERANCE) {
            return fail("moved %.2f blocks while aligning to the plan".format(positionDrift))
        }

        val targetYaw = path.plan.initialState.rotation.yaw
        request.runSafeAutomated { rotationRequest { yaw(targetYaw) }.submit() }
        val yawDrift = abs(Rotation.wrap(player.moveYaw - targetYaw))
        if (yawDrift <= START_YAW_TOLERANCE) {
            install(path)
            return
        }

        status = Status.Aligning(leg + 1, yawDrift)
        if (++alignmentTicks > MAX_ALIGNMENT_TICKS) {
            fail("could not align movement yaw to the plan (%.1f degrees remain)".format(yawDrift))
        }
    }

    private fun finishTrajectory(path: PublishedPath) {
        cursor = null
        tickInput = null
        awaitingObservation = false

        if (path.partial) {
            val request = activeRequest
            if (request != null) {
                info(
                    "Safe partial tape complete (${path.plan.tape.frameCount} frames); " +
                        "continuing toward ${path.finalGoal.short()}.",
                    PATHING_SOURCE,
                )
                planTrajectory(request)
                return
            }
        }
        status = Status.Complete(path.plan.tape.frameCount, 1)
        activeRequest = null
        info(
            "Reached ${path.finalGoal.short()} in one continuous ${path.plan.tape.frameCount}-frame trajectory; " +
                "max replay deviation %.2e".format(maxDeviation) +

                ", adopted $adopted improvement(s)" +
                (if (rejectedImprovements > 0) ", rejected $rejectedImprovements" else "") +
                ", ${TrajectoryPlanner.publications} commitment(s) published",
            PATHING_SOURCE,
        )
    }

    private fun reject(
        frame: Int,
        deviation: ExecutionDeviation,
        observed: MovementSimulationState,
        afterInput: Boolean,
    ) = fail(executionRejectionReport(published, frame, deviation, observed, afterInput))

    private fun observe(plan: TrajectoryPlan, frame: Int): MovementSimulationState {
        val player = mc.player ?: error("No player")
        return MovementSimulationState.from(
            player,
            isJumping = frame > 0 && frame <= plan.tape.frameCount && plan.tape[frame - 1].jump,
        )
    }

    private const val START_DRIFT_TOLERANCE = 0.35
    private const val START_YAW_TOLERANCE = 1.0

    private const val MAX_ALIGNMENT_TICKS = 20

    private const val SETTLED_SPEED = 1e-6

    private const val MAX_SETTLE_TICKS = 40

    private const val PATHING_SOURCE = "Pathing"

    private val ALIGNMENT_INPUT = MovementSimulationInput()

    private fun Stance.short() = "($x, $y, $z)"

    private fun Vec3d.short() = "(%.4f,%.4f,%.4f)".format(x, y, z)

    private fun Throwable.rootMessage(): String {
        var root = this
        while (root.cause != null && root.cause !== root) root = root.cause!!
        return root.message ?: root::class.simpleName ?: "unknown error"
    }
}
