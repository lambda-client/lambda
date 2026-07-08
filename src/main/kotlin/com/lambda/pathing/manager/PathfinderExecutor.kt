/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.pathing.manager

import com.lambda.Lambda
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.core.Loadable
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.Rotation.Companion.rotationTo
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.pathing.execution.ExecutionPath
import com.lambda.pathing.execution.ExecutionSegment
import com.lambda.pathing.execution.PathExecutorDebugSample
import com.lambda.pathing.execution.PathExecutorDebugState
import com.lambda.pathing.execution.RecoveryMode
import com.lambda.pathing.execution.SegmentSelection
import com.lambda.pathing.execution.WalkSegment
import com.lambda.pathing.execution.projectWorldDeltaToLocalInput
import com.lambda.threading.runSafeAutomated
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.CommunicationUtils.warn
import com.lambda.util.player.MovementUtils.update
import com.lambda.util.world.FastVector
import net.minecraft.client.input.Input
import net.minecraft.util.math.Vec3d
import kotlin.math.hypot
import kotlin.math.max

object PathfinderExecutor : Loadable {

    private var activePath: ExecutionPath? = null
    private var activeTraversalId: Int? = null
    private var activeSourcePath: List<FastVector>? = null
    private var currentSegmentIndex = 0
    private var currentCommand: FollowCommand? = null
    private var debugState = PathExecutorDebugState()
    private val debugSamples = ArrayDeque<PathExecutorDebugSample>()
    private var lastSteeringTelemetry = SteeringTelemetry()
    private var lastLoggedSignature = ""
    private var lastLoggedTick = Int.MIN_VALUE

    private var lostTicks = 0
    private var skippedSegments = 0
    private var rewoundSegments = 0
    private var replansRequested = 0
    private var lastNotifiedTerminalId: Int? = null

    val state: PathExecutorDebugState
        get() = debugState

    val recentSamples: List<PathExecutorDebugSample>
        get() = debugSamples.toList()

    init {
        listen<TickEvent.Player.Post> {
            PathfinderManager.runSafeAutomated {
                updateExecutorState()
            }
        }

        listen<TickEvent.Pre> {
            val command = currentCommand ?: return@listen
            PathfinderManager.rotationRequest { yaw(command.desiredYaw) }.submit()
        }

        // Master's input hook: fires after Input.tick() and after RotationManager's
        // strafe redirect, so whatever we write here is exactly what the physics
        // step consumes — no later remap can stomp the analog steering values.
        listen<MovementEvent.InputUpdate> { event ->
            PathfinderManager.runSafeAutomated {
                applyFollowInput(event.input)
            }
        }

        listen<MovementEvent.Sprint> { event ->
            val command = currentCommand ?: return@listen
            event.sprint = command.sprint
        }
    }

    private fun AutomatedSafeContext.applyFollowInput(input: Input) {
        val command = currentCommand ?: return
        // The input written here feeds vanilla physics directly, and vanilla
        // moves the player relative to the live player.yaw — regardless of what
        // the rotation manager reports to the server. Projecting the steering
        // into that basis keeps the world-space motion correct in every
        // rotation mode, including Silent.
        val basisYaw = player.yaw.toDouble()
        val desiredDelta = command.lookaheadPoint.subtract(player.pos).flattenY()
        val steering = projectWorldDeltaToLocalInput(desiredDelta, basisYaw)
        val sprint = command.sprint && steering.forward > 0.05

        input.update(
            forward = steering.forward,
            strafe = steering.strafe,
            jump = command.jump && player.isOnGround,
            sneak = false,
            sprint = sprint,
        )
        if (command.throttle < 0.999) {
            input.movementVector = input.movementVector.multiply(command.throttle.toFloat())
        }

        lastSteeringTelemetry = SteeringTelemetry(
            desiredDelta = desiredDelta,
            movementBasisYaw = basisYaw,
            effectiveMovementYaw = RotationManager.movementYaw?.toDouble(),
            commandedForward = input.movementVector.y.toDouble(),
            commandedStrafe = input.movementVector.x.toDouble(),
            throttle = command.throttle,
            sprint = sprint,
        )

        debugState = debugState.copy(
            desiredDelta = desiredDelta,
            movementBasisYaw = basisYaw,
            effectiveMovementYaw = RotationManager.movementYaw?.toDouble(),
            playerYaw = player.yaw.toDouble(),
            desiredYaw = command.desiredYaw,
            throttle = command.throttle,
            commandedForward = input.movementVector.y.toDouble(),
            commandedStrafe = input.movementVector.x.toDouble(),
            sprintCommand = sprint,
        )
    }

    private fun AutomatedSafeContext.updateExecutorState() {
        val handle = PathfinderManager.activeTraversal
        notifyTerminalIfChanged(handle)

        if (!shouldFollow(handle)) {
            handleNotFollowing(handle)
            return
        }

        val activeHandle = handle ?: return
        val path = rebuildPathIfNeeded(activeHandle)
        if (path.isEmpty) {
            stopFollowing()
            val status = if (activeHandle.path.size <= 1) "AtGoal" else "NoSegments"
            debugState = baseDebugState(active = true, status = status, handle = activeHandle)
            maybeLogDebugState(player.age)
            return
        }

        val playerPos = player.pos
        val previousIndex = currentSegmentIndex
        while (currentSegmentIndex < path.lastSegmentIndex && path.segments[currentSegmentIndex].hasReached(playerPos, movementConfig.reachDistance, movementConfig.verticalTolerance)) {
            currentSegmentIndex++
        }

        val selection = path.locateSegment(
            position = playerPos,
            currentIndex = currentSegmentIndex,
            searchBehind = movementConfig.searchBehindSegments,
            searchAhead = movementConfig.searchAheadSegments,
            corridorRadius = movementConfig.corridorRadius,
            verticalTolerance = movementConfig.verticalTolerance,
            relocalizeDistance = movementConfig.relocalizeDistance,
            backtrackAllowance = movementConfig.backtrackAllowance,
            overshootAllowance = movementConfig.overshootAllowance,
        )

        if (selection == null) {
            handleLost(activeHandle, path)
            return
        }

        lostTicks = 0
        applyRecovery(selection, previousIndex)
        currentSegmentIndex = selection.index

        val segment = selection.segment
        val projectedPoint = segment.closestPoint(playerPos)
        val projectedDistance = segment.projectedDistance(playerPos).coerceIn(0.0, segment.horizontalLength)
        val verticalError = segment.verticalError(playerPos)

        if (!segment.supportedByController) {
            stopFollowing()
            debugState = baseDebugState(active = true, status = "UnsupportedSegment", handle = activeHandle).copy(
                segmentIndex = currentSegmentIndex,
                segmentCount = path.segments.size,
                segmentType = segment.typeName,
                recoveryMode = selection.recoveryMode,
                remainingDistance = selection.remainingDistance,
                segmentLength = segment.horizontalLength,
                projectedDistance = projectedDistance,
                lateralError = selection.lateralError,
                verticalError = verticalError,
                skippedSegments = skippedSegments,
                rewoundSegments = rewoundSegments,
                replansRequested = replansRequested,
                segmentStart = segment.startPose.position,
                segmentEnd = segment.endPose.position,
                projectedPoint = projectedPoint,
                supportedSegment = false,
            )
            pushDebugSample(debugState)
            maybeLogDebugState(player.age)
            return
        }

        val lookaheadPointRaw = path.lookaheadAcrossSegments(playerPos, currentSegmentIndex, movementConfig.lookaheadDistance)
        val lookaheadPoint = Vec3d(lookaheadPointRaw.x, playerPos.y, lookaheadPointRaw.z)
        val desiredDelta = lookaheadPoint.subtract(playerPos).flattenY()
        val desiredYaw = playerPos.rotationTo(lookaheadPoint).yaw
        val remainingDistance = segment.remainingDistance(playerPos)
        val isFinalSegment = currentSegmentIndex == path.lastSegmentIndex
        val throttle = if (isFinalSegment && movementConfig.finalApproachDistance > 0.0 && remainingDistance < movementConfig.finalApproachDistance) {
            max(movementConfig.minimumThrottle, remainingDistance / movementConfig.finalApproachDistance)
        } else {
            1.0
        }
        val pathRemaining = remainingPathLength(path, currentSegmentIndex, playerPos)
        val sprint = movementConfig.allowSprint && pathRemaining >= movementConfig.sprintMinRemaining
        val needsStepUpJump = needsStepUpJump(path, currentSegmentIndex, segment, playerPos)
        val command = FollowCommand(
            lookaheadPoint = lookaheadPoint,
            desiredYaw = desiredYaw,
            throttle = throttle,
            sprint = sprint,
            jump = needsStepUpJump,
        )
        currentCommand = command

        val basisYaw = player.yaw.toDouble()
        debugState = baseDebugState(active = true, status = "Following", handle = activeHandle).copy(
            segmentIndex = currentSegmentIndex,
            segmentCount = path.segments.size,
            segmentType = segment.typeName,
            recoveryMode = selection.recoveryMode,
            remainingDistance = remainingDistance,
            segmentLength = segment.horizontalLength,
            projectedDistance = projectedDistance,
            lateralError = selection.lateralError,
            verticalError = verticalError,
            lostTicks = lostTicks,
            skippedSegments = skippedSegments,
            rewoundSegments = rewoundSegments,
            replansRequested = replansRequested,
            lookaheadPoint = lookaheadPoint,
            projectedPoint = projectedPoint,
            segmentStart = segment.startPose.position,
            segmentEnd = segment.endPose.position,
            desiredDelta = desiredDelta,
            desiredYaw = desiredYaw,
            movementBasisYaw = basisYaw,
            throttle = throttle,
            commandedForward = lastSteeringTelemetry.commandedForward,
            commandedStrafe = lastSteeringTelemetry.commandedStrafe,
            sprintCommand = lastSteeringTelemetry.sprint || sprint,
            supportedSegment = true,
        )
        debugState = applySteeringTelemetry(debugState)
        pushDebugSample(debugState)
        maybeLogDebugState(player.age)
    }

    private fun SafeContext.baseDebugState(
        active: Boolean,
        status: String,
        handle: TraversalHandle? = null,
    ): PathExecutorDebugState {
        val moveDelta = currentMoveDelta()
        return PathExecutorDebugState(
            active = active,
            traversalId = handle?.id,
            status = status,
            playerPosition = player.pos,
            playerVelocity = player.velocity,
            moveDelta = moveDelta,
            playerYaw = player.yaw.toDouble(),
            effectiveMovementYaw = RotationManager.movementYaw?.toDouble(),
            horizontalSpeed = horizontalSpeed(player.velocity),
            movedLastTick = horizontalDistance(moveDelta),
        )
    }

    private fun stopFollowing() {
        currentCommand = null
        lastSteeringTelemetry = SteeringTelemetry()
    }

    private fun AutomatedSafeContext.handleNotFollowing(handle: TraversalHandle?) {
        if (handle == null || handle.status.isTerminal || handle.status == TraversalHandle.Status.Failed) {
            clearRuntimePath()
            resetTelemetry()
        } else {
            stopFollowing()
        }
        val status = when {
            handle != null && !movementConfig.enabled -> "ExecutorDisabled"
            handle?.status == null -> "Idle"
            handle.status == TraversalHandle.Status.Planning -> "WaitingForPath"
            handle.status == TraversalHandle.Status.Partial -> "PartialDisabled"
            handle.status == TraversalHandle.Status.Failed -> "TraversalFailed"
            handle.status == TraversalHandle.Status.Cancelled -> "TraversalCancelled"
            handle.status == TraversalHandle.Status.Ready -> "NoPath"
            else -> "Idle"
        }
        debugState = baseDebugState(active = false, status = status, handle = handle)
        maybeLogDebugState(player.age)
    }

    private fun AutomatedSafeContext.handleLost(activeHandle: TraversalHandle, path: ExecutionPath) {
        stopFollowing()
        lostTicks++
        if (lostTicks >= movementConfig.maxLostTicks) {
            with(PathfinderManager) { refreshActiveTraversal() }
            replansRequested++
            lostTicks = 0
        }
        debugState = baseDebugState(active = true, status = "Lost", handle = activeHandle).copy(
            segmentIndex = currentSegmentIndex,
            segmentCount = path.segments.size,
            lostTicks = lostTicks,
            skippedSegments = skippedSegments,
            rewoundSegments = rewoundSegments,
            replansRequested = replansRequested,
        )
        pushDebugSample(debugState)
        maybeLogDebugState(player.age)
    }

    private fun AutomatedSafeContext.shouldFollow(handle: TraversalHandle?): Boolean {
        if (handle == null) return false
        if (!movementConfig.enabled) return false
        return when (handle.status) {
            TraversalHandle.Status.Ready -> handle.path.size >= 2
            TraversalHandle.Status.Partial -> movementConfig.followPartialPaths && handle.path.size >= 2
            TraversalHandle.Status.Planning,
            TraversalHandle.Status.Failed,
            TraversalHandle.Status.Cancelled -> false
        }
    }

    private fun rebuildPathIfNeeded(handle: TraversalHandle): ExecutionPath {
        val existing = activePath
        val sourcePath = handle.path
        if (existing != null && activeTraversalId == handle.id && activeSourcePath === sourcePath) {
            return existing
        }

        val traversalChanged = activeTraversalId != null && activeTraversalId != handle.id
        val rebuilt = ExecutionPath.fromNodes(handle.id, sourcePath)
        activePath = rebuilt
        activeTraversalId = handle.id
        activeSourcePath = sourcePath
        currentSegmentIndex = 0
        currentCommand = null
        lastSteeringTelemetry = SteeringTelemetry()
        if (traversalChanged) resetTelemetry()
        return rebuilt
    }

    private fun applyRecovery(selection: SegmentSelection, previousIndex: Int) {
        when (selection.recoveryMode) {
            RecoveryMode.SkippedForward -> skippedSegments += selection.index - previousIndex
            RecoveryMode.Rewound -> rewoundSegments += previousIndex - selection.index
            RecoveryMode.KeptCurrent,
            RecoveryMode.Relocalized -> Unit
        }
    }

    private fun clearRuntimePath() {
        activePath = null
        activeTraversalId = null
        activeSourcePath = null
        currentSegmentIndex = 0
        currentCommand = null
        debugSamples.clear()
        lastSteeringTelemetry = SteeringTelemetry()
        lastLoggedSignature = ""
        lastLoggedTick = Int.MIN_VALUE
    }

    private fun resetTelemetry() {
        lostTicks = 0
        skippedSegments = 0
        rewoundSegments = 0
        replansRequested = 0
    }

    private fun applySteeringTelemetry(state: PathExecutorDebugState): PathExecutorDebugState = state.copy(
        desiredDelta = lastSteeringTelemetry.desiredDelta ?: state.desiredDelta,
        movementBasisYaw = lastSteeringTelemetry.movementBasisYaw ?: state.movementBasisYaw,
        effectiveMovementYaw = lastSteeringTelemetry.effectiveMovementYaw ?: state.effectiveMovementYaw,
        commandedForward = lastSteeringTelemetry.commandedForward,
        commandedStrafe = lastSteeringTelemetry.commandedStrafe,
        throttle = lastSteeringTelemetry.throttle,
        sprintCommand = lastSteeringTelemetry.sprint || state.sprintCommand,
    )

    private fun AutomatedSafeContext.pushDebugSample(state: PathExecutorDebugState) {
        val playerPosition = state.playerPosition ?: return
        debugSamples += PathExecutorDebugSample(
            status = state.status,
            segmentIndex = state.segmentIndex,
            segmentType = state.segmentType,
            playerPosition = playerPosition,
            projectedPoint = state.projectedPoint,
            lookaheadPoint = state.lookaheadPoint,
            playerVelocity = state.playerVelocity ?: Vec3d.ZERO,
            moveDelta = state.moveDelta ?: Vec3d.ZERO,
            desiredDelta = state.desiredDelta,
            lateralError = state.lateralError,
            verticalError = state.verticalError,
            remainingDistance = state.remainingDistance,
            throttle = state.throttle,
            commandedForward = state.commandedForward,
            commandedStrafe = state.commandedStrafe,
        )
        while (debugSamples.size > movementConfig.maxDebugSamples) {
            debugSamples.removeFirst()
        }
    }

    private fun AutomatedSafeContext.maybeLogDebugState(playerAge: Int) {
        if (!movementConfig.logExecutionDebug) return

        val state = debugState
        val signature = listOf(
            state.status,
            state.segmentIndex,
            state.segmentType,
            state.recoveryMode,
        ).joinToString("|")
        val statusChanged = signature != lastLoggedSignature
        val intervalElapsed = playerAge - lastLoggedTick >= movementConfig.logDebugInterval
        if (!statusChanged && !intervalElapsed) return

        lastLoggedSignature = signature
        lastLoggedTick = playerAge
        Lambda.LOG.info(buildLogSummary(state))
    }

    private fun buildLogSummary(state: PathExecutorDebugState): String = buildString {
        appendLine("[PathfinderExecutor] ${state.status} traversal=${state.traversalId ?: "-"}")
        appendLine("  segment=${if (state.segmentIndex >= 0) state.segmentIndex + 1 else 0}/${state.segmentCount} ${state.segmentType ?: "?"} recovery=${state.recoveryMode ?: "-"} supported=${state.supportedSegment}")
        appendLine("  progress=${"%.2f".format(state.projectedDistance)}/${"%.2f".format(state.segmentLength)} left=${"%.2f".format(state.remainingDistance)} drift=${"%.2f".format(state.lateralError)} vertical=${"%.2f".format(state.verticalError)}")
        appendLine("  yaw player=${state.playerYaw?.formatAngle() ?: "-"} desired=${state.desiredYaw?.formatAngle() ?: "-"} basis=${state.movementBasisYaw?.formatAngle() ?: "-"} effective=${state.effectiveMovementYaw?.formatAngle() ?: "-"}")
        appendLine("  input forward=${"%.2f".format(state.commandedForward)} strafe=${"%.2f".format(state.commandedStrafe)} throttle=${"%.2f".format(state.throttle)} sprint=${state.sprintCommand}")
        append("  motion speed=${"%.2f".format(state.horizontalSpeed)} moved=${"%.2f".format(state.movedLastTick)} lost=${state.lostTicks} skip=${state.skippedSegments} rewind=${state.rewoundSegments} replan=${state.replansRequested}")
    }

    private fun SafeContext.currentMoveDelta(): Vec3d = Vec3d(player.x - player.lastX, player.y - player.lastY, player.z - player.lastZ)

    private fun horizontalSpeed(velocity: Vec3d): Double = hypot(velocity.x, velocity.z)

    private fun horizontalDistance(delta: Vec3d): Double = hypot(delta.x, delta.z)

    private fun Double.formatAngle(): String = "%.1f".format(this)

    private fun Vec3d.flattenY() = Vec3d(x, 0.0, z)

    private fun remainingPathLength(
        path: ExecutionPath,
        currentSegmentIndex: Int,
        playerPos: Vec3d,
    ): Double {
        if (currentSegmentIndex !in 0..path.lastSegmentIndex) return 0.0
        var total = path.segments[currentSegmentIndex].remainingDistance(playerPos)
        for (i in (currentSegmentIndex + 1)..path.lastSegmentIndex) {
            total += path.segments[i].horizontalLength
        }
        return total
    }

    private fun notifyTerminalIfChanged(handle: TraversalHandle?) {
        if (handle == null) {
            lastNotifiedTerminalId = null
            return
        }
        val isTerminalish = handle.status == TraversalHandle.Status.Failed ||
            handle.status == TraversalHandle.Status.Cancelled
        if (!isTerminalish) {
            if (lastNotifiedTerminalId == handle.id) lastNotifiedTerminalId = null
            return
        }
        if (lastNotifiedTerminalId == handle.id) return
        lastNotifiedTerminalId = handle.id

        when (handle.status) {
            TraversalHandle.Status.Failed -> {
                val reason = handle.failureReason ?: "unknown reason"
                this@PathfinderExecutor.warn("Pathfinder stopped: $reason. Re-enable to retry.")
            }
            TraversalHandle.Status.Cancelled -> {
                this@PathfinderExecutor.info("Pathfinder traversal cancelled.")
            }
            else -> Unit
        }
    }

    private fun SafeContext.needsStepUpJump(
        path: ExecutionPath,
        currentSegmentIndex: Int,
        currentSegment: ExecutionSegment,
        playerPos: Vec3d,
    ): Boolean {
        if (!player.isOnGround) return false

        val current = currentSegment as? WalkSegment

        if (current != null && current.verticalStep > 0 && playerPos.y < current.endPose.position.y - 0.1) {
            return true
        }

        if (current != null && !current.requiresVerticalMotion) {
            val next = path.segments.getOrNull(currentSegmentIndex + 1) as? WalkSegment ?: return false
            if (next.verticalStep > 0 && current.remainingDistance(playerPos) <= 0.6) {
                return true
            }
        }
        return false
    }

    private data class FollowCommand(
        val lookaheadPoint: Vec3d,
        val desiredYaw: Double,
        val throttle: Double,
        val sprint: Boolean,
        val jump: Boolean = false,
    )

    private data class SteeringTelemetry(
        val desiredDelta: Vec3d? = null,
        val movementBasisYaw: Double? = null,
        val effectiveMovementYaw: Double? = null,
        val commandedForward: Double = 0.0,
        val commandedStrafe: Double = 0.0,
        val throttle: Double = 1.0,
        val sprint: Boolean = false,
    )
}
