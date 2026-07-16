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
import com.lambda.pathing.execution.ExecutionDeviation
import com.lambda.pathing.execution.ExecutionInputResult
import com.lambda.pathing.execution.ExecutionObservationResult
import com.lambda.pathing.execution.TrajectoryExecutionCursor
import com.lambda.pathing.trajectory.TrajectoryPlan
import com.lambda.pathing.trajectory.WalkingSeedParameters
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

/**
 * Single owner of walking execution. Callers submit a [PathingRequest]; the manager
 * plans off-thread, then replays the certified tape through
 * [TrajectoryExecutionCursor].
 *
 * The manager holds no steering logic of its own. It emits the tape's stored input
 * and compares the observed state against the tape's expected state -- if they
 * disagree it stops, it never corrects. All rotation goes through
 * [RotationManager] under the requester's own automation config.
 */
object PathingManager : Manager<PathingRequest>(0) {
    /** Frozen result of a successful plan, safe to read from the render thread. */
    data class PublishedPath(
        val route: CoarseRoutePlan,
        val plan: TrajectoryPlan,
        val profile: PlayerPhysicsProfile,
        val parameters: WalkingSeedParameters,
        val attempts: Int,
        val planMillis: Long,
        /** Where the continuously expanded tape must end. */
        val finalGoal: Stance,
        /** Worker-local control pieces expanded into the single published tape. */
        val controlSegments: Int = 1,
        /** Predicted moving-state boundaries already flattened into [plan]. */
        val spliceFrames: List<Int> = emptyList(),
        /** Infeasible coarse jumps D* was rerouted around before this plan certified. */
        val reroutes: Int = 0,
        /** Failure-directed launch runway accumulated across the certified tape. */
        val launchMarginFrames: Int = 0,
    )

    sealed interface Status {
        data object Idle : Status

        /**
         * Bringing the body to true rest before the initial state is captured. A plan
         * begins from the exact state it was simulated at; capturing a still-drifting
         * body freezes a moving frame zero that no longer exists once the async plan
         * returns, and the cursor then rejects it. Only entered when actually moving.
         */
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

    /**
     * The render config of whoever asked for the current path. Live, not a copy: the
     * renderer should pick up setting changes without a replan.
     */
    @Volatile
    var renderConfig: PathingRenderConfig = AutomationConfig.DEFAULT.pathingRenderConfig
        private set

    private val trail = ArrayList<Vec3d>()

    /** Immutable view for the renderer. */
    val liveTrail: List<Vec3d> get() = synchronized(trail) { ArrayList(trail) }

    private var activeRequest: PathingRequest? = null

    /** Zero before publication, one while/after replay of the single full trajectory. */
    private var leg = 0
    /** Yaw captured with the planning snapshot; held until the worker result arrives. */
    private var planningYaw: Double? = null
    /** A certified plan waiting for movement yaw to match its immutable initial state. */
    private var pendingPath: PublishedPath? = null
    private var alignmentTicks = 0
    private var settleTicks = 0
    private var cursor: TrajectoryExecutionCursor? = null
    /**
     * The input the tape says to press this tick. Written in [TickEvent.Pre] and read
     * by both the input hook and the sprint hook, so it must outlive the input write.
     */
    private var tickInput: MovementSimulationInput? = null
    private var awaitingObservation = false

    fun isFinished(request: PathingRequest): Boolean =
        activeRequest !== request || status is Status.Complete || status is Status.Failed

    /** Abandons the walk. The player simply stops; nothing is left holding the input. */
    fun cancel() {
        activeRequest = null
        cursor = null
        tickInput = null
        awaitingObservation = false
        leg = 0
        planningYaw = null
        pendingPath = null
        alignmentTicks = 0
        settleTicks = 0
        if (status is Status.Settling || status is Status.Planning ||
            status is Status.Aligning || status is Status.Executing
        ) {
            status = Status.Idle
        }
    }

    fun clear() {
        cancel()
        published = null
        status = Status.Idle
        maxDeviation = 0.0
        synchronized(trail) { trail.clear() }
        PlanningDebugChannel.reset()
    }

    override fun AutomatedSafeContext.handleRequest(request: PathingRequest) {
        if (!request.fresh) return

        cancel()
        published = null
        maxDeviation = 0.0
        synchronized(trail) { trail.clear() }

        activeRequest = request
        renderConfig = request.pathingRenderConfig
        leg = 0

        unsteerable(request)?.let { return fail(it, request.goal) }
        planTrajectory(request)
    }

    /**
     * Plans one full trajectory. Local search horizons may be concatenated from
     * predicted moving states on the worker, but no partial route is executable.
     *
     * If the body still carries drift from a previous action, it is settled to true
     * rest first: the plan's frame zero is the exact state it was simulated at, and a
     * moving capture is stale before the async worker even returns.
     */
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

    /**
     * Presses zero movement input until the body reaches the rest vanilla clamps to,
     * then captures and plans. Bounded: a body that will not settle in time is planned
     * from where it is, with the drift guard in [begin] as the backstop.
     */
    private fun settle(request: PathingRequest) {
        val player = mc.player ?: return fail("no player")
        tickInput = ALIGNMENT_INPUT
        if (player.velocity.horizontalLength() <= SETTLED_SPEED && player.isOnGround) {
            return capturePlan(request, player)
        }
        if (++settleTicks > MAX_SETTLE_TICKS) {
            // Something keeps the body moving with no input of ours -- a slope, ice, a
            // current. Better a clear refusal than a capture the cursor will reject.
            fail("could not settle to a stable start (%.3f b/t after %d ticks)".format(
                player.velocity.horizontalLength(), settleTicks,
            ))
        }
    }

    private fun capturePlan(request: PathingRequest, player: ClientPlayerEntity) {
        tickInput = null

        // Movement yaw is part of the simulator's immutable initial state. A prior
        // leg's request can decay back toward the camera while this worker runs, so
        // keep the captured yaw alive until adoption instead of rejecting a valid
        // continuation merely because planning crossed a tick boundary.
        planningYaw = player.moveYaw.toDouble()
        status = Status.Planning("(${request.goal.x}, ${request.goal.y}, ${request.goal.z})")
        PlanningDebugChannel.begin(
            request.pathingRenderConfig.enabled && request.pathingRenderConfig.renderPlanning,
        )

        val planning = try {
            TrajectoryPlanner.planAsync(player, request.goal, request.pathingConfig)
        } catch (failure: Exception) {
            fail("could not capture a world snapshot: ${failure.message}")
            return
        }

        planning.whenCompleteAsync({ result, failure ->
            // Late results from a superseded request must not install themselves.
            if (activeRequest !== request) return@whenCompleteAsync
            if (failure != null) {
                LOG.error("Pathing worker failed while expanding the trajectory", failure)
                fail("trajectory expansion crashed: ${failure.rootMessage()}")
                return@whenCompleteAsync
            }
            when (val completed = checkNotNull(result)) {
                is PathPlanResult.Planned -> begin(completed.path)
                is PathPlanResult.NoRoute -> fail(completed.reason)
                is PathPlanResult.NoSafeStop -> fail(completed.summary)
            }
        }, mc)
    }

    private fun begin(path: PublishedPath) {
        val player = mc.player ?: return fail("no player")
        if (path.route.goal != path.finalGoal) {
            return fail("planner attempted to publish a partial route ending at ${path.route.goal.short()}")
        }

        // The tape is only valid from the state it was simulated at; the cursor
        // would reject on frame 0 anyway, but this says why in one line.
        val drift = player.pos.distanceTo(path.plan.initialState.position)
        if (drift > START_DRIFT_TOLERANCE) {
            return fail("moved %.2f blocks while planning".format(drift))
        }

        // Frame 0 is immutable, but yaw can safely be repaired while the body is
        // stopped: rotate in place to the captured heading before giving the cursor
        // any input. Position drift cannot be repaired this way and still refuses.
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

    private fun install(path: PublishedPath) {
        planningYaw = null
        pendingPath = null
        alignmentTicks = 0
        published = path
        cursor = TrajectoryExecutionCursor(path.plan, path.profile)
        awaitingObservation = false
        leg = 1
        status = Status.Executing(0, path.plan.tape.frameCount, leg)
        info(
            "Certified continuous trajectory: ${path.route.nodes.first().short()} -> ${path.route.goal.short()}, " +
                "${path.plan.tape.frameCount} frames, ${path.attempts} attempts, " +
                "${path.planMillis} ms, ${path.controlSegments} continuous segment(s)" +
                path.parameters.gapLaunchFrames.takeIf { it.isNotEmpty() }
                    ?.let { ", jump launch frames ${it.joinToString()}" }.orEmpty() +
                path.spliceFrames.takeIf { it.isNotEmpty() }
                    ?.let { ", predicted splice frames ${it.joinToString()}" }.orEmpty() +
                path.reroutes.takeIf { it > 0 }
                    ?.let { ", rerouted around $it infeasible coarse jump(s)" }.orEmpty(),
            PATHING_SOURCE,
        )
    }

    /**
     * Refuse configs that cannot steer a tape, rather than walking off in a
     * plausible-looking wrong direction. Unsupported physics is a typed result.
     */
    private fun unsteerable(request: PathingRequest): String? {
        val config = request.rotationConfig
        if (config.rotationMode == RotationMode.Silent) {
            return "rotation mode Silent cannot steer movement: it nulls movementYaw, " +
                "so the body would follow the camera instead of the plan. Use Sync or Lock."
        }
        if (config.turnSpeed < MIN_TURN_SPEED) {
            return "turn speed %.0f deg/tick is below the %.0f the plan needs; the turn would fall short each tick"
                .format(config.turnSpeed, MIN_TURN_SPEED)
        }
        return null
    }

    private fun fail(reason: String, goal: Stance? = activeRequest?.goal ?: published?.finalGoal) {
        val position = mc.player?.blockPos?.let { "(${it.x}, ${it.y}, ${it.z})" } ?: "unknown position"
        val destination = goal?.let { " toward ${it.short()}" } ?: ""
        status = Status.Failed(reason)
        activeRequest = null
        cursor = null
        tickInput = null
        awaitingObservation = false
        planningYaw = null
        pendingPath = null
        alignmentTicks = 0
        warn(
            "Stopped ${if (leg == 0) "before" else "during"} continuous replay at " +
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

            // snapshotRevision is a capture timestamp, not a content revision, so
            // the live world clock would reject every frame. Dependency-revision
            // indexing (M7) is what makes this check real; until then the tape
            // assumes the world has not changed under it.
            val revision = path.plan.snapshotRevision
            val frame = active.nextFrame

            if (awaitingObservation) {
                // After the tick, the jump the client holds is the one frame [frame]
                // wrote -- which observe() reads from tape[f - 1], hence frame + 1.
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

            val observed = observe(path.plan, active.nextFrame)
            when (val next = active.nextInput(observed, revision)) {
                is ExecutionInputResult.Apply -> {
                    tickInput = next.input
                    awaitingObservation = true
                    status = Status.Executing(next.frame, path.plan.tape.frameCount, leg)

                    // Yaw only, and only as a request. `EntityMixin.velocityYaw` already
                    // routes Entity.updateVelocity through RotationManager.movementYaw,
                    // so a non-silent request IS how the body turns -- writing player.yaw
                    // would only yank the camera out of the user's hands for no effect.
                    // Pitch is never requested: it does not enter walking physics, and
                    // requesting it would lock the head.
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

        // Fires inside tickMovement, after the keyboard input has ticked and after
        // the rotation manager's strafe redirect -- the last point before the
        // physics step reads the input, so nothing downstream can stomp the tape.
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

        // Sprint is deliberately NOT forced through MovementEvent.Sprint. Vanilla
        // already takes it from the input we wrote above
        // (`if (input.playerInput.sprint()) setSprinting(true)`), and that event
        // wraps *every* isSprinting() read in tickMovement -- including the guard on
        // `if (isSprinting()) { if (shouldStopSprinting()) setSprinting(false) }`.
        // Forcing it to the tape's value therefore stops the brake from ever
        // clearing the sprint flag.
    }

    /** Prevents a captured movement yaw from decaying back to the camera while planning. */
    private fun holdPlanningYaw(request: PathingRequest) {
        val yaw = planningYaw ?: return
        request.runSafeAutomated { rotationRequest { yaw(yaw) }.submit() }
    }

    /** Rotates in place to the certified initial yaw, then installs the cursor next tick. */
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
            // Keep the zero input for this tick. The alignment request is fresh and
            // cannot be overridden until the next tick; replay starts only then.
            install(path)
            return
        }

        status = Status.Aligning(leg + 1, yawDrift)
        if (++alignmentTicks > MAX_ALIGNMENT_TICKS) {
            fail("could not align movement yaw to the plan (%.1f degrees remain)".format(yawDrift))
        }
    }

    /** The one published tape ended at its final certified stop. */
    private fun finishTrajectory(path: PublishedPath) {
        cursor = null
        tickInput = null
        awaitingObservation = false
        status = Status.Complete(path.plan.tape.frameCount, 1)
        activeRequest = null
        info(
            "Reached ${path.finalGoal.short()} in one continuous ${path.plan.tape.frameCount}-frame trajectory; " +
                "max replay deviation %.2e".format(maxDeviation),
            PATHING_SOURCE,
        )
    }

    private fun reject(
        frame: Int,
        deviation: ExecutionDeviation,
        observed: MovementSimulationState,
        afterInput: Boolean,
    ) {
        val path = published
        val splice = path?.spliceFrames?.let { boundaries ->
            when {
                frame in boundaries -> " at predicted splice"
                frame + 1 in boundaries -> " immediately before predicted splice"
                else -> ""
            }
        }.orEmpty()
        val input = path?.plan?.tape?.asList()?.getOrNull(frame)
        val expected = path?.plan?.let { plan ->
            if (afterInput) plan.frames.getOrNull(frame)?.state
            else if (frame == 0) plan.initialState else plan.frames.getOrNull(frame - 1)?.state
        }
        val phase = if (afterInput) "after input" else "before input"
        val inputDetail = input?.let {
            "; input f=%.1f s=%.1f jump=%s sprintKey=%s yaw=%s".format(
                it.forward, it.strafe, it.jump, it.sprint,
                it.rotation?.yaw?.let { yaw -> "%.2f".format(yaw) } ?: "hold",
            )
        }.orEmpty()
        val stateDetail = expected?.let {
            ("; expected/live yaw %.3f/%.3f, sprint %s/%s, ground %s/%s, " +
                "hCollision %s/%s, soft %s/%s, vCollision %s/%s, jumpCooldown %d/%d, " +
                "position %s/%s, velocity %s/%s").format(
                it.rotation.yaw, observed.rotation.yaw,
                it.isSprinting, observed.isSprinting,
                it.onGround, observed.onGround,
                it.horizontalCollision, observed.horizontalCollision,
                it.collidedSoftly, observed.collidedSoftly,
                it.verticalCollision, observed.verticalCollision,
                it.jumpingCooldown, observed.jumpingCooldown,
                it.position.short(), observed.position.short(),
                it.velocity.short(), observed.velocity.short(),
            )
        }.orEmpty()
        val previousInputDetail = path?.plan?.tape?.asList()?.getOrNull(frame - 1)?.let {
            "; previous input f=%.1f jump=%s sprintKey=%s".format(it.forward, it.jump, it.sprint)
        }.orEmpty()
        val liveInputDetail = mc.player?.input?.let {
            "; live input f=%.1f jump=%s sprintKey=%s".format(
                it.movementVector.y, it.playerInput.jump(), it.playerInput.sprint(),
            )
        }.orEmpty()
        fail(
            "frame $frame $phase$splice: $deviation$inputDetail$previousInputDetail" +
                "$liveInputDetail$stateDetail",
        )
    }

    /**
     * `LivingEntity.jumping` is not exposed, but it only ever mirrors the jump input
     * written on the previous tick, so it is reconstructed from the tape. Every
     * physics-bearing field is read from the live player.
     */
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

    /**
     * Horizontal speed below which the body counts as at rest for capture.
     *
     * It must mean *actually stopped*, not almost: vanilla clamps horizontal velocity to
     * exactly zero under 0.003, and only after that clamp fires does the body stop moving.
     * Capturing at, say, 0.0029 would still bleed up to that much drift across the async
     * plan, and frame zero is checked to 2e-6. So this waits for the post-clamp zero --
     * one or two ticks longer, and the difference between a valid capture and a rejected
     * one.
     */
    private const val SETTLED_SPEED = 1e-6

    /** From any settleable speed the clamp fires within ~10 ticks; this is ample headroom. */
    private const val MAX_SETTLE_TICKS = 40

    /** The seed search caps yaw change at 30 deg/frame; the turn must clear that. */
    private const val MIN_TURN_SPEED = 30.0

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
