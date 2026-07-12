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
import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.interaction.managers.rotating.Rotation.Companion.rotationTo
import com.lambda.interaction.managers.rotating.RotationManager
import com.lambda.pathing.execution.ChainSegment
import com.lambda.pathing.execution.ExecutionPath
import com.lambda.pathing.execution.ExecutionSegment
import com.lambda.pathing.maneuver.ManeuverPolicy
import com.lambda.pathing.execution.PathExecutorDebugSample
import com.lambda.pathing.execution.PathExecutorDebugState
import com.lambda.pathing.execution.PlannedArc
import com.lambda.pathing.execution.RecoveryMode
import com.lambda.pathing.execution.SegmentSelection
import com.lambda.pathing.execution.SteeringInput
import com.lambda.pathing.execution.WalkSegment
import com.lambda.pathing.execution.projectWorldDeltaToLocalInput
import com.lambda.pathing.movement.WalkingMovementModel
import com.lambda.threading.runSafeAutomated
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.CommunicationUtils.warn
import com.lambda.util.math.flooredBlockPos
import com.lambda.util.player.MovementUtils.update
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.MovementSimulator
import com.lambda.util.world.FastVector
import com.lambda.util.world.toBlockPos
import net.minecraft.client.input.Input
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

object PathfinderExecutor : Loadable {

    private var activePath: ExecutionPath? = null
    private var activeTraversalId: Int? = null
    private var activeSourcePath: List<FastVector>? = null
    private var currentSegmentIndex = 0
    /** A replaced refined-path object needs one full-path localization pass. */
    private var pathNeedsRelocalization = false
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
    private var stepUpRetrySegment = -1
    private var stepUpRetryCount = 0

    // Sprint the CURRENT follow command applies — what the launch sims must
    // model; player.isSprinting lags it by at least a tick.
    private var commandSprint = false

    // Set when the launch sim proved the jump only lands WITHOUT the
    // sprint boost (short ascend hops): the command drops sprint for the
    // launch tick, exactly the walk-speed hop a human would do.
    private var launchSprintSuppressed = false
    private var lastJumpCommandGate = ""
    private var lastJumpInputGate = ""
    private var lastIssuedJumpTarget: Vec3d? = null
    private var stuckTraversalId = Int.MIN_VALUE
    private var stuckSegmentIndex = Int.MIN_VALUE
    private var stuckBestRemaining = Double.MAX_VALUE
    private var stuckTicks = 0
    private var chainTraversalId = Int.MIN_VALUE
    private var chainSegmentIndex = Int.MIN_VALUE
    private var chainTargetIndex = 0
    private var wallCollisionTicks = 0
    private var headBonkTicks = 0
    private var plannedArcsCache: List<PlannedArc> = emptyList()
    private var plannedArcsPath: ExecutionPath? = null
    private var newJumpIssued = false
    private var launchArcPoints: List<Vec3d> = emptyList()
    private var launchArcIndex = -1
    private var lastArcTickError: Double? = null
    private var lastPlanArcError: Double? = null
    /** Set when the launch sim vetoed a jump this tick: shed speed and retry. */
    private var launchSimHold = false

    /** The launch sim predicted an undershoot: keep building speed toward
     *  the takeoff instead of walk-back/align — shedding on a short arc
     *  guarantees it stays short. Reset every follow tick. */
    private var launchSimBuild = false

    /** Past the point of no return with no jump headroom: hard-stop this
     *  tick rather than carry off the edge. Reset every follow tick. */
    private var launchEdgeStop = false

    /** The jump this segment needs is blocked by GEOMETRY (no apex
     *  headroom) — a state no amount of waiting or speed-tuning resolves.
     *  Accelerates the stuck-report clock. Reset every follow tick. */
    private var structuralJumpBlock = false

    val state: PathExecutorDebugState
        get() = debugState

    val recentSamples: List<PathExecutorDebugSample>
        get() = debugSamples.toList()

    /** Predicted maneuver flight paths of the active path, for rendering. */
    val plannedArcs: List<PlannedArc>
        get() = plannedArcsCache

    /** Launch-time flight prediction of the jump currently in the air. */
    val activeLaunchArc: List<Vec3d>
        get() = if (launchArcIndex >= 0) launchArcPoints else emptyList()

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
        val nominalSteering = projectWorldDeltaToLocalInput(desiredDelta, basisYaw)
        // Receding-horizon air control: launch validation can only certify the
        // state at takeoff. Once airborne, latency/collisions may perturb the
        // velocity, so choose this tick's input by forward-simulating a small
        // set of corrections from the LIVE state. The next tick replans from
        // the resulting state, allowing lateral correction and acceleration/
        // braking rather than blindly replaying one fixed arc.
        val airborneCorrection = if (!player.isOnGround && command.jumpTarget != null) {
            optimizeAirborneInput(
                target = command.jumpTarget,
                basisYaw = basisYaw,
                sprint = command.sprint,
                brakeLead = command.jumpBrakeLead,
                nominal = nominalSteering,
            )
        } else null
        val steering = airborneCorrection ?: nominalSteering
        val sprint = command.sprint && steering.forward > 0.05
        val jump = shouldIssueJumpInput(command)
        lastIssuedJumpTarget = if (jump) command.jumpTarget else null
        if (jump && newJumpIssued) {
            newJumpIssued = false
            predictLaunchArc(command, sprint)
        }

        input.update(
            forward = steering.forward,
            strafe = steering.strafe,
            jump = jump,
            sneak = false,
            sprint = sprint,
        )
        val appliedThrottle = if (airborneCorrection != null) 1.0 else command.throttle
        if (appliedThrottle < 0.999) {
            input.movementVector = input.movementVector.multiply(appliedThrottle.toFloat())
        }

        lastSteeringTelemetry = SteeringTelemetry(
            desiredDelta = desiredDelta,
            movementBasisYaw = basisYaw,
            effectiveMovementYaw = RotationManager.movementYaw?.toDouble(),
            commandedForward = input.movementVector.y.toDouble(),
            commandedStrafe = input.movementVector.x.toDouble(),
            throttle = appliedThrottle,
            sprint = sprint,
        )

        debugState = debugState.copy(
            desiredDelta = desiredDelta,
            movementBasisYaw = basisYaw,
            effectiveMovementYaw = RotationManager.movementYaw?.toDouble(),
            playerYaw = player.yaw.toDouble(),
            desiredYaw = command.desiredYaw,
            throttle = appliedThrottle,
            commandedForward = input.movementVector.y.toDouble(),
            commandedStrafe = input.movementVector.x.toDouble(),
            sprintCommand = sprint,
            jumpCommand = jump,
            jumpCommandGate = lastJumpCommandGate,
            jumpInputGate = lastJumpInputGate,
        )
    }

    /**
     * One-step model-predictive controller for an active jump. Each candidate
     * is applied for the next tick; later simulated ticks steer at the target
     * and use the maneuver brake policy. Lowest predicted touchdown error
     * wins. This runs on the client input thread, where live-world simulation
     * is safe, and is deliberately bounded to a tiny candidate set/horizon.
     */
    private fun SafeContext.optimizeAirborneInput(
        target: Vec3d,
        basisYaw: Double,
        sprint: Boolean,
        brakeLead: Double?,
        nominal: SteeringInput,
    ): SteeringInput {
        fun normalized(forward: Double, strafe: Double): SteeringInput {
            val magnitude = hypot(forward, strafe)
            return if (magnitude <= 1.0) SteeringInput(forward, strafe)
            else SteeringInput(forward / magnitude, strafe / magnitude)
        }

        val candidates = listOf(
            nominal,
            normalized(nominal.forward * 0.55, nominal.strafe * 0.55),
            SteeringInput.ZERO,
            normalized(nominal.forward, nominal.strafe + AIR_MPC_LATERAL_INPUT),
            normalized(nominal.forward, nominal.strafe - AIR_MPC_LATERAL_INPUT),
            SteeringInput(0.0, 1.0),
            SteeringInput(0.0, -1.0),
            normalized(-nominal.forward * AIR_MPC_REVERSE_INPUT, -nominal.strafe * AIR_MPC_REVERSE_INPUT),
        ).distinct()

        val rotation = Rotation(basisYaw, player.pitch.toDouble())
        var best = nominal
        var bestScore = Double.POSITIVE_INFINITY
        var bestTrajectory: List<Vec3d> = emptyList()
        for (candidate in candidates) {
            val simulator = MovementSimulator(
                player = player,
                initialState = MovementSimulationState.at(
                    player = player,
                    position = player.pos,
                    rotation = rotation,
                    velocity = player.velocity,
                    onGround = false,
                    isSprinting = sprint,
                ),
            ).also { it.skipEntityCollisions = true }

            var closest = hypot(target.x - player.x, target.z - player.z)
            var score = Double.POSITIVE_INFINITY
            val trajectory = ArrayList<Vec3d>(AIR_MPC_MAX_TICKS)
            for (tick in 0 until AIR_MPC_MAX_TICKS) {
                val before = simulator.lastTick
                val control = if (tick == 0) candidate else {
                    val distance = hypot(target.x - before.position.x, target.z - before.position.z)
                    val speed = hypot(before.velocity.x, before.velocity.z)
                    if (brakeLead != null && ManeuverPolicy.shouldBrake(distance, speed, brakeLead)) {
                        SteeringInput.ZERO
                    } else {
                        projectWorldDeltaToLocalInput(target.subtract(before.position).flattenY(), basisYaw)
                    }
                }
                val current = simulator.tickMovement(
                    MovementSimulationInput(
                        forward = control.forward,
                        strafe = control.strafe,
                        jump = false,
                        sneak = false,
                        sprint = sprint,
                        useItemSlowdown = false,
                        rotation = rotation,
                    )
                )
                trajectory += current.position
                val horizontalError = hypot(target.x - current.position.x, target.z - current.position.z)
                closest = minOf(closest, horizontalError)
                if (current.simulator.state.horizontalCollision) {
                    score = AIR_MPC_COLLISION_PENALTY + horizontalError
                    break
                }
                if (current.onGround) {
                    score = horizontalError + abs(target.y - current.position.y) * AIR_MPC_VERTICAL_PENALTY
                    break
                }
                if (current.position.y < target.y - 0.5) {
                    score = AIR_MPC_FALL_PENALTY + horizontalError
                    break
                }
            }
            if (!score.isFinite()) score = AIR_MPC_NO_LANDING_PENALTY + closest
            // Preserve the nominal policy on numerical ties; correction is
            // only worthwhile when the predicted landing actually improves.
            if (score + AIR_MPC_MIN_IMPROVEMENT < bestScore) {
                bestScore = score
                best = candidate
                bestTrajectory = trajectory
            }
        }
        // The white committed-trajectory renderer follows the controller's
        // current prediction, not the now-obsolete launch-time open-loop arc.
        if (bestTrajectory.isNotEmpty()) {
            launchArcPoints = listOf(player.pos) + bestTrajectory
            launchArcIndex = 0
        }
        return best
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
        refreshPlannedArcs(path)
        if (path.isEmpty) {
            stopFollowing()
            val status = if (activeHandle.path.size <= 1) "AtGoal" else "NoSegments"
            debugState = baseDebugState(active = true, status = status, handle = activeHandle)
            maybeLogDebugState(player.age)
            return
        }

        val playerPos = player.pos
        trackTrajectoryMatch(playerPos)

        // A jump arc tops out ~1.25 blocks above the segment line — well past
        // the grounded vertical tolerance. Without airborne slack the executor
        // goes Lost at every apex, stopFollowing() zeroes the inputs, and the
        // arc flies ballistic on takeoff momentum alone (2.2 blocks instead of
        // 3.5+) — the H6 telemetry signature of gap jumps landing short.
        // Goal arrival below stays on the strict tolerance.
        val trackingVerticalTolerance = if (player.isOnGround) {
            movementConfig.verticalTolerance
        } else {
            max(movementConfig.verticalTolerance, AIRBORNE_VERTICAL_TOLERANCE)
        }

        if (!pathNeedsRelocalization) {
            while (currentSegmentIndex < path.lastSegmentIndex && path.segments[currentSegmentIndex].hasReached(playerPos, movementConfig.reachDistance, trackingVerticalTolerance)) {
                currentSegmentIndex++
            }
        }

        val localizationIndex = currentSegmentIndex.takeUnless { pathNeedsRelocalization }
        val selection = path.locateSegment(
            position = playerPos,
            currentIndex = localizationIndex,
            searchBehind = movementConfig.searchBehindSegments,
            searchAhead = movementConfig.searchAheadSegments,
            corridorRadius = movementConfig.corridorRadius,
            verticalTolerance = trackingVerticalTolerance,
            relocalizeDistance = movementConfig.relocalizeDistance,
            backtrackAllowance = movementConfig.backtrackAllowance,
            overshootAllowance = movementConfig.overshootAllowance,
        )

        if (selection == null) {
            handleLost(activeHandle, path)
            return
        }

        lostTicks = 0
        applyRecovery(selection, localizationIndex)
        currentSegmentIndex = selection.index
        pathNeedsRelocalization = false

        // Contact quality while following: wall scrapes (horizontal) and
        // airborne head bonks (vertical, off-ground) are the "sloppy motion"
        // signals — a clean run keeps both near zero.
        if (player.horizontalCollision) wallCollisionTicks++
        if (player.verticalCollision && !player.isOnGround) headBonkTicks++

        val segment = selection.segment
        val projectedPoint = segment.closestPoint(playerPos)
        val projectedDistance = segment.projectedDistance(playerPos).coerceIn(0.0, segment.horizontalLength)
        val verticalError = segment.verticalError(playerPos)
        // Arrival means *standing* on the final node, not passing through its
        // tolerance sphere at speed: position + grounded + braked. Downstream
        // consumers (stance-precise tasks) and the bench oracle rely on it.
        // The path must actually END at the goal: with async planning the
        // executor can reach the end of a *partial* path while the worker is
        // still extending it — completing there succeeded a traversal 95
        // blocks from the goal (observed). The sync planner masked this by
        // republishing a longer path every tick.
        val pathEndsAtGoal = activeHandle.status == TraversalHandle.Status.Ready &&
            activeHandle.path.lastOrNull() == activeHandle.goal.targetNode
        val finalSegmentReached = pathEndsAtGoal &&
            currentSegmentIndex == path.lastSegmentIndex &&
            segment.hasReached(playerPos, movementConfig.reachDistance, movementConfig.verticalTolerance) &&
            player.isOnGround &&
            horizontalSpeed(player.velocity) <= movementConfig.goalStopSpeed

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

        val remainingDistance = segment.remainingDistance(playerPos)
        trackExecutionProgress(activeHandle, remainingDistance, playerPos)
        val isFinalSegment = currentSegmentIndex == path.lastSegmentIndex
        var throttle = if (isFinalSegment && movementConfig.finalApproachDistance > 0.0 && remainingDistance < movementConfig.finalApproachDistance) {
            max(movementConfig.minimumThrottle, remainingDistance / movementConfig.finalApproachDistance)
        } else {
            1.0
        }
        val pathRemaining = remainingPathLength(path, currentSegmentIndex, playerPos)
        // Sprint discipline (Baritone's field rules): never sprint into a
        // short step-up — the extra speed reaches the riser face before the
        // arc has gained a block of height (the chest-bonk failure) — and
        // never sprint short *flat* gap jumps: a walk arc covers ~2.5
        // blocks, plenty for a 1-gap, while a sprint arc overshoots the
        // validated landing. Rising gap jumps ([riseWithin] skips them) and
        // discovered long jumps were validated AT sprint speed and require
        // it — a walk-speed launch lands them in the hole.
        val chainSegment = segment as? ChainSegment
        if (chainSegment == null) resetChainState()
        val longGapActive = chainSegment == null && isLongGapSegment(segment)
        // The launch sims must model the sprint state THIS COMMAND applies,
        // not the entity's latched flag: at a rise the command cuts sprint
        // while player.isSprinting still reads true, and simulating the
        // stale flag fires a +0.2 boost the real jump won't have — every
        // boostless rise launch then reads as a 2-block overshoot (NoGo).
        commandSprint = movementConfig.allowSprint && (
            chainSegment != null || longGapActive || (
                pathRemaining >= movementConfig.sprintMinRemaining &&
                    !riseWithin(path, currentSegmentIndex, playerPos, RISE_SPRINT_CUT_DISTANCE) &&
                    !shortFlatGapWithin(path, currentSegmentIndex, playerPos, GAP_SPRINT_CUT_DISTANCE)
                )
            )
        // Keep the same chain waypoint until it has actually been touched on
        // the ground. Selecting by projected progress switched to the next
        // pad while still flying over the current one, re-applied forward
        // input, and produced exactly the observed overshoot/fall/replan.
        // The discovery simulator is stateful in the same way, so this also
        // restores the validator/executor single-policy invariant.
        val chainTarget = chainSegment?.let {
            activeTraversalId?.let { traversalId -> activeChainTarget(traversalId, it, playerPos) }
                ?: it.waypoints.firstOrNull()
                ?: it.endPose.position
        }
        launchSimHold = false
        launchSimBuild = false
        launchSprintSuppressed = false
        launchEdgeStop = false
        structuralJumpBlock = false
        val needsChainJump = chainSegment != null && needsChainJump(chainSegment, playerPos)
        val stepUpTarget = if (chainSegment == null) {
            stepUpJumpTarget(path, currentSegmentIndex, segment, playerPos)
        } else null
        val needsStepUpJump = stepUpTarget != null
        if (stepUpTarget != null) {
            if (stepUpRetrySegment == stepUpTarget.index) stepUpRetryCount++ else {
                stepUpRetrySegment = stepUpTarget.index
                stepUpRetryCount = 1
            }
            if (ENVELOPE_EXEC_DEBUG && stepUpRetryCount in 2..3) {
                info("[StepUpGuard] counting seg=${stepUpTarget.index} count=$stepUpRetryCount stuck=$stuckTicks segIdx=$currentSegmentIndex")
            }
            // A step-up that keeps re-commanding without ever mounting is
            // jumping at a face the clearance probes miss. The vertical
            // bouncing defeats the stall detector (each bounce reads as
            // movement), so burn the stuck clock structurally instead —
            // measured: 182 wall-jumps over 2000 ticks with zero reports.
            if (stepUpRetryCount > STEP_UP_MAX_RETRY_COMMANDS) {
                structuralJumpBlock = true
                if (ENVELOPE_EXEC_DEBUG && stepUpRetryCount % 8 == 0) {
                    info("[StepUpGuard] seg=${stepUpTarget.index} retries=$stepUpRetryCount stuckTicks=$stuckTicks")
                }
            }
        } else if (player.isOnGround && currentSegmentIndex > stepUpRetrySegment) {
            // Mounted: standing past the segment the retries targeted. The
            // grounded requirement is load-bearing — a failed bounce's arc
            // relocalizes one segment ahead at its apex and back on landing,
            // and an airborne reset here erased the count every bounce.
            stepUpRetrySegment = -1
            stepUpRetryCount = 0
        }
        val needsGapJump = chainSegment == null && !needsStepUpJump && needsGapJump(segment, playerPos)
        // A momentum (envelope) edge certifies a tight takeoff window, and
        // at sprint speed the segment often becomes current only after that
        // window has passed. Arm the NEXT segment's envelope gap while
        // finishing the approach segment — scoped strictly to envelope
        // edges so every ordinary jump keeps its segment-advance timing.
        val nextEnvelopeGap = if (chainSegment == null && !needsStepUpJump && !needsGapJump) {
            nextEnvelopeGapSegment(path, currentSegmentIndex, segment, playerPos)
        } else null
        val needsEnvelopeAnticipation = nextEnvelopeGap != null && needsGapJump(nextEnvelopeGap, playerPos)
        val jumpRequested = needsChainJump || needsStepUpJump || needsGapJump || needsEnvelopeAnticipation
        val jumpTarget = when {
            needsChainJump -> chainTarget
            needsStepUpJump -> stepUpTarget.endPose.position
            needsGapJump -> segment.endPose.position
            needsEnvelopeAnticipation -> nextEnvelopeGap.endPose.position
            else -> null
        }
        // Brake-policy playback covers chains AND discovered single jumps,
        // each with its own lead (chains protect the next takeoff; singles
        // only trim landing overshoot).
        val jumpBrakeLead = when {
            needsChainJump -> ManeuverPolicy.BRAKE_LEAD_TICKS
            needsGapJump && (segment as? WalkSegment)?.discovered == true ->
                ManeuverPolicy.SINGLE_BRAKE_LEAD_TICKS
            needsEnvelopeAnticipation -> ManeuverPolicy.SINGLE_BRAKE_LEAD_TICKS
            needsStepUpJump && stepUpTarget?.discovered == true ->
                ManeuverPolicy.SINGLE_BRAKE_LEAD_TICKS
            else -> null
        }

        // Steering target. Default: lookahead along the path. On a gap
        // segment two overrides apply (Baritone MovementParkour semantics):
        // mid-arc, fly at the landing node — the cross-segment lookahead
        // already points around the next corner and drags the arc off the
        // landing line; grounded on the takeoff side without a committed
        // jump, steer *to the takeoff point* at reduced throttle instead of
        // charging the edge unaligned (the align phase).
        val lookaheadPointRaw = path.lookaheadAcrossSegments(playerPos, currentSegmentIndex, movementConfig.lookaheadDistance)
        var steerTarget = Vec3d(lookaheadPointRaw.x, playerPos.y, lookaheadPointRaw.z)
        val walkSegment = segment as? WalkSegment
        val gapSegmentActive = walkSegment != null &&
            (isGapJumpSegment(walkSegment) || isRisingGapSegment(walkSegment))
        if (chainSegment != null) {
            // Chain policy playback (ManeuverPolicy — same rule the sim
            // validated): steer at the next landing; brake mid-air when
            // close to it. Jump input comes from needsChainJump above.
            val target = chainTarget ?: chainSegment.endPose.position
            steerTarget = Vec3d(target.x, playerPos.y, target.z)
            if (!player.isOnGround) {
                val distanceToTarget = hypot(target.x - playerPos.x, target.z - playerPos.z)
                val speed = hypot(player.velocity.x, player.velocity.z)
                if (ManeuverPolicy.shouldBrake(distanceToTarget, speed)) throttle = 0.0
            }
        } else if (gapSegmentActive) {
            if (!player.isOnGround || jumpRequested) {
                // Mid-arc AND the launch tick itself: fly at the landing
                // node. The sprint-jump boost fires along the commanded yaw,
                // so a launch-tick yaw at the cross-corner lookahead skews
                // the whole arc off the validated line — the recurring
                // bedrock miss signature (landing a block off despite a
                // centered, in-window takeoff).
                val end = segment.endPose.position
                steerTarget = Vec3d(end.x, playerPos.y, end.z)
                // Discovered jumps brake mid-air near the landing — the
                // same ManeuverPolicy rule their validation sims ran.
                if (!player.isOnGround && walkSegment?.discovered == true) {
                    val distanceToEnd = hypot(end.x - playerPos.x, end.z - playerPos.z)
                    val speed = hypot(player.velocity.x, player.velocity.z)
                    if (ManeuverPolicy.shouldBrake(distanceToEnd, speed, ManeuverPolicy.SINGLE_BRAKE_LEAD_TICKS)) {
                        throttle = 0.0
                    }
                }
            } else {
                val takeoffPoint = gapTakeoffPoint(walkSegment)
                val projected = segment.projectedDistance(playerPos)
                val relativeProgress = projected - gapTakeoffOffset(walkSegment)
                val beforeHole = holeStartDistance(walkSegment)?.let { projected < it } != false
                // Envelope edges end their usable window at the certified
                // launch depth, not the generic 0.9 cap.
                val takeoffWindowEnd = walkSegment.entrySpeedEnvelope
                    ?.maxTakeoffProgress?.coerceAtMost(GAP_TAKEOFF_MAX_PROGRESS)
                    ?: GAP_TAKEOFF_MAX_PROGRESS
                if (relativeProgress > takeoffWindowEnd && beforeHole && !launchSimBuild) {
                    // Grounded past the takeoff window without a committed
                    // jump, still on the takeoff side of the hole — dead
                    // ahead is the gap. Walk back to the takeoff so the
                    // window and jump gate re-engage on the way in. Reached
                    // from drop/arc landings that overshoot the validated
                    // takeoff node. The hole-side test is load-bearing: a
                    // landing that touches down short of the segment end is
                    // also "past the window", and walking back from THERE
                    // strides backward into the gap just crossed (measured).
                    // launchSimBuild overrides the retreat: the takeoff gate
                    // ran the launch sim from this deep stance and predicted
                    // an undershoot that more speed fixes — build it moving
                    // FORWARD; walking back here is the "slight overshoot →
                    // retreat → momentum gone" field signature.
                    steerTarget = Vec3d(takeoffPoint.x, playerPos.y, takeoffPoint.z)
                    throttle = kotlin.math.min(throttle, GAP_ALIGN_THROTTLE)
                } else if (relativeProgress > -0.5) {
                    // Inside (or just before) the takeoff window: aim at the
                    // LANDING, never at the takeoff point — steering at a
                    // point one is standing on degenerates and the commanded
                    // yaw wobbles 90°+, which the yaw gate then blocks
                    // forever (the bedrock stall-replan signature). Aiming
                    // down the jump line settles yaw and lateral error
                    // together; the window cap plus walk-back bound how far
                    // the approach can carry. Throttle: full speed while the
                    // launch sim is driving the entry (SimShort — momentum
                    // is the fix); align-creep when the sim said too hot
                    // (launchSimHold) or the jump is structurally blocked
                    // (NoHeadroom/misalignment — charging a blocked edge at
                    // sprint was the mutation-ceiling collision storm). Long
                    // discovered gaps stay fast unless held: their launch
                    // needs sprint carry and was validated across the band.
                    val end = segment.endPose.position
                    steerTarget = Vec3d(end.x, playerPos.y, end.z)
                    if (launchSimHold || (!launchSimBuild && !longGapActive)) {
                        throttle = kotlin.math.min(throttle, GAP_ALIGN_THROTTLE)
                    }
                }
                // The stutter-step: trim the approach early when the launch
                // ticks would straddle a tight certified window.
                if (player.isOnGround && walkSegment.entrySpeedEnvelope != null) {
                    phaseAlignmentThrottle(walkSegment, playerPos)?.let {
                        throttle = kotlin.math.min(throttle, it)
                    }
                }
                // Edge-stop overrides everything: no headroom to jump and no
                // room to keep rolling — kill the input entirely.
                if (launchEdgeStop) throttle = 0.0
            }
        } else if (nextEnvelopeGap != null) {
            if (needsEnvelopeAnticipation) {
                // Launch tick armed from the approach segment: aim at the
                // landing, exactly as the in-segment launch-tick rule — the
                // sprint-jump boost fires along the commanded yaw.
                val end = nextEnvelopeGap.endPose.position
                steerTarget = Vec3d(end.x, playerPos.y, end.z)
            } else {
                phaseAlignmentThrottle(nextEnvelopeGap, playerPos)?.let {
                    throttle = kotlin.math.min(throttle, it)
                }
            }
            // The gate may have edge-stopped the approach (entry state not
            // certifiable and support about to end): honor it here too.
            if (launchEdgeStop) throttle = 0.0
        }
        val lookaheadPoint = steerTarget
        val desiredDelta = lookaheadPoint.subtract(playerPos).flattenY()
        val desiredYaw = playerPos.rotationTo(lookaheadPoint).yaw

        val command = FollowCommand(
            lookaheadPoint = lookaheadPoint,
            desiredYaw = desiredYaw,
            throttle = throttle,
            sprint = commandSprint && !launchSprintSuppressed,
            jump = jumpRequested,
            jumpUrgent = needsGapJump || needsChainJump || needsEnvelopeAnticipation,
            jumpTarget = jumpTarget,
            jumpBrakeLead = jumpBrakeLead,
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
            sprintCommand = lastSteeringTelemetry.sprint || commandSprint,
            jumpCommandGate = lastJumpCommandGate,
            jumpInputGate = lastJumpInputGate,
            jumpTarget = lastIssuedJumpTarget,
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
            lostTicks = lostTicks,
            skippedSegments = skippedSegments,
            rewoundSegments = rewoundSegments,
            replansRequested = replansRequested,
            jumpTarget = lastIssuedJumpTarget,
            wallCollisionTicks = wallCollisionTicks,
            headBonkTicks = headBonkTicks,
            arcTickError = lastArcTickError,
            plannedArcError = lastPlanArcError,
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
        // Stuck tracking is grounded-only, including the segment-identity
        // rekey: a failed bounce's arc relocalizes one segment ahead at its
        // apex and back on landing, and letting airborne ticks rekey (or
        // letting the apex's shrinking 3D remaining distance count as
        // progress) reset the clock every bounce — 187 wall-jumps over
        // 2000 ticks without a single stuck report (measured).
        if (!player.isOnGround) return
        if (handle.id != stuckTraversalId || currentSegmentIndex != stuckSegmentIndex) {
            stuckTraversalId = handle.id
            stuckSegmentIndex = currentSegmentIndex
            stuckBestRemaining = remainingDistance
            stuckTicks = 0
            return
        }
        if (remainingDistance < stuckBestRemaining - STUCK_PROGRESS_EPSILON) {
            stuckBestRemaining = remainingDistance
            stuckTicks = 0
            return
        }
        // Geometric blocks (no jump headroom) never self-resolve — no speed
        // or alignment tuning helps — so they burn the report clock faster
        // than plain no-progress ticks, which still deserve the full window
        // (align/walk-back phases legitimately pause progress).
        stuckTicks += if (structuralJumpBlock) STRUCTURAL_BLOCK_STUCK_STEP else 1
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

        // Urgent jumps (gap ahead) skip the retry cooldown: the alternative
        // to jumping is walking into the hole, and flight-toggle safety is
        // inherent — consecutive presses are separated by a full arc's air
        // time, well past the double-tap window.
        if (!command.jumpUrgent && player.age < jumpRetryAllowedTick) {
            lastJumpInputGate = "cooldown(${jumpRetryAllowedTick - player.age})"
            return false
        }

        if (!jumpAttemptActive) {
            jumpAttemptActive = true
            jumpHoldUntilTick = player.age + JUMP_HOLD_TICKS
            newJumpIssued = true
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
        // One volatile read: the path and its maneuver provenance must come
        // from the same worker publication, never from live discovery maps.
        val publishedPlan = handle.planSnapshot
        val sourcePath = publishedPlan.path
        if (existing != null && activeTraversalId == handle.id && activeSourcePath === sourcePath) {
            return existing
        }

        val traversalChanged = activeTraversalId != null && activeTraversalId != handle.id
        val annotations = publishedPlan.edgeAnnotations
        val rebuilt = ExecutionPath.fromNodes(
            handle.id, sourcePath,
            maneuverWaypoints = { from, to -> annotations[from to to]?.chainWaypoints },
            discoveredJump = { from, to -> annotations[from to to]?.discoveredJump == true },
            entrySpeedEnvelope = { from, to -> annotations[from to to]?.entrySpeedEnvelope },
        )
        activePath = rebuilt
        activeTraversalId = handle.id
        activeSourcePath = sourcePath
        currentSegmentIndex = 0
        pathNeedsRelocalization = true
        currentCommand = null
        lastSteeringTelemetry = SteeringTelemetry()
        if (traversalChanged) resetTelemetry()
        return rebuilt
    }

    private fun applyRecovery(selection: SegmentSelection, previousIndex: Int?) {
        if (previousIndex == null) return
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
        pathNeedsRelocalization = false
        currentCommand = null
        plannedArcsCache = emptyList()
        plannedArcsPath = null
        debugSamples.clear()
        lastSteeringTelemetry = SteeringTelemetry()
        lastLoggedSignature = ""
        lastLoggedTick = Int.MIN_VALUE
        resetJumpState()
        resetChainState()
    }

    private fun resetTelemetry() {
        lostTicks = 0
        skippedSegments = 0
        rewoundSegments = 0
        replansRequested = 0
        wallCollisionTicks = 0
        headBonkTicks = 0
        resetJumpState()
        resetChainState()
    }

    private fun resetJumpState() {
        jumpAttemptActive = false
        jumpHoldUntilTick = Int.MIN_VALUE
        jumpRetryAllowedTick = Int.MIN_VALUE
        stepUpRetrySegment = -1
        stepUpRetryCount = 0
        newJumpIssued = false
        launchArcPoints = emptyList()
        launchArcIndex = -1
        lastArcTickError = null
        lastPlanArcError = null
    }

    private fun resetChainState() {
        chainTraversalId = Int.MIN_VALUE
        chainSegmentIndex = Int.MIN_VALUE
        chainTargetIndex = 0
    }

    private fun SafeContext.activeChainTarget(traversalId: Int, chain: ChainSegment, playerPos: Vec3d): Vec3d {
        if (chainTraversalId != traversalId || chainSegmentIndex != chain.index) {
            chainTraversalId = traversalId
            chainSegmentIndex = chain.index
            chainTargetIndex = 0
        }
        val targets = chain.waypoints + chain.endPose.position
        var target = targets[chainTargetIndex.coerceIn(0, targets.lastIndex)]
        if (player.isOnGround && chainTargetIndex < targets.lastIndex &&
            abs(playerPos.y - target.y) <= CHAIN_WAYPOINT_VERTICAL_TOLERANCE &&
            hypot(playerPos.x - target.x, playerPos.z - target.z) <= ManeuverPolicy.WAYPOINT_TOLERANCE
        ) {
            chainTargetIndex++
            target = targets[chainTargetIndex]
        }
        return target
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

    private fun SafeContext.stepUpJumpTarget(
        path: ExecutionPath,
        currentSegmentIndex: Int,
        currentSegment: ExecutionSegment,
        playerPos: Vec3d,
    ): WalkSegment? {
        if (!player.isOnGround) {
            lastJumpCommandGate = "airborne"
            return null
        }
        val current = currentSegment as? WalkSegment ?: run {
            lastJumpCommandGate = "notWalk"
            return null
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
                        return null
                    }
            else -> {
                lastJumpCommandGate = "verticalCurrent"
                return null
            }
        }
        if (playerPos.y >= rise.endPose.position.y - 0.1) {
            lastJumpCommandGate = "alreadyUp"
            return null
        }

        // Rising gap jump (Baritone: "ascend ⇒ sprint", edge takeoff): the
        // +1 landing must be met while the arc still has a block of height —
        // an early launch puts the descending tail at the pad face instead.
        // Not a step-up: route through the shared edge-takeoff gate.
        if (isRisingGapSegment(rise)) {
            if (rise !== current) {
                lastJumpCommandGate = "riseGapApproach"
                return null
            }
            // A rising-gap arc can touch down on an adjacent high block, then
            // slide back onto supported lower ground just beyond the nominal
            // takeoff window. Treat that stance as a recoverable step-up, not
            // as an obstruction that must sit motionless for 40 ticks before
            // replanning. This branch is deliberately narrow: below the
            // intended landing, close to its face, centered, and still on
            // continuous support.
            val progress = rise.projectedDistance(playerPos) - gapTakeoffOffset(rise)
            val end = rise.endPose.position
            val horizontalToEnd = hypot(end.x - playerPos.x, end.z - playerPos.z)
            if (progress > GAP_TAKEOFF_MAX_PROGRESS &&
                horizontalToEnd <= RECOVERY_STEP_UP_DISTANCE &&
                rise.lateralError(playerPos) <= CONFINED_JUMP_MAX_SIDE_OFFSET &&
                with(WalkingMovementModel) {
                    hasContinuousSupport(playerPos) && hasJumpApexClearance(playerPos)
                }
            ) {
                lastJumpCommandGate = "riseGapRecovery"
                return rise
            }
            return rise.takeIf { edgeTakeoffReady(rise, playerPos, "riseGap") }
        }

        // Drift gate (Baritone MovementAscend): a jump launched while sliding
        // sideways lands off the validated column. Wait for steering to
        // settle — one or two ticks — before committing the arc.
        if (perpendicularSpeed(rise, player.velocity) > JUMP_MAX_LATERAL_DRIFT) {
            lastJumpCommandGate = "drifting"
            return null
        }

        val end = rise.endPose.position
        val horizontalToEnd = hypot(end.x - playerPos.x, end.z - playerPos.z)
        val towardX = (end.x - playerPos.x) / horizontalToEnd.coerceAtLeast(1.0E-9)
        val towardZ = (end.z - playerPos.z) / horizontalToEnd.coerceAtLeast(1.0E-9)
        val aheadPos = Vec3d(playerPos.x + towardX * 0.5, playerPos.y, playerPos.z + towardZ * 0.5)
        val apexClear = with(WalkingMovementModel) {
            hasJumpApexClearance(playerPos) && hasJumpApexClearance(aheadPos)
        }

        if (apexClear) {
            // Open air: jump *early* (Baritone's tactic) — launching before
            // the riser face means a full block of height is gained before
            // arrival, so the chest never hits the ledge. A walk-speed arc
            // covers ~2.5 blocks, so anything inside the window lands on or
            // past the ledge lip.
            if (horizontalToEnd > EARLY_JUMP_TRIGGER_DISTANCE) {
                lastJumpCommandGate = "tooFar(%.2f)".format(horizontalToEnd)
                return null
            }
            lastJumpCommandGate = "commanded"
            return rise
        }

        // Confined (ceiling nearby): Baritone's ActionClimb rule — walk in
        // until at most ~1.2 blocks out and laterally centered, then hop.
        // Walking closer is always valid: the validated node has clearance.
        if (horizontalToEnd > CONFINED_JUMP_TRIGGER_DISTANCE) {
            lastJumpCommandGate = "tooFar(%.2f)".format(horizontalToEnd)
            return null
        }
        if (rise.lateralError(playerPos) > CONFINED_JUMP_MAX_SIDE_OFFSET) {
            lastJumpCommandGate = "misaligned"
            return null
        }
        if (!with(WalkingMovementModel) { hasJumpApexClearance(playerPos) }) {
            lastJumpCommandGate = "noHeadroom"
            return null
        }

        lastJumpCommandGate = "commanded"
        return rise
    }

    /**
     * Flat gap-jump segments carry no vertical step, so [stepUpJumpTarget]
     * can never fire for them — the H6 baseline failure mode (gauntlet
     * telemetry: agents sprint-carry small gaps or fall in, `noCommand` the
     * whole way). Detect them structurally: a gap-jump edge survives
     * refinement as exactly a cardinal 2-block flat segment (the corridor
     * can never merge across the unsupported middle column), so a flat
     * segment of length 2 with no ground under its midpoint is a gap jump.
     * Longer flat segments are corridor-validated and therefore gap-free.
     *
     * Interim reactive gate — WP3.3 replaces this with scripted maneuvers
     * carrying entry envelopes; until then takeoff happens as soon as the
     * segment is active, trading landing precision for never undershooting.
     */
    /**
     * The structural gap-jump signature — see [needsGapJump]. Template gap
     * edges are exactly 2 blocks; discovered sprint-jump edges (WP3.2) run
     * up to ~4.3. Any flat segment in that band with an unsupported column
     * along its interior is a jump, never a walk: corridor-validated
     * shortcuts and merges are hole-free by construction.
     */
    private fun SafeContext.isGapJumpSegment(segment: ExecutionSegment): Boolean {
        val walk = segment as? WalkSegment ?: return false
        // Discovered flat and DESCENDING jumps both run the flat-gap
        // machinery: takeoff at the start node, arc falls to the landing.
        if (walk.discovered) return walk.verticalStep <= 0
        if (walk.verticalStep != 0) return false
        if (walk.horizontalLength !in GAP_SEGMENT_MIN_LENGTH..GAP_SEGMENT_MAX_LENGTH) return false

        val start = walk.startPose.position
        val end = walk.endPose.position
        var distance = 0.8
        while (distance <= walk.horizontalLength - 0.8 + 1.0E-6) {
            val t = distance / walk.horizontalLength
            val probe = Vec3d(
                start.x + (end.x - start.x) * t,
                start.y,
                start.z + (end.z - start.z) * t,
            )
            if (!with(WalkingMovementModel) { hasContinuousSupport(probe) }) return true
            distance += 0.4
        }
        return false
    }

    /**
     * Discovered sprint-jump segment: validated at sprint entry speed, must
     * launch at the segment start. Provenance-keyed — a refiner-merged
     * template gap of the same length is a *walk*-entry hop whose takeoff
     * anchors to the hole; classifying by length sprint-launched those from
     * the merged start and overshot every landing by ~2 blocks (measured).
     */
    private fun isLongGapSegment(segment: ExecutionSegment): Boolean =
        (segment as? WalkSegment)?.discovered == true

    private fun SafeContext.needsGapJump(segment: ExecutionSegment, playerPos: Vec3d): Boolean {
        if (!player.isOnGround) return false
        if (!isGapJumpSegment(segment)) return false
        return edgeTakeoffReady(segment as WalkSegment, playerPos, "gap")
    }

    /**
     * Launch-phase alignment for envelope edges (the stutter-step): tick
     * quantization makes the launch fire at discrete positions one ground
     * stride (~0.28 blocks at sprint) apart, and a certified takeoff window
     * narrower than that stride is only reachable if the approach is
     * trimmed EARLY so that a future tick lands inside it — exactly how a
     * human lines up a max-distance jump. Returns a reduced throttle for
     * this tick when the predicted landing tick overshoots the window, or
     * null when the phase is already good (or it is too late to trim
     * without arriving below the envelope's minimum entry speed).
     */
    private fun SafeContext.phaseAlignmentThrottle(walk: WalkSegment, playerPos: Vec3d): Double? {
        val envelope = walk.entrySpeedEnvelope ?: return null
        if (!player.isOnGround) return null
        val progress = walk.projectedDistance(playerPos) - gapTakeoffOffset(walk)
        if (progress >= envelope.minTakeoffProgress) return null
        val stored = alongSpeed(walk, player.velocity)
        if (stored <= 0.03) return null
        // Stored velocity is post-friction; the actual per-tick displacement
        // divides the ground friction factor back out.
        val slipperiness = world.getBlockState(player.velocityAffectingPos).block.slipperiness.toDouble()
        val stride = stored / (slipperiness * GROUND_DRAG)
        val distance = envelope.minTakeoffProgress - progress
        val ticksToWindow = kotlin.math.ceil(distance / stride - 1.0E-6)
        val landingProgress = progress + ticksToWindow * stride
        if (landingProgress <= envelope.maxTakeoffProgress - PHASE_LANDING_MARGIN) return null
        // Trim only while there is room to rebuild the envelope speed
        // before the window; at the lip the launch sim owns the decision.
        if (distance < stride * PHASE_TRIM_MIN_STRIDES) return null
        return PHASE_TRIM_THROTTLE
    }

    /**
     * The upcoming envelope gap to arm from the current approach segment,
     * or null. Only envelope (momentum-critical) edges get early arming —
     * their certified takeoff window can be narrower than one sprint
     * stride, so waiting for segment advancement can skip it entirely.
     */
    private fun SafeContext.nextEnvelopeGapSegment(
        path: ExecutionPath,
        index: Int,
        segment: ExecutionSegment,
        playerPos: Vec3d,
    ): WalkSegment? {
        if (!player.isOnGround) return null
        val current = segment as? WalkSegment ?: return null
        if (current.discovered) return null
        val next = path.segments.getOrNull(index + 1) as? WalkSegment ?: return null
        if (next.entrySpeedEnvelope == null) return null
        // Rising gaps have their own look-ahead (stepUpJumpTarget); this
        // arming covers the flat/descending gap machinery only.
        if (!isGapJumpSegment(next)) return null
        if (current.remainingDistance(playerPos) > GAP_SEGMENT_ANTICIPATION_DISTANCE) return null
        return next
    }

    /**
     * Rising gap-jump signature: a 2-block edge climbing one block — the
     * gapJump(rise=1) template. Only real gap edges survive refinement at
     * this shape (corridor shortcuts are flat), so no support probe needed.
     */
    private fun SafeContext.isRisingGapSegment(walk: WalkSegment): Boolean {
        if (walk.verticalStep <= 0) return false
        if (walk.discovered) return true
        if (walk.horizontalLength < GAP_SEGMENT_MIN_LENGTH) return false

        // Refinement can merge an ordinary step and its flat approach into a
        // two-block rising segment. Length alone therefore does not prove a
        // gap. Probe the interior at the destination stance height: if it is
        // supported, use the regular ascend controller (including its tighter
        // lateral-drift gate) instead of edge-parkour playback.
        val start = walk.startPose.position
        val end = walk.endPose.position
        var distance = 0.8
        while (distance <= walk.horizontalLength - 0.8 + 1.0E-6) {
            val t = distance / walk.horizontalLength
            val probe = Vec3d(
                start.x + (end.x - start.x) * t,
                end.y,
                start.z + (end.z - start.z) * t,
            )
            if (!with(WalkingMovementModel) { hasContinuousSupport(probe) }) return true
            distance += 0.4
        }
        return false
    }

    /**
     * Where the takeoff window of a gap-class segment begins, as projected
     * distance from the segment start: one probe step before the first
     * unsupported interior column. A clean 2-block gap edge yields 0 (the
     * segment start IS the takeoff block), but refinement legally merges an
     * approach run into the edge — sim-validated at sprint, where momentum
     * carries the hole — and then a window measured from the segment start
     * sits entirely on the approach: the gate reads "past takeoff" while
     * still blocks before the hole and the agent strides straight into it
     * (the gauntlet-mixed walk-off regression, exposed the moment the
     * sprint cut removed the accidental momentum-carry).
     */
    /**
     * Projected distance at which the segment's first unsupported column
     * begins, or null when the interior is fully supported.
     */
    private fun SafeContext.holeStartDistance(walk: WalkSegment): Double? {
        val start = walk.startPose.position
        val end = walk.endPose.position
        val probeY = if (walk.verticalStep > 0) end.y else start.y
        var distance = 0.8
        while (distance <= walk.horizontalLength - 0.8 + 1.0E-6) {
            val t = distance / walk.horizontalLength
            val probe = Vec3d(
                start.x + (end.x - start.x) * t,
                probeY,
                start.z + (end.z - start.z) * t,
            )
            if (!with(WalkingMovementModel) { hasContinuousSupport(probe) }) return distance
            distance += 0.4
        }
        return null
    }

    private fun SafeContext.gapTakeoffOffset(walk: WalkSegment): Double {
        if (walk.discovered) return 0.0
        val holeStart = holeStartDistance(walk) ?: return 0.0
        return (holeStart - 0.8).coerceAtLeast(0.0)
    }

    /** The point on the segment line where the takeoff window begins. */
    private fun SafeContext.gapTakeoffPoint(walk: WalkSegment): Vec3d {
        val offset = gapTakeoffOffset(walk)
        if (walk.horizontalLength <= 1.0E-6) return walk.startPose.position
        val t = (offset / walk.horizontalLength).coerceIn(0.0, 1.0)
        val start = walk.startPose.position
        val end = walk.endPose.position
        return Vec3d(
            start.x + (end.x - start.x) * t,
            start.y,
            start.z + (end.z - start.z) * t,
        )
    }

    /**
     * Shared takeoff gate for gap-class jumps, flat and rising (Baritone
     * MovementParkour's lineup rules): launch only from the takeoff-side
     * window — never early (a short arc lands in the gap), never from the
     * landing side (a pointless hop) — centered on the segment line and
     * drift-free, so the arc cannot clip the gap corner. The window is
     * anchored to the hole ([gapTakeoffOffset]), not the segment start.
     */
    private fun SafeContext.edgeTakeoffReady(walk: WalkSegment, playerPos: Vec3d, prefix: String): Boolean {
        if (edgeTakeoffGates(walk, playerPos, prefix)) return true

        // Edge guard — the fallback when every gate above refused. Two
        // zones, judged against the physical edge (hole start + footprint
        // overhang), never against "could I stop if I kept this speed":
        //
        // 1. Feet leave support NEXT TICK: last chance. A committed launch
        //    with mid-air correction beats the guaranteed fall (the field
        //    "fell without ever jumping" failure); at creep speeds or
        //    without headroom, hard-stop — the overhang absorbs the slide.
        // 2. Friction alone can no longer stop before the edge (≈2.2×
        //    current speed of slide): hard-brake NOW and re-gate slower.
        //
        // A forced jump on ANY gate refusal inside the stopping envelope
        // was tried and produced misyawed jumps in random directions —
        // at sprint speed that envelope spans a discovered jump's entire
        // launch window, so every transient Misyawed/NoGo hold launched.
        val holeStart = holeStartDistance(walk) ?: return false
        val projectedAbs = walk.projectedDistance(playerPos)
        // Guard only while the hole is AHEAD. A grounded stance can project
        // at most probe-slack + footprint overhang past the support end on
        // the near side; anything beyond is a carry landing on the FAR side
        // (the arc crossed the hole). Without this, the landing tick of
        // every carry jump read "leaving support next tick" and force-fired
        // an unvalidated hop into the next pit (bench: 10 failed landings).
        if (projectedAbs > holeStart + EDGE_NEAR_SIDE_MAX) return false
        val forwardSpeed = alongSpeed(walk, player.velocity)
        if (forwardSpeed <= 0.03) return false
        val physicalEdge = holeStart + EDGE_OVERHANG_SLACK
        if (projectedAbs + forwardSpeed > physicalEdge) {
            // Envelope edges never force-launch outside their certified
            // interval — an uncertified arc into a momentum gap is a
            // guaranteed miss; the hard stop below is strictly better.
            val committed = forwardSpeed >= EDGE_FORCE_MIN_SPEED &&
                walk.entrySpeedEnvelope?.contains(forwardSpeed, ENTRY_SPEED_TOLERANCE) != false &&
                with(WalkingMovementModel) { hasJumpApexClearance(playerPos) }
            if (committed) {
                lastJumpCommandGate = "${prefix}EdgeForced($lastJumpCommandGate)"
                return true
            }
            lastJumpCommandGate = "${prefix}EdgeStop($lastJumpCommandGate)"
            launchEdgeStop = true
            return false
        }
        if (projectedAbs + forwardSpeed * EDGE_STOP_LEAD > physicalEdge) {
            lastJumpCommandGate = "${prefix}EdgeBrake($lastJumpCommandGate)"
            launchEdgeStop = true
        }
        return false
    }

    private fun SafeContext.edgeTakeoffGates(walk: WalkSegment, playerPos: Vec3d, prefix: String): Boolean {
        val holeStart = holeStartDistance(walk)
        val projectedAbs = walk.projectedDistance(playerPos)
        val progress = projectedAbs - gapTakeoffOffset(walk)
        val envelope = walk.entrySpeedEnvelope
        // A jump commanded this tick processes on the next physics tick
        // FROM THE CURRENT POSITION, so in-window means fire-now — there is
        // no extra latency stride to anticipate (measured; an anticipation
        // tick was tried and simulated launches from positions that never
        // occur). Tight windows are hit by approach phase alignment
        // ([phaseAlignmentThrottle]), not by gate timing tricks.
        if (progress < (if (envelope != null) envelope.minTakeoffProgress - ENTRY_PROGRESS_TOLERANCE else 0.0)) {
            lastJumpCommandGate = "${prefix}NotAtTakeoff"
            return false
        }
        if (envelope != null && progress > envelope.maxTakeoffProgress + ENTRY_PROGRESS_TOLERANCE) {
            // Beyond the certified launch depth nothing is validated —
            // never deep-launch an envelope edge. Steering's walk-back
            // covers ordinary overshoot; the edge guard covers a hot
            // approach that can no longer stop. Below envelope speed this
            // stance cannot be recovered by in-segment shaping — tell the
            // planner instead of dancing at the lip.
            lastJumpCommandGate = "${prefix}PastEnvelope"
            if (alongSpeed(walk, player.velocity) < envelope.min - ENTRY_SPEED_TOLERANCE) {
                structuralJumpBlock = true
            }
            return false
        }
        if (progress > GAP_TAKEOFF_MAX_PROGRESS) {
            // Past the nominal window but still on the takeoff side of the
            // hole: a deep launch is usually still valid — discovery
            // validates launches 0.45 past center, and the sim below is
            // ground truth from the exact stance. Walking back instead was
            // the single biggest field momentum leak (land slightly deep →
            // retreat → re-approach from scratch). The margin keeps the
            // charge from carrying into the hole while the sim still says
            // Short.
            val deepLaunchable = holeStart == null || projectedAbs < holeStart - DEEP_LAUNCH_HOLE_MARGIN
            if (!deepLaunchable) {
                lastJumpCommandGate = "${prefix}PastTakeoff"
                return false
            }
        }
        // The pre-filters below only spare the launch sim from hopeless
        // states — they are deliberately loose. The sim is the decider;
        // gating stricter than the sim can actually land forces align
        // choreography (and its momentum loss) for jumps that were fine.
        if (walk.lateralError(playerPos) > GAP_MAX_LATERAL_ERROR) {
            lastJumpCommandGate = "${prefix}Misaligned"
            return false
        }
        if (perpendicularSpeed(walk, player.velocity) > GAP_MAX_LATERAL_DRIFT) {
            lastJumpCommandGate = "${prefix}Drifting"
            return false
        }
        // Never launch while still moving toward the takeoff from the
        // walk-back recovery: a backward-moving arc undershoots into the
        // hole. One or two ticks of forward input settles this.
        if (alongSpeed(walk, player.velocity) < -0.02) {
            lastJumpCommandGate = "${prefix}Reversing"
            return false
        }
        // Yaw sanity only: the launch sim models the settling rotation
        // itself (tick-0 boost fires along the LIVE yaw), so misyaw shows
        // up as a simulated miss rather than needing a tight gate here.
        val segmentYaw = walk.startPose.position.rotationTo(walk.endPose.position).yaw
        val yawError = abs(net.minecraft.util.math.MathHelper.wrapDegrees(movementPhysicsBasisYaw() - segmentYaw))
        if (yawError > GAP_MAX_YAW_ERROR_DEGREES) {
            lastJumpCommandGate = "${prefix}Misyawed(%.0f)".format(yawError)
            return false
        }
        if (!with(WalkingMovementModel) { hasJumpApexClearance(playerPos) }) {
            lastJumpCommandGate = "${prefix}NoHeadroom"
            structuralJumpBlock = true
            return false
        }
        // Never launch a discovered jump from a (near-)standstill: the
        // sprint latch and yaw settling both lag the command by a tick, so
        // the sim cannot know whether the real launch gets its boost — a
        // predicted rim-landing then flips to an undershoot (measured at
        // spawn-adjacent takeoffs). Two build ticks later the state is
        // settled and the sim's authority is real.
        if (walk.discovered && alongSpeed(walk, player.velocity) < LAUNCH_MIN_ENTRY_SPEED) {
            lastJumpCommandGate = "${prefix}EntrySettling"
            launchSimBuild = true
            return false
        }
        // Envelope interval gate (T3): the worker certified this edge only
        // for entries inside [min, max] — the whole-band disturbance margin
        // ordinary edges carry does not exist here, so the live entry state
        // must match before the launch sim gets a vote.
        if (envelope != null) {
            val entrySpeed = alongSpeed(walk, player.velocity)
            if (entrySpeed > envelope.max + ENTRY_SPEED_TOLERANCE) {
                lastJumpCommandGate = "${prefix}AboveEnvelope(%.2f>%.2f)".format(entrySpeed, envelope.max)
                launchSimHold = true
                return false
            }
            if (entrySpeed < envelope.min - ENTRY_SPEED_TOLERANCE) {
                // Too slow for the certified interval: keep building
                // forward (launchSimBuild = full throttle). If the window
                // ends before another build tick can deliver vmin, the edge
                // is infeasible from this approach — report it structurally
                // (5× stuck clock) and let the planner reroute. Entry
                // shaping at the lip is never a recovery: it oscillates
                // against relocalization (tried twice in this project).
                lastJumpCommandGate = "${prefix}BelowEnvelope(%.2f<%.2f)".format(entrySpeed, envelope.min)
                launchSimBuild = true
                if (progress + entrySpeed.coerceAtLeast(0.0) > envelope.maxTakeoffProgress) {
                    structuralJumpBlock = true
                }
                return false
            }
        }
        if (ENVELOPE_EXEC_DEBUG) {
            val verdict = launchSimVerdict(walk)
            info(
                "[EnvelopeGate] $prefix progress=%.3f speed=%.3f sprint=%b window=[%.2f,%.2f] verdict=%s seg=%d".format(
                    progress, alongSpeed(walk, player.velocity), player.isSprinting,
                    envelope?.minTakeoffProgress ?: 0.0, envelope?.maxTakeoffProgress ?: 9.9, verdict, walk.index,
                )
            )
        }
        // The decider: simulate the jump from the LIVE state — position,
        // velocity, sprint, everything — and only launch if that flight
        // lands on the target. The verdict is directional: an entry that's
        // too HOT holds the jump and sheds speed (launchSimHold); one
        // that's still too SLOW keeps full throttle and re-checks next
        // tick as speed builds — shedding on a short arc just guaranteed
        // it stayed short and ended in walk-back (measured). Entry
        // self-tunes per jump instead of fixed windows matching every
        // terrain shape.
        when (launchSimVerdict(walk)) {
            LaunchSimVerdict.Go -> {
                lastJumpCommandGate = "${prefix}Commanded"
                return true
            }
            LaunchSimVerdict.Short -> {
                // Keep building speed; the edge guard in [edgeTakeoffReady]
                // brakes the charge if the stopping envelope is ever
                // violated, so safety needs no second rule here.
                lastJumpCommandGate = "${prefix}SimShort"
                launchSimBuild = true
                return false
            }
            LaunchSimVerdict.NoGo -> {
                // A sprint-boosted arc that overflies a short landing can
                // still be a clean walk-speed hop (ascend rises): prove it
                // in the sim and drop sprint for the launch tick — the
                // walk-speed hop a human does at a one-block step gap.
                if (commandSprint && launchSimVerdict(walk, sprint = false) == LaunchSimVerdict.Go) {
                    launchSprintSuppressed = true
                    lastJumpCommandGate = "${prefix}CommandedNoSprint"
                    return true
                }
                lastJumpCommandGate = "${prefix}SimNoGo"
                launchSimHold = true
                return false
            }
        }
    }

    /** Directional launch-sim outcome; see [edgeTakeoffReady]. */
    private enum class LaunchSimVerdict {
        /** Predicted touchdown on the landing — fire the jump now. */
        Go,
        /** Arc ends short of the landing: more entry speed (or a deeper
         *  launch point) fixes it — keep throttle, never shed. */
        Short,
        /** Overshoot, off-line, or collision: shed speed and re-align. */
        NoGo,
    }

    /**
     * One tick-accurate flight sim from the live player state with the
     * follow controller's own inputs (steer at landing, hold forward, brake
     * per policy on discovered jumps). Tick 0 uses the LIVE movement yaw —
     * the sprint-jump boost fires along the commanded yaw the tick the jump
     * input lands, so a still-settling rotation must show up as a simulated
     * miss, not be assumed away. Runs only on grounded ticks that passed
     * the cheap gates — a handful of 10–30 tick sims per jump approach.
     */
    private fun SafeContext.launchSimVerdict(walk: WalkSegment, sprint: Boolean = commandSprint): LaunchSimVerdict {
        val target = walk.endPose.position
        val from = player.pos
        val flat = target.subtract(from).flattenY()
        if (flat.lengthSquared() < 1.0E-6) return LaunchSimVerdict.NoGo
        val aimRotation = from.rotationTo(target)
        val liveRotation = Rotation(movementPhysicsBasisYaw(), player.pitch.toDouble())
        val targetProgress = walk.projectedDistance(target)

        // Terminal state short of the landing along the segment line means
        // speed (or launch depth) was insufficient — the recheck next tick
        // sees a faster entry. A head-bonked arc is excluded: the ceiling
        // flattened it, and no amount of entry speed changes that (charging
        // at a bonk-shortened arc was the mutation-ceiling collision storm).
        var headBonked = false
        fun terminalVerdict(position: Vec3d): LaunchSimVerdict =
            if (!headBonked && walk.projectedDistance(position) < targetProgress - LAUNCH_SIM_SHORT_MARGIN) {
                LaunchSimVerdict.Short
            } else {
                LaunchSimVerdict.NoGo
            }

        val simulator = MovementSimulator(
            player = player,
            initialState = MovementSimulationState.at(
                player = player,
                position = from,
                rotation = liveRotation,
                velocity = player.velocity,
                onGround = true,
                isSprinting = sprint,
            ),
        ).also { it.skipEntityCollisions = true }

        for (tick in 0 until ARC_MAX_TICKS) {
            val before = simulator.lastTick
            val brake = walk.discovered && tick > 0 && !before.onGround && ManeuverPolicy.shouldBrake(
                hypot(target.x - before.position.x, target.z - before.position.z),
                hypot(before.velocity.x, before.velocity.z),
                ManeuverPolicy.SINGLE_BRAKE_LEAD_TICKS,
            )
            val current = simulator.tickMovement(
                MovementSimulationInput(
                    forward = if (brake) 0.0 else 1.0,
                    strafe = 0.0,
                    jump = tick == 0,
                    sneak = false,
                    sprint = sprint,
                    useItemSlowdown = false,
                    rotation = if (tick == 0) liveRotation else aimRotation,
                )
            )
            if (current.simulator.state.verticalCollision && !current.onGround) headBonked = true
            if (current.onGround && tick > 1) {
                // Discovered jumps launch on the sim's word alone, so their
                // Go needs disturbance margin: the predicted touchdown must
                // sit well inside the pad, not on its rim — one more build
                // tick otherwise. Template walk-arcs keep the full pad (an
                // honest 2-gap walk arc legitimately lands 0.6–0.8 past
                // center and always did); envelope edges live off their
                // certified entry box instead.
                val tolerance = if (walk.discovered && walk.entrySpeedEnvelope == null) {
                    LAUNCH_SIM_COMMIT_TOLERANCE
                } else {
                    LAUNCH_SIM_LANDING_TOLERANCE
                }
                val onTarget = hypot(current.position.x - target.x, current.position.z - target.z) <=
                    tolerance &&
                    abs(current.position.y - target.y) <= 0.3
                // Envelope edges are validated with same-level carry (±1
                // block onto the landing platform); hold the live gate to
                // the same predicate or the worker admits edges the
                // executor then vetoes forever.
                val feet = current.position.flooredBlockPos
                val targetFeet = target.flooredBlockPos
                val carryTarget = !onTarget && walk.entrySpeedEnvelope != null &&
                    feet.y == targetFeet.y &&
                    abs(feet.x - targetFeet.x) <= 1 && abs(feet.z - targetFeet.z) <= 1 &&
                    with(WalkingMovementModel) { hasContinuousSupport(current.position) }
                return if (onTarget || carryTarget) LaunchSimVerdict.Go else terminalVerdict(current.position)
            }
            if (current.simulator.state.horizontalCollision) {
                if (ENVELOPE_EXEC_DEBUG) {
                    info("[EnvelopeGate] sim hColl tick=$tick at %.2f,%.2f,%.2f".format(current.position.x, current.position.y, current.position.z))
                }
                return LaunchSimVerdict.NoGo
            }
            if (current.position.y < minOf(from.y, target.y) - 0.2) {
                if (ENVELOPE_EXEC_DEBUG) {
                    info("[EnvelopeGate] sim fell tick=$tick at %.2f,%.2f".format(current.position.x, current.position.z))
                }
                return terminalVerdict(current.position)
            }
        }
        return LaunchSimVerdict.NoGo
    }

    /**
     * Chain jump gate: the first takeoff gets the full alignment gates (a
     * misaligned chain entry compounds over every hop); every later grounded
     * tick inside the chain jumps immediately — the validated policy is
     * jump-on-landing, and hesitation sheds the momentum the next hop needs.
     * Near the end, stop jumping and let the walk controller settle.
     */
    private fun SafeContext.needsChainJump(chain: ChainSegment, playerPos: Vec3d): Boolean {
        if (!player.isOnGround) {
            lastJumpCommandGate = "chainAirborne"
            return false
        }
        val progress = chain.projectedDistance(playerPos)
        if (progress < 0.0) {
            lastJumpCommandGate = "chainNotAtTakeoff"
            return false
        }
        if (chain.remainingDistance(playerPos) < 1.0) {
            lastJumpCommandGate = "chainAtEnd"
            return false
        }
        if (progress < 1.0) {
            if (chain.lateralError(playerPos) > CHAIN_MAX_LATERAL_ERROR) {
                lastJumpCommandGate = "chainMisaligned"
                return false
            }
            if (perpendicularSpeed(chain, player.velocity) > CHAIN_MAX_LATERAL_DRIFT) {
                lastJumpCommandGate = "chainDrifting"
                return false
            }
        }
        if (!with(WalkingMovementModel) { hasJumpApexClearance(playerPos) }) {
            lastJumpCommandGate = "chainNoHeadroom"
            structuralJumpBlock = true
            return false
        }
        lastJumpCommandGate = "chainCommanded"
        return true
    }

    /**
     * The moment a jump input actually fires, forward-simulate the flight
     * from the REAL player state (position, velocity, sprint) with the same
     * steering the follow controller will apply — steer at the jump target,
     * hold forward, brake per [ManeuverPolicy] on chains. This is the
     * honest expected trajectory of THIS jump: per-tick deviation from it
     * measures simulator-vs-server divergence (theory note T3's δ), while
     * deviation from the pre-planned arc measures how far the executed
     * entry drifted from the plan's assumptions. Also rendered.
     */
    private fun AutomatedSafeContext.predictLaunchArc(command: FollowCommand, sprint: Boolean) {
        val target = command.jumpTarget ?: command.lookaheadPoint
        val flat = target.subtract(player.pos).flattenY()
        if (flat.lengthSquared() < 1.0E-6) return
        val rotation = player.pos.rotationTo(target)
        val brakeLead = command.jumpBrakeLead
        val simulator = MovementSimulator(
            player = player,
            initialState = MovementSimulationState.at(
                player = player,
                position = player.pos,
                rotation = rotation,
                velocity = player.velocity,
                onGround = true,
                isSprinting = sprint,
            ),
        ).also { it.skipEntityCollisions = true }

        val points = ArrayList<Vec3d>(ARC_MAX_TICKS)
        for (tick in 0 until ARC_MAX_TICKS) {
            val before = simulator.lastTick
            val brake = brakeLead != null && !before.onGround && ManeuverPolicy.shouldBrake(
                hypot(target.x - before.position.x, target.z - before.position.z),
                hypot(before.velocity.x, before.velocity.z),
                brakeLead,
            )
            val current = simulator.tickMovement(
                MovementSimulationInput(
                    forward = if (brake) 0.0 else 1.0,
                    strafe = 0.0,
                    jump = tick == 0,
                    sneak = false,
                    sprint = sprint,
                    useItemSlowdown = false,
                    rotation = rotation,
                )
            )
            points += current.position
            if (current.onGround && tick > 1) break
        }
        launchArcPoints = points
        launchArcIndex = -1
    }

    /**
     * Per-tick trajectory-match tracking (runs on Player.Post, i.e. after
     * this tick's physics): tick-aligned distance to the launch prediction,
     * and nearest distance to the active segment's planned arc while
     * airborne. Cleared on touchdown.
     */
    private fun AutomatedSafeContext.trackTrajectoryMatch(playerPos: Vec3d) {
        if (launchArcPoints.isEmpty()) {
            lastArcTickError = null
            lastPlanArcError = null
            return
        }
        launchArcIndex++
        if (player.isOnGround && launchArcIndex >= 1 || launchArcIndex > launchArcPoints.lastIndex + 2) {
            launchArcPoints = emptyList()
            launchArcIndex = -1
            lastArcTickError = null
            lastPlanArcError = null
            return
        }
        lastArcTickError = launchArcPoints.getOrNull(launchArcIndex)?.distanceTo(playerPos)
        lastPlanArcError = if (!player.isOnGround) {
            plannedArcsCache.firstOrNull { it.segmentIndex == currentSegmentIndex }
                ?.points?.minOfOrNull { it.distanceTo(playerPos) }
        } else null
    }

    // ------------------------------------------------------------------
    // Planned-arc prediction: one forward simulation per maneuver segment,
    // run with the same entry assumptions the follow controller reproduces
    // (walk entry for short flat gaps and step-ups, sprint for long/rising
    // gaps and chains). Cached per adopted path object; render-only.
    // ------------------------------------------------------------------

    // Always computed, not render-gated: the planned-arc polylines are also
    // the reference the trajectory-match telemetry measures against.
    private fun AutomatedSafeContext.refreshPlannedArcs(path: ExecutionPath) {
        if (plannedArcsPath === path) return
        plannedArcsCache = path.segments.mapNotNull { segment ->
            when (segment) {
                is ChainSegment -> predictChainArc(segment)
                is WalkSegment -> predictWalkArc(segment)
                else -> null
            }
        }
        plannedArcsPath = path
    }

    private fun SafeContext.predictWalkArc(segment: WalkSegment): PlannedArc? {
        val rising = isRisingGapSegment(segment)
        val gap = !rising && isGapJumpSegment(segment)
        val kind = when {
            rising -> PlannedArc.Kind.RisingGap
            gap -> PlannedArc.Kind.GapJump
            segment.verticalStep > 0 -> PlannedArc.Kind.StepUp
            segment.verticalStep < 0 -> PlannedArc.Kind.Drop
            else -> return null
        }
        val sprintEntry = rising || (gap && isLongGapSegment(segment))
        // Gap arcs launch where the executor will: the segment start for
        // discovered jumps (the validated takeoff node), the hole-anchored
        // takeoff point for template/refiner-merged gaps. Step-ups launch
        // EARLY (the open-air tactic) — measured launches sit ~1.5 blocks
        // before the rise end, often before the segment start.
        val start = segment.startPose.position
        val end = segment.endPose.position
        val launchPoint = when {
            (gap || rising) && !segment.discovered -> gapTakeoffPoint(segment)
            kind == PlannedArc.Kind.StepUp -> {
                val back = end.subtract(start).flattenY().normalize().multiply(STEP_UP_ARC_LAUNCH_DISTANCE)
                Vec3d(end.x - back.x, start.y, end.z - back.z)
            }
            else -> start
        }
        return simulateArc(
            segmentIndex = segment.index,
            kind = kind,
            start = launchPoint,
            target = segment.endPose.position,
            entrySpeed = if (sprintEntry) ARC_SPRINT_ENTRY_SPEED else ARC_WALK_ENTRY_SPEED,
            sprint = sprintEntry,
            jumpAtStart = kind != PlannedArc.Kind.Drop,
            brakeLead = if (segment.discovered) ManeuverPolicy.SINGLE_BRAKE_LEAD_TICKS else null,
        )
    }

    private fun SafeContext.simulateArc(
        segmentIndex: Int,
        kind: PlannedArc.Kind,
        start: Vec3d,
        target: Vec3d,
        entrySpeed: Double,
        sprint: Boolean,
        jumpAtStart: Boolean,
        brakeLead: Double? = null,
    ): PlannedArc? {
        val flatDelta = target.subtract(start).flattenY()
        if (flatDelta.lengthSquared() < 1.0E-6) return null
        val rotation = start.rotationTo(target)
        val direction = flatDelta.normalize()
        val simulator = MovementSimulator(
            player = player,
            initialState = MovementSimulationState.at(
                player = player,
                position = start,
                rotation = rotation,
                velocity = direction.multiply(entrySpeed),
                onGround = true,
                isSprinting = sprint,
            ),
        ).also { it.skipEntityCollisions = true }

        val points = ArrayList<Vec3d>(ARC_MAX_TICKS + 1)
        points += start
        for (tick in 0 until ARC_MAX_TICKS) {
            val before = simulator.lastTick
            val brake = brakeLead != null && tick > 0 && !before.onGround && ManeuverPolicy.shouldBrake(
                hypot(target.x - before.position.x, target.z - before.position.z),
                hypot(before.velocity.x, before.velocity.z),
                brakeLead,
            )
            val current = simulator.tickMovement(
                MovementSimulationInput(
                    forward = if (brake) 0.0 else 1.0,
                    strafe = 0.0,
                    jump = jumpAtStart && tick == 0,
                    sneak = false,
                    sprint = sprint,
                    useItemSlowdown = false,
                    rotation = rotation,
                )
            )
            points += current.position
            // A drop stays grounded while approaching the edge; only a
            // touchdown *below* the start level ends its arc.
            val landed = current.onGround && tick > 1 &&
                (jumpAtStart || current.position.y < start.y - 0.5)
            if (landed || current.simulator.state.horizontalCollision) break
        }
        if (points.size < 3) return null
        return PlannedArc(segmentIndex, kind, points, target)
    }

    private fun SafeContext.predictChainArc(chain: ChainSegment): PlannedArc? {
        val start = chain.startPose.position
        val end = chain.endPose.position
        val flatDelta = end.subtract(start).flattenY()
        if (flatDelta.lengthSquared() < 1.0E-6) return null
        val rotation = start.rotationTo(end)
        val targets = chain.waypoints + end
        val simulator = MovementSimulator(
            player = player,
            initialState = MovementSimulationState.at(
                player = player,
                position = start,
                rotation = rotation,
                velocity = flatDelta.normalize().multiply(ARC_SPRINT_ENTRY_SPEED),
                onGround = true,
                isSprinting = true,
            ),
        ).also { it.skipEntityCollisions = true }

        val points = ArrayList<Vec3d>(ManeuverPolicy.MAX_CHAIN_TICKS + 1)
        points += start
        var targetIndex = 0
        for (tick in 0 until ManeuverPolicy.MAX_CHAIN_TICKS) {
            val before = simulator.lastTick
            val target = targets[targetIndex]
            val distanceToTarget = hypot(target.x - before.position.x, target.z - before.position.z)
            val speed = hypot(before.velocity.x, before.velocity.z)
            val brake = !before.onGround && ManeuverPolicy.shouldBrake(distanceToTarget, speed)
            val current = simulator.tickMovement(
                MovementSimulationInput(
                    forward = if (brake) 0.0 else 1.0,
                    strafe = 0.0,
                    jump = before.onGround,
                    sneak = false,
                    sprint = true,
                    useItemSlowdown = false,
                    rotation = rotation,
                )
            )
            points += current.position
            if (current.simulator.state.horizontalCollision) break
            if (current.onGround && tick > 1) {
                val feet = current.position
                val onTarget = hypot(targets[targetIndex].x - feet.x, targets[targetIndex].z - feet.z) <=
                    ManeuverPolicy.WAYPOINT_TOLERANCE
                if (onTarget) {
                    if (targetIndex == targets.lastIndex) break
                    targetIndex++
                }
            }
        }
        if (points.size < 3) return null
        return PlannedArc(chain.index, PlannedArc.Kind.Chain, points, end)
    }

    /** Speed component perpendicular to the segment line, in blocks/tick. */
    private fun perpendicularSpeed(segment: ExecutionSegment, velocity: Vec3d): Double {
        val dx = segment.endPose.position.x - segment.startPose.position.x
        val dz = segment.endPose.position.z - segment.startPose.position.z
        val length = hypot(dx, dz)
        if (length < 1.0E-9) return 0.0
        return kotlin.math.abs(velocity.x * -dz + velocity.z * dx) / length
    }

    /** Signed speed along the segment line (negative = moving backward). */
    private fun alongSpeed(segment: ExecutionSegment, velocity: Vec3d): Double {
        val dx = segment.endPose.position.x - segment.startPose.position.x
        val dz = segment.endPose.position.z - segment.startPose.position.z
        val length = hypot(dx, dz)
        if (length < 1.0E-9) return 0.0
        return (velocity.x * dx + velocity.z * dz) / length
    }

    /**
     * A short flat gap jump starts within [distance] blocks of path ahead
     * (the current segment included). Walk arcs were what validated these —
     * cutting sprint only once the gap segment is current is too late: the
     * leftover 0.25+ b/t carry lands the arc a block past the validated
     * node (both landing misses of the 16:26 bedrock run). Long gaps are
     * excluded — they were validated at sprint entry and need it.
     */
    private fun SafeContext.shortFlatGapWithin(path: ExecutionPath, index: Int, playerPos: Vec3d, distance: Double): Boolean {
        var remaining = distance
        var i = index
        while (i <= path.lastSegmentIndex && remaining > 0.0) {
            val segment = path.segments[i]
            if (isGapJumpSegment(segment) && !isLongGapSegment(segment)) return true
            remaining -= if (i == index) segment.remainingDistance(playerPos) else segment.horizontalLength
            i++
        }
        return false
    }

    /**
     * A short step-up starts within [distance] blocks of path ahead. Rising
     * *gap* segments are deliberately excluded: they are the ascend-parkour
     * case that needs sprint speed, not the chest-bonk case that forbids it.
     */
    private fun SafeContext.riseWithin(path: ExecutionPath, index: Int, playerPos: Vec3d, distance: Double): Boolean {
        var remaining = distance
        var i = index
        while (i <= path.lastSegmentIndex && remaining > 0.0) {
            val segment = path.segments[i]
            val walk = segment as? WalkSegment
            if (walk != null && walk.verticalStep > 0 && !isRisingGapSegment(walk)) return true
            remaining -= if (i == index) segment.remainingDistance(playerPos) else segment.horizontalLength
            i++
        }
        return false
    }

    private data class FollowCommand(
        val lookaheadPoint: Vec3d,
        val desiredYaw: Double,
        val throttle: Double,
        val sprint: Boolean,
        val jump: Boolean = false,
        /** Gap-ahead jump: skips the retry cooldown (falling is worse). */
        val jumpUrgent: Boolean = false,
        val jumpTarget: Vec3d? = null,
        /** Mid-air brake lead ticks for this jump's flight, null = no braking. */
        val jumpBrakeLead: Double? = null,
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
    private const val CHAIN_WAYPOINT_VERTICAL_TOLERANCE = 0.20

    // Step-up trigger windows, following Baritone MovementAscend: with clear
    // apex headroom, jump early (height is gained before the riser face —
    // never a chest bonk); under a ceiling, walk to ~1.2 out and hop from a
    // centered, drift-free stance (their ActionClimb rule).
    private const val EARLY_JUMP_TRIGGER_DISTANCE = 2.2
    private const val CONFINED_JUMP_TRIGGER_DISTANCE = 1.2
    private const val CONFINED_JUMP_MAX_SIDE_OFFSET = 0.2
    private const val RECOVERY_STEP_UP_DISTANCE = 1.25
    private const val JUMP_MAX_LATERAL_DRIFT = 0.10

    // How early (remaining distance on the flat segment) the executor may
    // anticipate the next rise segment's jump.
    private const val JUMP_ANTICIPATION_DISTANCE = 0.6

    // How early (remaining distance on the approach segment) the gap gate
    // may arm the NEXT segment's envelope jump — about one sprint stride.
    private const val GAP_SEGMENT_ANTICIPATION_DISTANCE = 0.8

    // Consecutive grounded step-up jump commands on the same segment before
    // the block is reported structural (each wall-bounce produces ~1).
    private const val STEP_UP_MAX_RETRY_COMMANDS = 6

    // Live velocity has small per-tick jitter around the worker's sampled
    // envelope endpoints; this is measurement tolerance, not band widening —
    // the launch sim keeps final authority inside it.
    private const val ENTRY_SPEED_TOLERANCE = 0.008
    private const val ENTRY_PROGRESS_TOLERANCE = 0.08

    // Minimum stored entry speed before a discovered-jump launch may fire;
    // below it the sprint latch and yaw are still settling and the sim's
    // boost assumption is unreliable (field: turns deliver 0.12–0.15).
    private const val LAUNCH_MIN_ENTRY_SPEED = 0.10

    // Envelope gate/sim outcome logging; keep off outside investigations.
    private const val ENVELOPE_EXEC_DEBUG = false

    // Launch-phase alignment (see phaseAlignmentThrottle). Vanilla ground
    // friction multiplies stored velocity by slipperiness × 0.91 per tick.
    private const val GROUND_DRAG = 0.91
    private const val PHASE_TRIM_THROTTLE = 0.6
    private const val PHASE_LANDING_MARGIN = 0.04
    private const val PHASE_TRIM_MIN_STRIDES = 1.5

    // Structural gap-detection band for non-discovered segments: template
    // gap edges are exactly 2 blocks; refiner merges extend them. Discovered
    // jumps carry provenance and skip the band.
    private const val GAP_SEGMENT_MIN_LENGTH = 1.9
    private const val GAP_SEGMENT_MAX_LENGTH = 4.4

    // Vertical segment-tracking slack while airborne: vanilla jump apex
    // (~1.252, nominal — WP0 calibration owns the measured value) plus
    // margin, so an ordinary arc never triggers Lost.
    private const val AIRBORNE_VERTICAL_TOLERANCE = 1.5

    // Gap takeoff pre-filters. These only spare the launch sim from
    // hopeless states — the sim (which models the settling yaw itself) is
    // the decider. Deliberately loose: tightening them past what the sim
    // can land forces align choreography and its momentum loss (measured;
    // the previous 0.25/0.08/20° gates were the pre-sim-gate values and
    // over-constrained every approach once the sim existed).
    private const val GAP_MAX_LATERAL_ERROR = 0.4
    private const val GAP_MAX_LATERAL_DRIFT = 0.15
    private const val GAP_TAKEOFF_MAX_PROGRESS = 0.9
    private const val GAP_MAX_YAW_ERROR_DEGREES = 40.0

    // Chain first-hop alignment keeps the strict pre-sim values: chains
    // have no per-tick launch sim and entry error compounds over hops.
    private const val CHAIN_MAX_LATERAL_ERROR = 0.25
    private const val CHAIN_MAX_LATERAL_DRIFT = 0.08

    // Predicted-landing acceptance for the launch sim gate: feet within
    // this horizontal distance of the landing node center. With the
    // vy-set simulator fix the prediction equals the real touchdown, so
    // this matches the bench oracle's own strict-landing tolerance; a
    // tighter gate was tried and rejected template walk-arcs whose honest
    // overshoot P0 always accepted in reality.
    private const val LAUNCH_SIM_LANDING_TOLERANCE = 0.9
    private const val LAUNCH_SIM_COMMIT_TOLERANCE = 0.55

    // A simulated terminal state at least this far short of the landing
    // (along the segment) reads as an undershoot: keep speed and re-check,
    // never shed. Within the margin, treat as overshoot/off-line instead —
    // ambiguous landings must not keep charging the edge.
    private const val LAUNCH_SIM_SHORT_MARGIN = 0.35

    // Deep-launch zone bound: past the nominal takeoff window, forward
    // speed-building (SimShort) is only honored while the stance stays this
    // far before the hole edge — beyond it, only a sim Go (immediate jump)
    // or walk-back are acceptable; charging the last centimeters risks a
    // walk-off on the next tick.
    private const val DEEP_LAUNCH_HOLE_MARGIN = 0.3

    // Point-of-no-return lead (ticks-worth of current speed): vanilla ground
    // friction (slip 0.6 × drag 0.91 ≈ ×0.546/tick) stops a zero-input
    // player in ≈2.2× current speed; the margin covers the hole-probe
    // granularity. Beyond this, it's jump now or fall.
    private const val EDGE_STOP_LEAD = 2.4

    // Below this along-speed a "can't stop" verdict is illusory — the probe
    // granularity plus the 0.3 footprint overhang absorb the slide — so the
    // edge guard stops instead of forcing a hopeless standstill hop.
    private const val EDGE_FORCE_MIN_SPEED = 0.15

    // Feet keep support while their center overhangs the last block by up
    // to ~0.3; judged from the hole-probe boundary, keep a bit less.
    private const val EDGE_OVERHANG_SLACK = 0.2

    // Max distance a grounded NEAR-side stance can project past the hole
    // probe boundary (0.4 probe quantization + 0.3 footprint overhang);
    // grounded farther than this means the hole is behind the player.
    private const val EDGE_NEAR_SIDE_MAX = 0.5


    // Bounded receding-horizon correction while airborne. Inputs include
    // nominal steering, partial/zero/reverse acceleration, and two lateral
    // probes; only a predicted touchdown improvement changes the command.
    private const val AIR_MPC_MAX_TICKS = 16
    private const val AIR_MPC_LATERAL_INPUT = 0.55
    private const val AIR_MPC_REVERSE_INPUT = 0.45
    private const val AIR_MPC_VERTICAL_PENALTY = 8.0
    private const val AIR_MPC_COLLISION_PENALTY = 50.0
    private const val AIR_MPC_FALL_PENALTY = 40.0
    private const val AIR_MPC_NO_LANDING_PENALTY = 25.0
    private const val AIR_MPC_MIN_IMPROVEMENT = 0.02

    // Align-phase throttle: grounded on the takeoff side without a committed
    // jump, walk gently back to the takeoff point instead of charging the
    // edge (Baritone's lineup walk-back).
    private const val GAP_ALIGN_THROTTLE = 0.6


    // Sprint is cut when a rise begins within this much path distance —
    // sprint arcs reach the riser face before gaining a block of height.
    private const val RISE_SPRINT_CUT_DISTANCE = 2.5

    // Sprint is cut when a short flat gap begins within this much path
    // distance, so the walk-speed arc those jumps were validated at is the
    // arc that actually flies (ground drag settles the carry in ~2 blocks).
    private const val GAP_SPRINT_CUT_DISTANCE = 2.5

    // Stuck detection: how long the player may sit on a segment without net
    // progress before the edge is reported as obstructed to the planner.
    private const val STUCK_REPORT_TICKS = 40

    // Stuck-clock ticks charged per structurally-blocked tick (see
    // trackExecutionProgress): a headroom block reports in ~8 real ticks.
    private const val STRUCTURAL_BLOCK_STUCK_STEP = 5
    private const val STUCK_PROGRESS_EPSILON = 0.02

    // Planned-arc prediction entry speeds: the middle of the discovery
    // validation band for sprint launches, a settled walk approach for
    // short flat gaps and step-ups. Never feeds control — the arcs are the
    // rendering and the trajectory-match reference.
    private const val ARC_SPRINT_ENTRY_SPEED = 0.27
    private const val ARC_WALK_ENTRY_SPEED = 0.15
    private const val ARC_MAX_TICKS = 30

    // Where a step-up's planned arc launches: this far before the rise end
    // along the segment line (measured early-jump launches: 1.4–1.7).
    private const val STEP_UP_ARC_LAUNCH_DISTANCE = 1.5
}
