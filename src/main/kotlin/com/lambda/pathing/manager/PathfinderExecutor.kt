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
import com.lambda.pathing.movement.WalkingMovementModel
import com.lambda.threading.runSafeAutomated
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.CommunicationUtils.warn
import com.lambda.util.player.MovementUtils.update
import com.lambda.util.world.FastVector
import com.lambda.util.world.toBlockPos
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
    private var jumpAttemptActive = false
    private var jumpHoldUntilTick = Int.MIN_VALUE
    private var jumpRetryAllowedTick = Int.MIN_VALUE
    private var lastJumpCommandGate = ""
    private var lastJumpInputGate = ""
    private var stuckTraversalId = Int.MIN_VALUE
    private var stuckSegmentIndex = Int.MIN_VALUE
    private var stuckBestRemaining = Double.MAX_VALUE
    private var stuckTicks = 0

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
        // Entity.updateVelocity is mixed to use RotationManager.movementYaw when
        // a non-silent rotation request is active, and player.yaw otherwise.
        // Project into that exact physics basis so mouse/camera yaw changes do
        // not perturb path-following while Sync/Lock rotation is split.
        val basisYaw = movementPhysicsBasisYaw()
        val desiredDelta = command.lookaheadPoint.subtract(player.pos).flattenY()
        val steering = projectWorldDeltaToLocalInput(desiredDelta, basisYaw)
        val sprint = command.sprint && steering.forward > 0.05
        val jump = shouldIssueJumpInput(command)

        input.update(
            forward = steering.forward,
            strafe = steering.strafe,
            jump = jump,
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
            jumpCommand = jump,
            jumpCommandGate = lastJumpCommandGate,
            jumpInputGate = lastJumpInputGate,
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
        val finalSegmentReached = currentSegmentIndex == path.lastSegmentIndex &&
            segment.hasReached(playerPos, movementConfig.reachDistance, movementConfig.verticalTolerance)

        if (finalSegmentReached) {
            stopFollowing()
            PathfinderManager.completeActiveTraversal()
            debugState = baseDebugState(active = false, status = "AtGoal", handle = activeHandle).copy(
                segmentIndex = currentSegmentIndex,
                segmentCount = path.segments.size,
                segmentType = segment.typeName,
                recoveryMode = selection.recoveryMode,
                remainingDistance = 0.0,
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
                supportedSegment = segment.supportedByController,
            )
            pushDebugSample(debugState)
            maybeLogDebugState(player.age)
            return
        }

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
        trackExecutionProgress(activeHandle, remainingDistance, playerPos)
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

        val basisYaw = movementPhysicsBasisYaw()
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
            jumpCommandGate = lastJumpCommandGate,
            jumpInputGate = lastJumpInputGate,
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
        stuckTraversalId = Int.MIN_VALUE
        stuckSegmentIndex = Int.MIN_VALUE
        stuckBestRemaining = Double.MAX_VALUE
        stuckTicks = 0
    }

    /**
     * Execution-feedback loop (research plan §4.6): if the player makes no
     * progress on the active segment for a sustained window while grounded,
     * the world disagrees with the plan — report the coarse edge under the
     * player as obstructed so the planner reroutes, instead of pushing into
     * a wall forever.
     */
    private fun AutomatedSafeContext.trackExecutionProgress(
        handle: TraversalHandle,
        remainingDistance: Double,
        playerPos: Vec3d,
    ) {
        if (handle.id != stuckTraversalId || currentSegmentIndex != stuckSegmentIndex ||
            remainingDistance < stuckBestRemaining - STUCK_PROGRESS_EPSILON
        ) {
            stuckTraversalId = handle.id
            stuckSegmentIndex = currentSegmentIndex
            stuckBestRemaining = remainingDistance
            stuckTicks = 0
            return
        }

        if (!player.isOnGround) return
        stuckTicks++
        if (stuckTicks < STUCK_REPORT_TICKS) return
        stuckTicks = 0
        stuckBestRemaining = Double.MAX_VALUE

        val coarse = handle.coarsePath
        if (coarse.size < 2) return
        val playerBlock = player.blockPos
        var nearest = 0
        var nearestDistance = Double.MAX_VALUE
        coarse.forEachIndexed { index, node ->
            val b = node.toBlockPos()
            val dx = (b.x - playerBlock.x).toDouble()
            val dy = (b.y - playerBlock.y).toDouble()
            val dz = (b.z - playerBlock.z).toDouble()
            val distance = dx * dx + dy * dy + dz * dz
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearest = index
            }
        }
        if (nearest >= coarse.size - 1) return

        replansRequested++
        with(PathfinderManager) { reportEdgeObstructed(coarse[nearest], coarse[nearest + 1]) }
    }

    private fun SafeContext.movementPhysicsBasisYaw(): Double =
        RotationManager.movementYaw?.toDouble() ?: player.yaw.toDouble()

    private fun AutomatedSafeContext.shouldIssueJumpInput(command: FollowCommand): Boolean {
        if (!command.jump) {
            jumpAttemptActive = false
            lastJumpInputGate = "noCommand"
            return false
        }

        if (!player.isOnGround) {
            // The previous request actually got us airborne. Do not keep the
            // key latched while in the air, and do not immediately re-fire on
            // the first landing tick if the same step-up command is still true.
            jumpAttemptActive = false
            jumpRetryAllowedTick = player.age + JUMP_RETRY_COOLDOWN_TICKS
            lastJumpInputGate = "airborne"
            return false
        }

        if (player.age < jumpRetryAllowedTick) {
            lastJumpInputGate = "cooldown(${jumpRetryAllowedTick - player.age})"
            return false
        }

        if (!jumpAttemptActive) {
            jumpAttemptActive = true
            jumpHoldUntilTick = player.age + JUMP_HOLD_TICKS
        }

        if (player.age <= jumpHoldUntilTick) {
            lastJumpInputGate = "issued"
            return true
        }

        // If the held jump did not make the player leave the ground, back off
        // before trying again. This keeps failed step-up jumps from becoming a
        // creative-mode double-tap spam loop, while still holding long enough
        // for vanilla/survival jumps to be consumed reliably.
        jumpAttemptActive = false
        jumpRetryAllowedTick = player.age + JUMP_RETRY_COOLDOWN_TICKS
        lastJumpInputGate = "backoff"
        return false
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
            handle.status == TraversalHandle.Status.Succeeded -> "TraversalSucceeded"
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
            TraversalHandle.Status.Succeeded,
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
        resetJumpState()
    }

    private fun resetTelemetry() {
        lostTicks = 0
        skippedSegments = 0
        rewoundSegments = 0
        replansRequested = 0
        resetJumpState()
    }

    private fun resetJumpState() {
        jumpAttemptActive = false
        jumpHoldUntilTick = Int.MIN_VALUE
        jumpRetryAllowedTick = Int.MIN_VALUE
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
            handle.status == TraversalHandle.Status.Succeeded ||
            handle.status == TraversalHandle.Status.Cancelled
        if (!isTerminalish) {
            if (lastNotifiedTerminalId == handle.id) lastNotifiedTerminalId = null
            return
        }
        if (lastNotifiedTerminalId == handle.id) return
        lastNotifiedTerminalId = handle.id

        when (handle.status) {
            TraversalHandle.Status.Succeeded -> {
                this@PathfinderExecutor.info("Pathfinder traversal complete.")
            }
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
        if (!player.isOnGround) {
            lastJumpCommandGate = "airborne"
            return false
        }
        val current = currentSegment as? WalkSegment ?: run {
            lastJumpCommandGate = "notWalk"
            return false
        }

        // Resolve which rise we are approaching: either the current segment
        // itself or, in anticipation, the next one.
        val rise = when {
            current.verticalStep > 0 -> current
            !current.requiresVerticalMotion ->
                (path.segments.getOrNull(currentSegmentIndex + 1) as? WalkSegment)
                    ?.takeIf { it.verticalStep > 0 && current.remainingDistance(playerPos) <= JUMP_ANTICIPATION_DISTANCE }
                    ?: run {
                        lastJumpCommandGate = "noRiseAhead"
                        return false
                    }
            else -> {
                lastJumpCommandGate = "verticalCurrent"
                return false
            }
        }
        if (playerPos.y >= rise.endPose.position.y - 0.1) {
            lastJumpCommandGate = "alreadyUp"
            return false
        }

        // Proximity gate: a jump issued too far from the rise completes its
        // arc before reaching the ledge and is a guaranteed failure. Only jump
        // once the landing block is within the arc's horizontal reach.
        val end = rise.endPose.position
        val horizontalToEnd = hypot(end.x - playerPos.x, end.z - playerPos.z)
        if (horizontalToEnd > JUMP_TRIGGER_DISTANCE) {
            lastJumpCommandGate = "tooFar(%.2f)".format(horizontalToEnd)
            return false
        }

        // Headroom gate: plan-time clearance was checked at node centers, but
        // we are jumping from between nodes — a ceiling here (tunnel stairs,
        // tree canopy) bonks the head, kills the arc, and turns the retry loop
        // into a creative-flight double-tap. Walking closer first is always
        // valid: the validated node itself has clearance.
        if (!with(WalkingMovementModel) { hasJumpApexClearance(playerPos) }) {
            lastJumpCommandGate = "noHeadroom"
            return false
        }

        lastJumpCommandGate = "commanded"
        return true
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

    private const val JUMP_HOLD_TICKS = 4
    private const val JUMP_RETRY_COOLDOWN_TICKS = 8

    // How close (horizontally, in blocks) the rise segment's landing block
    // must be before a jump is issued. A walk-speed jump covers ~1.2-2 blocks
    // of air time; issuing beyond this lands the arc short of the ledge.
    private const val JUMP_TRIGGER_DISTANCE = 1.05

    // How early (remaining distance on the flat segment) the executor may
    // anticipate the next rise segment's jump.
    private const val JUMP_ANTICIPATION_DISTANCE = 0.6

    // Stuck detection: how long the player may sit on a segment without net
    // progress before the edge is reported as obstructed to the planner.
    private const val STUCK_REPORT_TICKS = 40
    private const val STUCK_PROGRESS_EPSILON = 0.02
}
