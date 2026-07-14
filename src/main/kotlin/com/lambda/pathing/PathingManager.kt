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
    )

    sealed interface Status {
        data object Idle : Status
        data class Planning(val goal: String) : Status
        data class Executing(val frame: Int, val frames: Int) : Status
        data class Complete(val frames: Int) : Status
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
        if (status is Status.Planning || status is Status.Executing) status = Status.Idle
    }

    fun clear() {
        cancel()
        published = null
        status = Status.Idle
        maxDeviation = 0.0
        synchronized(trail) { trail.clear() }
    }

    override fun AutomatedSafeContext.handleRequest(request: PathingRequest) {
        if (!request.fresh) return

        unsteerable(request)?.let { return fail(it) }

        cancel()
        published = null
        maxDeviation = 0.0
        synchronized(trail) { trail.clear() }

        activeRequest = request
        renderConfig = request.pathingRenderConfig
        status = Status.Planning("(${request.goal.x}, ${request.goal.y}, ${request.goal.z})")

        val planning = try {
            TrajectoryPlanner.planAsync(player, request.goal, request.pathingConfig)
        } catch (failure: Exception) {
            fail("could not capture a world snapshot: ${failure.message}")
            return
        }

        planning.thenAcceptAsync({ result ->
            // Late results from a superseded request must not install themselves.
            if (activeRequest !== request) return@thenAcceptAsync
            when (result) {
                is PathPlanResult.Planned -> begin(request, result.path)
                is PathPlanResult.NoRoute -> fail(result.reason)
                is PathPlanResult.NoSafeStop -> fail(result.summary)
            }
        }, mc)
    }

    private fun begin(request: PathingRequest, path: PublishedPath) {
        val player = mc.player ?: return fail("no player")

        // The tape is only valid from the state it was simulated at; the cursor
        // would reject on frame 0 anyway, but this says why in one line.
        val drift = player.pos.distanceTo(path.plan.initialState.position)
        if (drift > START_DRIFT_TOLERANCE) {
            return fail("moved %.2f blocks while planning".format(drift))
        }

        // Frame 0 is steered from the heading the tape was seeded at, and until the
        // first rotation request lands the movement yaw is still the camera's.
        val yawDrift = abs(Rotation.wrap(player.moveYaw - path.plan.initialState.rotation.yaw))
        if (yawDrift > START_YAW_TOLERANCE) {
            return fail("turned %.1f degrees while planning".format(yawDrift))
        }

        published = path
        cursor = TrajectoryExecutionCursor(path.plan, path.profile)
        awaitingObservation = false
        status = Status.Executing(0, path.plan.tape.frameCount)
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

    private fun fail(reason: String) {
        status = Status.Failed(reason)
        activeRequest = null
        cursor = null
        tickInput = null
        awaitingObservation = false
    }

    init {
        listenUnsafe<ConnectionEvent.Disconnect> { clear() }

        listen<TickEvent.Pre> {
            val active = cursor ?: return@listen
            val request = activeRequest ?: return@listen
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
                val result = active.observeAfterTick(observe(path.plan, frame + 1), revision)
                if (result is ExecutionObservationResult.Rejected) {
                    return@listen reject(result.frame, result.deviation)
                }
                awaitingObservation = false

                if (frame < path.plan.frames.size) {
                    val predicted = path.plan.frames[frame].state.position
                    synchronized(trail) { trail += player.pos }
                    maxDeviation = max(maxDeviation, player.pos.distanceTo(predicted))
                }

                if (result === ExecutionObservationResult.Complete) {
                    return@listen complete(path.plan.tape.frameCount)
                }
            }

            when (val next = active.nextInput(observe(path.plan, active.nextFrame), revision)) {
                is ExecutionInputResult.Apply -> {
                    tickInput = next.input
                    awaitingObservation = true
                    status = Status.Executing(next.frame, path.plan.tape.frameCount)

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

                ExecutionInputResult.Complete -> complete(path.plan.tape.frameCount)

                is ExecutionInputResult.Rejected -> reject(next.frame, next.deviation)
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

    private fun complete(frames: Int) {
        status = Status.Complete(frames)
        activeRequest = null
        cursor = null
        tickInput = null
        awaitingObservation = false
    }

    private fun reject(frame: Int, deviation: ExecutionDeviation) {
        fail("frame $frame: $deviation")
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

    /** The seed search caps yaw change at 30 deg/frame; the turn must clear that. */
    private const val MIN_TURN_SPEED = 30.0
}
