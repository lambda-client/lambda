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

package com.lambda

import com.lambda.context.SafeContext
import com.lambda.context.Automated
import com.lambda.config.automation.AutomationConfig
import com.lambda.config.blocks.PathingConfig
import com.lambda.pathing.PathingManager
import com.lambda.pathing.PathingRequest
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.BedrockFieldLayout
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.trajectory.PublishedPath
import com.lambda.threading.runSafe
import com.lambda.util.player.MovementUtils.buildMovementInput
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.MovementSimulator
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.minecraft.client.input.Input
import net.minecraft.util.math.Vec3d
import java.util.concurrent.CompletableFuture
import kotlin.jvm.optionals.getOrNull
import kotlin.math.abs

/**
 * Shared plumbing for the client gametest suite: the walk and replay assertions,
 * scenario filtering, aggregated failures, and the standard arena restore.
 */
@Suppress("UnstableApiUsage")
internal object PathingTestHarness {
    /**
     * Wipes the whole shared workspace (including below-deck builds down to y=87, the
     * slime pit out to x=11, and staircase builds out to z=24) before rebuilding the
     * standard deck, so no scenario's terrain can leak into the next one.
     */
    fun restoreArena(server: TestServerContext) {
        server.runCommand("/fill -8 87 -8 11 105 24 minecraft:air")
        server.runCommand("/fill -8 99 -8 8 99 8 minecraft:stone")
        auditArena(server)
    }

    // ------------------------------------------------------------------ measurement

    /** Scenario most recently run, for attributing terrain corruption and stuck events. */
    var lastScenario: String = "<none>"
        private set

    val measurementReport = ArrayList<String>()

    private var cleanFingerprint: Long? = null

    /**
     * Fingerprints the entire terrain envelope the suite ever touches and names any
     * scenario that left dirt behind. Trusting per-scenario teardowns is exactly how
     * corrupted terrain leaked between scenarios undetected before.
     */
    private fun auditArena(server: TestServerContext) {
        val dirt = ArrayList<String>()
        var hash = 1469598103934665603L
        server.runOnServer<IllegalStateException> { minecraftServer ->
            val world = minecraftServer.overworld
            val pos = net.minecraft.util.math.BlockPos.Mutable()
            for (x in AUDIT_MIN_X..AUDIT_MAX_X) for (y in AUDIT_MIN_Y..AUDIT_MAX_Y) for (z in AUDIT_MIN_Z..AUDIT_MAX_Z) {
                val state = world.getBlockState(pos.set(x, y, z))
                if (!state.isAir) {
                    hash = (hash xor (state.block.hashCode().toLong() * 31 + (x * 341873128712L + y * 132897987541L + z))) * 1099511628211L
                    if (cleanFingerprint != null && dirt.size < MAX_REPORTED_DIRT &&
                        !(y == 99 && x in -8..8 && z in -8..8 && state.isOf(net.minecraft.block.Blocks.STONE)) &&
                        !(y <= AUDIT_SUPERFLAT_TOP)
                    ) {
                        dirt += "($x,$y,$z)=${net.minecraft.registry.Registries.BLOCK.getId(state.block).path}"
                    }
                }
            }
        }
        val clean = cleanFingerprint
        if (clean == null) {
            cleanFingerprint = hash
        } else if (hash != clean) {
            measurementReport += "[arena-dirt] after $lastScenario: ${dirt.size} foreign block(s): " +
                dirt.joinToString(" ")
        }
    }

    /** One stuck event: no observable progress while the walk should be moving. */
    fun recordStuck(kind: String, detail: String) {
        measurementReport += "[stuck] $lastScenario: $kind $detail"
    }

    private const val AUDIT_MIN_X = -8
    private const val AUDIT_MAX_X = 120
    private const val AUDIT_MIN_Y = 60
    private const val AUDIT_MAX_Y = 106
    private const val AUDIT_MIN_Z = -8
    private const val AUDIT_MAX_Z = 135
    private const val AUDIT_SUPERFLAT_TOP = -60
    private const val MAX_REPORTED_DIRT = 24

    fun assertPathingWalk(
        context: ClientGameTestContext,
        server: net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext,
        scenario: String,
        goal: Stance,
        minLegs: Int = 1,
        maxLegs: Int? = null,
        maxDeviation: Double = REPLAY_DEVIATION_EPSILON,
        expectedJumpDy: Int? = null,
        requireJumpInput: Boolean = false,
        minGapLaunches: Int = 0,
        minContinuousSegments: Int = 1,
        requireMovingSplices: Boolean = false,
        cameraYawDuringPlanning: Float? = null,
        plannerMaxFrames: Int? = null,
        driftBeforeSubmit: Vec3d? = null,
        start: String = "0.5 100 0.5 0 0",
        maxPathingTicks: Int = MAX_PATHING_TICKS,
    ) {
        // Filtered out scenarios are skipped, but the world commands around them are not:
        // they build the terrain later scenarios stand on, so running a subset has to
        // leave the world exactly as running all of it would.
        if (!scenarioSelected(scenario)) return
        // One failing scenario used to abort the whole suite, so "one scenario is red"
        // never meant more than "at least one" -- every later result stayed unknown until
        // the first was fixed. Failures are collected and reported together instead.
        try {
            runPathingWalk(
                context, server, scenario, goal, minLegs, maxLegs, maxDeviation, expectedJumpDy,
                requireJumpInput, minGapLaunches, minContinuousSegments, requireMovingSplices,
                cameraYawDuringPlanning, plannerMaxFrames,
                driftBeforeSubmit, start, maxPathingTicks,
            )
        } catch (failure: IllegalStateException) {
            pathingFailures += failure.message ?: "$scenario: ${failure::class.simpleName}"
            println("[pathing-fail] ${failure.message}")
        }
    }

    /** Scenario substrings from `-Ppathing.filter`; empty means run them all. */
    val scenarioFilter: List<String> by lazy {
        System.getProperty("lambda.pathing.testFilter").orEmpty()
            .split(',').map(String::trim).filter(String::isNotEmpty)
    }

    fun scenarioSelected(scenario: String): Boolean =
        scenarioFilter.isEmpty() || scenarioFilter.any { scenario.contains(it) }

    val pathingFailures = ArrayList<String>()

    private fun runPathingWalk(
        context: ClientGameTestContext,
        server: net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext,
        scenario: String,
        goal: Stance,
        minLegs: Int,
        maxLegs: Int?,
        maxDeviation: Double,
        expectedJumpDy: Int?,
        requireJumpInput: Boolean,
        minGapLaunches: Int,
        minContinuousSegments: Int,
        requireMovingSplices: Boolean,
        cameraYawDuringPlanning: Float?,
        plannerMaxFrames: Int?,
        driftBeforeSubmit: Vec3d?,
        start: String,
        maxPathingTicks: Int,
    ) {
        server.runCommand("/tp Steve $start")
        // A previous long-haul scenario may have moved the client watch center far
        // enough that this scenario's start chunk must stream back in after teleport.
        // Do not mistake that ordinary loading delay for a pathing failure.
        var settledTicks = 0
        for (tick in 0 until TELEPORT_SETTLE_TICKS) {
            context.waitTick()
            context.runOnClient<IllegalStateException> {
                val player = Lambda.mc.player
                if (player?.isOnGround == true && player.velocity.lengthSquared() <= SETTLED_SPEED_SQUARED) {
                    settledTicks++
                } else {
                    settledTicks = 0
                }
            }
            if (settledTicks >= REQUIRED_SETTLED_TICKS) break
        }

        context.runOnClient<IllegalStateException> {
            val player = Lambda.mc.player ?: error("Missing client player")
            check(player.isOnGround) { "$scenario: player did not settle" }
            PathingManager.clear()
            val base = AutomationConfig.DEFAULT.pathingConfig
            val automated = if (plannerMaxFrames != null) {
                object : Automated by AutomationConfig.DEFAULT {
                    override val pathingConfig = object : PathingConfig by base {
                        override val maxFrames = checkNotNull(plannerMaxFrames)
                    }
                }
            } else {
                AutomationConfig.DEFAULT
            }
            // Submit while the body still drifts, reproducing a rapid retry: the manager
            // must settle to true rest before capturing, or frame zero is a moving state
            // the player has already shed by the time the async plan returns.
            driftBeforeSubmit?.let { player.velocity = it }
            PathingRequest(automated, goal).submit()
            // Change view immediately after the manager captured its immutable
            // planning yaw. Waiting for another client tick is racy: short plans
            // can finish before the test thread observes Status.Planning.
            cameraYawDuringPlanning?.let { player.yaw = it }
        }

        // Simulates the user looking elsewhere while the worker owns an immutable
        // start state. Movement yaw must be held/aligned rather than rejecting the
        // certified continuation because the camera changed during planning.
        // The assignment above is intentionally in the submit task, before the
        // worker completion can be installed on a later client task.

        lastScenario = scenario
        // The manager owns planning and replay; just let the client tick until it
        // settles. `return@repeat` would be a *continue*, so this must be a real loop
        // with a break -- otherwise the walk finishes and the test keeps ticking.
        var ticks = 0
        var lastObserved: Pair<String, Int> = "" to -1
        var stillTicks = 0
        var stuckReported = false
        while (ticks++ < maxPathingTicks) {
            context.waitTick()
            val status = PathingManager.status
            if (status is PathingManager.Status.Complete || status is PathingManager.Status.Failed) break
            // Progress watch: an Executing frame that does not advance, or any
            // non-executing state that persists, is a stuck walk RIGHT NOW -- record
            // it with live planner state instead of a generic timeout much later.
            val observed = when (status) {
                is PathingManager.Status.Executing -> "exec" to status.frame
                is PathingManager.Status.Planning -> "plan" to 0
                is PathingManager.Status.Settling -> "settle" to 0
                is PathingManager.Status.Aligning -> "align" to 0
                else -> "other" to 0
            }
            if (observed == lastObserved) stillTicks++ else { stillTicks = 0; stuckReported = false }
            lastObserved = observed
            if (stillTicks >= STUCK_TICKS && !stuckReported) {
                stuckReported = true
                recordStuck(
                    "no progress for $stillTicks ticks in ${observed.first}(${observed.second})",
                    "at tick $ticks: ${PathingManager.diagnostics()}",
                )
            }
        }

        context.runOnClient<IllegalStateException> {
            val status = PathingManager.status
            val published = PathingManager.published
            if (status !is PathingManager.Status.Complete || published == null) {
                PathingMetricSink.record(
                    PathingMetricSink.Run(
                        scenario, success = false, completionTicks = ticks,
                        trajectoryFrames = published?.plan?.frames?.size ?: 0,
                        collisionFrames = 0, bumps = 0,
                        launchMarginFrames = published?.launchMarginFrames ?: 0,
                        planLatencyMs = published?.planMillis ?: 0L,
                        maxReplayDeviation = PathingManager.maxDeviation,
                    ),
                )
            }
            check(status is PathingManager.Status.Complete) { "$scenario: ended $status" }
            check(status.legs >= minLegs) {
                "$scenario: expected at least $minLegs windows, walked ${status.legs}"
            }
            maxLegs?.let {
                check(status.legs <= it) {
                    "$scenario: expected at most $it windows, walked ${status.legs}"
                }
            }

            val path = checkNotNull(published) { "$scenario: nothing published" }
            // Every plan the body walked, not just the last one. A journey that stopped
            // and replanned ends on a short final leg, and asking that leg whether the
            // trip pressed jump answers about the last few blocks instead.
            val walked = PathingManager.executed.ifEmpty { listOf(path) }
            check(path.dependencies().isNotEmpty()) { "$scenario: no voxel dependencies published" }
            println(
                "[pathing-diag] $scenario: sprint=${path.parameters.sprint} " +
                    "frames=${path.plan.tape.frameCount} attempts=${path.attempts} " +
                    "edges=${path.route.edges.groupingBy { it.movement }.eachCount()} " +
                    "collisions=${path.plan.frames.filter { it.state.horizontalCollision }.map { frame ->
                        "${frame.index}@${frame.state.position}"
                    }}",
            )
            // Motion quality over the whole journey: wander that hides inside a
            // passing frames-per-block number shows up as path stretch (walked length
            // over straight displacement) and turn churn. These are what a watching
            // human judges; the count gates alone kept passing walks that looked bad.
            run {
                var pathLength = 0.0
                var turn = 0.0
                var frames = 0
                // Adopted improvements overlap: dedupe to the longest tape per start,
                // like the frame metric, or every improvement re-counts its prefix.
                val distinct = walked.groupBy { it.plan.initialState.position }
                    .map { (_, group) -> group.maxBy { it.plan.tape.frameCount } }
                distinct.forEach { leg ->
                    val states = leg.plan.frames.map { it.state }
                    frames += states.size
                    states.zipWithNext().forEach { (a, b) ->
                        pathLength += kotlin.math.hypot(
                            b.position.x - a.position.x, b.position.z - a.position.z,
                        )
                        turn += kotlin.math.abs(
                            com.lambda.interaction.managers.rotating.Rotation.wrap(
                                b.rotation.yaw - a.rotation.yaw,
                            ),
                        )
                    }
                }
                val first = distinct.first().plan.frames.first().state.position
                val last = distinct.last().plan.frames.last().state.position
                val displacement = kotlin.math.hypot(last.x - first.x, last.z - first.z)
                val stretch = if (displacement > 1.0) pathLength / displacement else 1.0
                measurementReport += "[motion] $scenario: stretch=%.2f turn/block=%.1f frames/block=%.2f".format(
                    stretch, if (displacement > 1.0) turn / displacement else 0.0,
                    if (displacement > 1.0) frames / displacement else 0.0,
                )
            }

            expectedJumpDy?.let { dy ->
                check(walked.any { leg ->
                    leg.route.edges.any { edge ->
                        edge.movement == MovementId.JUMP &&
                            edge.to.y - edge.from.y == dy
                    }
                }) {
                    "$scenario: no dy=$dy jump candidate in ${walked.flatMap { it.route.edges }}"
                }
            }
            if (requireJumpInput) {
                check(walked.any { leg -> leg.plan.tape.asList().any { it.jump } }) {
                    "$scenario: certified tape never pressed jump"
                }
            }
            val gapLaunches = walked.maxOf { leg -> leg.plan.tape.asList().count { it.jump } }
            check(gapLaunches >= minGapLaunches) {
                "$scenario: expected at least $minGapLaunches jump launches, got $gapLaunches"
            }
            check(walked.maxOf { it.controlSegments } >= minContinuousSegments) {
                "$scenario: expected at least $minContinuousSegments continuous control segments, " +
                    "got ${walked.map { it.controlSegments }}"
            }
            if (requireMovingSplices) {
                check(walked.any { it.spliceFrames.isNotEmpty() }) {
                    "$scenario: no predicted splice frames published"
                }
                walked.forEach { leg ->
                    check(leg.spliceFrames.all { frame ->
                        leg.plan.frames[frame - 1].state.velocity.horizontalLength() > 0.012
                    }) {
                        "$scenario: an internal splice discarded momentum at ${leg.spliceFrames}"
                    }
                }
            }

            check(PathingManager.maxDeviation <= maxDeviation) {
                "$scenario: max deviation ${PathingManager.maxDeviation} exceeded $maxDeviation"
            }
            val frames = path.plan.frames
            var previousCollision = frames.firstOrNull()?.let { path.plan.initialState.horizontalCollision } ?: false
            var bumps = 0
            frames.forEach { frame ->
                if (frame.state.horizontalCollision && !previousCollision) bumps++
                previousCollision = frame.state.horizontalCollision
            }
            // Certified motion across the whole journey, not the last tape.
            //
            // A horizon publishes many superseding tapes per leg, so summing them all
            // would count the same motion over and over; but taking only the last one
            // measures the final *leg*, and a walk that stopped and replanned once
            // recorded 14 frames for a journey of 32. Grouping by the state each leg was
            // planned from and taking the longest tape in each group is the journey.
            val legs = walked.groupBy { it.plan.initialState.position }
                .values.map { group -> group.maxBy { it.plan.frames.size }.plan.frames }
            val planned = legs.flatten()
            var legPrevious = false
            var plannedBumps = 0
            planned.forEach { frame ->
                if (frame.state.horizontalCollision && !legPrevious) plannedBumps++
                legPrevious = frame.state.horizontalCollision
            }
            val metrics = PathingMetricSink.Run(
                    scenario = scenario,
                    success = true,
                    completionTicks = ticks,
                    trajectoryFrames = planned.size,
                    finalFrames = frames.size,
                    collisionFrames = planned.count { it.state.horizontalCollision },
                    bumps = plannedBumps,
                    launchMarginFrames = path.launchMarginFrames,
                    // Time to the *first* published tape: how long the body stood still.
                    // The last tape's own age is not latency at all under a receding
                    // horizon -- the arriving publication is made at the end of the walk,
                    // so reading it measured the journey rather than the wait before it.
                    planLatencyMs = walked.first().planMillis,
                    maxReplayDeviation = PathingManager.maxDeviation,
                )
            PathingMetricSink.record(metrics)
            PathingMetricSink.assertWithinBaseline(metrics)
            PathingManager.clear()
        }
    }

    private fun PublishedPath.dependencies() = plan.dependencies

    fun assertMovementReplay(
        context: ClientGameTestContext,
        scenario: String,
        tape: List<MovementSimulationInput>,
    ) {
        lateinit var previousInput: Input
        lateinit var expected: List<MovementSimulationState>

        context.runOnClient<IllegalStateException> {
            val player = Lambda.mc.player ?: error("Missing client player")
            check(player.isOnGround) { "Movement replay player did not settle on the test platform" }

            previousInput = player.input
            val initial = MovementSimulationState.from(player)
            val profile = PlayerPhysicsProfile.capture(player)
            val center = player.blockPos
            val snapshot = SnapshotSimulationEnvironment.capture(
                world = player.entityWorld,
                player = player,
                bounds = SimulationSnapshotBounds(
                    minX = center.x - 6,
                    minY = center.y - 5,
                    minZ = center.z - 6,
                    maxX = center.x + 6,
                    maxY = center.y + 6,
                    maxZ = center.z + 10,
                ),
            )

            val liveSimulator = MovementSimulator(player, initial)
            val liveExpected = tape.map { liveSimulator.tickMovement(it).simulator.state }
            expected = CompletableFuture.supplyAsync {
                val snapshotSimulator = MovementSimulator(profile, snapshot, initial)
                tape.map { snapshotSimulator.tickMovement(it).simulator.state }
            }.join()

            liveExpected.zip(expected).forEachIndexed { frame, (live, captured) ->
                assertNear(live.position.x, captured.position.x, "$scenario snapshot frame $frame position.x")
                assertNear(live.position.y, captured.position.y, "$scenario snapshot frame $frame position.y")
                assertNear(live.position.z, captured.position.z, "$scenario snapshot frame $frame position.z")
                assertNear(live.velocity.x, captured.velocity.x, "$scenario snapshot frame $frame velocity.x")
                assertNear(live.velocity.y, captured.velocity.y, "$scenario snapshot frame $frame velocity.y")
                assertNear(live.velocity.z, captured.velocity.z, "$scenario snapshot frame $frame velocity.z")
                check(live.onGround == captured.onGround) {
                    "$scenario snapshot frame $frame onGround differs"
                }
            }
        }

        tape.forEachIndexed { frame, input ->
            context.runOnClient<IllegalStateException> {
                val player = Lambda.mc.player ?: error("Missing client player")
                input.rotation?.let { rotation ->
                    player.yaw = rotation.yawF
                    player.pitch = rotation.pitchF
                }
                player.input = buildMovementInput(
                    forward = input.forward,
                    strafe = input.strafe,
                    jump = input.jump,
                    sneak = input.sneak,
                    sprint = input.sprint,
                )
            }

            context.waitTick()

            context.runOnClient<IllegalStateException> {
                val player = Lambda.mc.player ?: error("Missing client player")
                val predicted = expected[frame]
                assertNear(predicted.position.x, player.x, "$scenario frame $frame position.x")
                assertNear(predicted.position.y, player.y, "$scenario frame $frame position.y")
                assertNear(predicted.position.z, player.z, "$scenario frame $frame position.z")
                assertNear(predicted.velocity.x, player.velocity.x, "$scenario frame $frame velocity.x")
                assertNear(predicted.velocity.y, player.velocity.y, "$scenario frame $frame velocity.y")
                assertNear(predicted.velocity.z, player.velocity.z, "$scenario frame $frame velocity.z")
                check(predicted.onGround == player.isOnGround) {
                    "$scenario frame $frame onGround: expected ${predicted.onGround}, actual ${player.isOnGround}"
                }
                assertNear(
                    predicted.boundingBox.maxY, player.boundingBox.maxY,
                    "$scenario frame $frame box.maxY (pose)",
                )
                check(predicted.isSprinting == player.isSprinting) {
                    "$scenario frame $frame isSprinting: expected ${predicted.isSprinting}, " +
                        "actual ${player.isSprinting}"
                }
                check(predicted.collidedSoftly == player.collidedSoftly) {
                    "$scenario frame $frame collidedSoftly: expected ${predicted.collidedSoftly}, " +
                        "actual ${player.collidedSoftly}"
                }
                // The block the player stands on, and the block vanilla reads
                // friction from. Both are derived, not observed, so a simulator
                // can drift on them while position and velocity still agree --
                // which is exactly how this went unnoticed.
                check(predicted.supportingBlockPos == player.supportingBlockPos.getOrNull()) {
                    "$scenario frame $frame supportingBlockPos: expected ${predicted.supportingBlockPos}, " +
                        "actual ${player.supportingBlockPos.getOrNull()}"
                }
                check(predicted.velocityAffectingPos == player.velocityAffectingPos) {
                    "$scenario frame $frame velocityAffectingPos: expected ${predicted.velocityAffectingPos}, " +
                        "actual ${player.velocityAffectingPos}"
                }
            }
        }

        context.runOnClient<IllegalStateException> {
            Lambda.mc.player?.input = previousInput
        }
    }

    private fun assertNear(expected: Double, actual: Double, label: String) {
        check(abs(expected - actual) <= MOVEMENT_EPSILON) {
            "$label: expected $expected, actual $actual"
        }
    }

    inline fun ClientGameTestContext.unit(label: String, crossinline block: SafeContext.() -> Boolean) {
        waitTick()

        runOnClient<IllegalStateException> {
            val asserted = runSafe(block)
                ?: throw IllegalStateException("Could not run in a safe context")

            check(asserted) { "Assertion failed: $label" }
        }
    }

    const val MOVEMENT_EPSILON = 1.0E-6

    /** Euclidean counterpart of [MOVEMENT_EPSILON]: sqrt(3) * per-axis, rounded up. */
    const val REPLAY_DEVIATION_EPSILON = 2.0E-6

    /** Stricter measured Euclidean gate than the cursor's frame-scaled per-axis bound. */
    const val EXECUTION_TOLERANCE = 1.7E-5

    /** Plan latency plus tape length; a walk that needs longer has already failed. */
    const val MAX_PATHING_TICKS = 1200

    /** Ticks without observable progress before a stuck event is recorded. */
    private const val STUCK_TICKS = 60

    const val TELEPORT_SETTLE_TICKS = 40
    const val REQUIRED_SETTLED_TICKS = 3
    const val SETTLED_SPEED_SQUARED = 1.0E-12

    const val BEDROCK_FIELD_LENGTH = 40
    const val BEDROCK_FIELD_HALF_WIDTH = BedrockFieldLayout.HALF_WIDTH
    const val BEDROCK_FIELD_SEED = BedrockFieldLayout.SEED
}
