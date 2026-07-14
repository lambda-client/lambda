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

package com.lambda.bench

import com.lambda.Lambda.LOG
import com.lambda.pathing.goal.TraversalGoal
import com.lambda.pathing.manager.PathfinderExecutor
import com.lambda.pathing.manager.PathfinderManager
import com.lambda.pathing.manager.TraversalHandle
import com.lambda.pathing.metrics.PlannerMetrics
import com.lambda.threading.runSafe
import com.lambda.threading.runSafeAutomated
import com.lambda.util.world.toBlockPos
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.util.math.Vec3d
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Aggregated outcome of one scenario run — one row of the suite report. */
data class ScenarioReport(
    val name: String,
    val expectSuccess: Boolean,
    val gated: Boolean,
    val reachedGoal: Boolean,
    val failedCleanly: Boolean,
    val ticks: Int,
    val plannedNodes: Int,
    val coarseNodes: Int,
    val plannedLength: Double,
    val processedNodes: Int,
    val graphSize: Int,
    val refinementSavedPercent: Double,
    val traveledLength: Double,
    val pathDeviationMean: Double,
    val pathDeviationMax: Double,
    val jumpInputTicks: Int,
    val airborneJumps: Int,
    val flightToggled: Boolean,
    val maxLostTicks: Int,
    val replansRequested: Int,
    val firstReadyTick: Int,
    val firstFollowingTick: Int,
    val followingTicks: Int,
    val planningPauseTicks: Int,
    val planningPauseBursts: Int,
    val longestPlanningPauseTicks: Int,
    val movementStallTicks: Int,
    val movementStallBursts: Int,
    val longestMovementStallTicks: Int,
    val executorLostTicks: Int,
    val executorLostBursts: Int,
    val longestExecutorLostBurstTicks: Int,
    val jumpLandingSuccesses: Int,
    val jumpLandingFailures: Int,
    /** Following ticks spent scraping a wall (horizontal collision). */
    val wallCollisionTicks: Int,
    /** Worst ticks from a scheduled mutation to the first published plan
     *  revision (pathLen change) — the replan-reaction latency. */
    val replanLatencyTicks: Int,
    /** Following ticks with an airborne head bonk (vertical collision off-ground). */
    val headBonkTicks: Int,
    /** Mean/max tick-aligned deviation from launch-time flight predictions (sim divergence). */
    val arcDevMean: Double,
    val arcDevMax: Double,
    /** Mean/max nearest-distance to the planned arcs while airborne (plan match). */
    val planDevMean: Double,
    val planDevMax: Double,
    val maxFirstFollowingTick: Int?,
    val maxCompletionTicks: Int?,
    val maxPlanningPauseTicks: Int?,
    val maxLongestPlanningPauseTicks: Int?,
    val maxLongestMovementStallTicks: Int?,
    val maxLongestExecutorLostBurstTicks: Int?,
    val maxReplansRequested: Int?,
    val minJumpLandingSuccessRate: Double?,
    val maxReplanLatencyTicks: Int?,
    val minAllowedY: Double?,
    val minPlayerY: Double,
    val parkourExpectedLandings: Int,
    val parkourMatchedTargets: Int,
    val parkourMatchedLandings: Int,
    val parkourMismatches: Int,
    val parkourRetries: Int,
    val finalStatus: String,
    val failureReason: String?,
    val endDistanceToGoal: Double,
    val coarsePathDump: String,
    val plannerStats: BenchPlannerMetrics.PlannerStats = BenchPlannerMetrics.PlannerStats.EMPTY,
    /** Every jump this scenario executed, for the suite-wide rating matrix. */
    val jumpRecords: List<JumpRecord> = emptyList(),
) {
    val parkourContractPassed: Boolean get() =
        parkourExpectedLandings == 0 ||
            (parkourMatchedTargets == parkourExpectedLandings &&
                parkourMatchedLandings == parkourExpectedLandings &&
                parkourMismatches == 0 && parkourRetries == 0)

    // In an unbounded world a truly unreachable goal manifests as
    // Partial-forever (the backward search can never exhaust the goal's
    // component), so expect-failure scenarios pass when the agent neither
    // reaches the goal nor misbehaves (creative-flight toggle).
    val jumpLandingSuccessRate: Double
        get() = if (jumpLandingSuccesses + jumpLandingFailures == 0) 1.0
        else jumpLandingSuccesses.toDouble() / (jumpLandingSuccesses + jumpLandingFailures)

    val qualityPassed: Boolean get() =
        (maxCompletionTicks == null || ticks <= maxCompletionTicks) &&
            (maxFirstFollowingTick == null || firstFollowingTick in 1..maxFirstFollowingTick) &&
            (maxPlanningPauseTicks == null || planningPauseTicks <= maxPlanningPauseTicks) &&
            (maxLongestPlanningPauseTicks == null || longestPlanningPauseTicks <= maxLongestPlanningPauseTicks) &&
            (maxLongestMovementStallTicks == null || longestMovementStallTicks <= maxLongestMovementStallTicks) &&
            (maxLongestExecutorLostBurstTicks == null || longestExecutorLostBurstTicks <= maxLongestExecutorLostBurstTicks) &&
            (maxReplansRequested == null || replansRequested <= maxReplansRequested) &&
            (minJumpLandingSuccessRate == null || jumpLandingSuccessRate >= minJumpLandingSuccessRate) &&
            (maxReplanLatencyTicks == null || replanLatencyTicks <= maxReplanLatencyTicks) &&
            (minAllowedY == null || minPlayerY >= minAllowedY) &&
            parkourContractPassed

    val passed: Boolean get() =
        (if (expectSuccess) reachedGoal else !reachedGoal && !flightToggled) && qualityPassed

    /** A harness/incremental-search failure, not an executor baseline miss. */
    val plannerStalled: Boolean
        get() = expectSuccess && !reachedGoal && finalStatus == TraversalHandle.Status.Partial.toString() && plannedNodes == 0

    /** Movement beyond the planned line, in percent of the planned length. */
    val movementWastePercent: Double
        get() = if (plannedLength > 0.5) ((traveledLength - plannedLength) / plannedLength * 100.0).coerceAtLeast(0.0) else 0.0

    fun summaryLine(): String = buildString {
        append(if (passed) "PASS" else "FAIL")
        if (!gated) append(" [baseline]")
        append("  ").append(name.padEnd(26))
        append(" reached=").append(reachedGoal)
        append(" ticks=").append(ticks)
        append(" dev=").append("%.2f/%.2f".format(pathDeviationMean, pathDeviationMax))
        append(" waste=").append("%.0f%%".format(movementWastePercent))
        append(" nodes=").append(plannedNodes)
        append(" jumps(in/air)=").append(jumpInputTicks).append('/').append(airborneJumps)
        append(" land=").append(jumpLandingSuccesses).append('/').append(jumpLandingSuccesses + jumpLandingFailures)
        append(" coll(wall/head)=").append(wallCollisionTicks).append('/').append(headBonkTicks)
        append(" arc=").append("%.2f/%.2f".format(arcDevMean, arcDevMax))
        append(" plan=").append("%.2f/%.2f".format(planDevMean, planDevMax))
        append(" firstFollow=").append(firstFollowingTick)
        append(" pause=").append(planningPauseTicks).append('/').append(longestPlanningPauseTicks)
        append(" stall=").append(movementStallTicks).append('/').append(longestMovementStallTicks)
        append(" lost=").append(executorLostTicks).append('/').append(longestExecutorLostBurstTicks)
        append(" initUs=").append(plannerStats.initialWallMicros)
        append(" repairs=").append(plannerStats.repairs)
        append("(p50=").append(plannerStats.repairWallUsP50).append("us)")
        plannerStats.computeCauses["budget_continuation"]?.let { append(" continue=").append(it) }
        if (replanLatencyTicks > 0) append(" replanLatency=").append(replanLatencyTicks)
        if (plannerStats.chunkVisibilityRebuilds > 0) append(" chunkRebuilds=").append(plannerStats.chunkVisibilityRebuilds)
        if (flightToggled) append(" FLIGHT-TOGGLED")
        if (plannerStalled) append(" PLANNER-STALLED")
        if (!qualityPassed) append(" QUALITY-FAILED")
        append(" status=").append(finalStatus)
        if (parkourExpectedLandings > 0) {
            append(" parkour(target/land/expected)=")
                .append(parkourMatchedTargets).append('/')
                .append(parkourMatchedLandings).append('/')
                .append(parkourExpectedLandings)
            if (parkourRetries > 0) append(" retries=").append(parkourRetries)
            if (parkourMismatches > 0) append(" mismatches=").append(parkourMismatches)
        }
        failureReason?.let { append(" reason=").append(it) }
        if (!passed) append("\n      coarse=").append(coarsePathDump)
    }

    fun toJson(): String = buildString {
        append('{')
        append("\"name\":\"").append(name).append('"')
        append(",\"passed\":").append(passed)
        append(",\"expectSuccess\":").append(expectSuccess)
        append(",\"gated\":").append(gated)
        append(",\"reachedGoal\":").append(reachedGoal)
        append(",\"failedCleanly\":").append(failedCleanly)
        append(",\"ticks\":").append(ticks)
        append(",\"plannedNodes\":").append(plannedNodes)
        append(",\"coarseNodes\":").append(coarseNodes)
        append(",\"plannedLength\":").append("%.3f".format(plannedLength))
        append(",\"processedNodes\":").append(processedNodes)
        append(",\"graphSize\":").append(graphSize)
        append(",\"refinementSavedPercent\":").append("%.2f".format(refinementSavedPercent))
        append(",\"traveledLength\":").append("%.3f".format(traveledLength))
        append(",\"movementWastePercent\":").append("%.2f".format(movementWastePercent))
        append(",\"pathDeviationMean\":").append("%.3f".format(pathDeviationMean))
        append(",\"pathDeviationMax\":").append("%.3f".format(pathDeviationMax))
        append(",\"jumpInputTicks\":").append(jumpInputTicks)
        append(",\"airborneJumps\":").append(airborneJumps)
        append(",\"flightToggled\":").append(flightToggled)
        append(",\"maxLostTicks\":").append(maxLostTicks)
        append(",\"replansRequested\":").append(replansRequested)
        append(",\"firstReadyTick\":").append(firstReadyTick)
        append(",\"firstFollowingTick\":").append(firstFollowingTick)
        append(",\"followingTicks\":").append(followingTicks)
        append(",\"planningPauseTicks\":").append(planningPauseTicks)
        append(",\"planningPauseBursts\":").append(planningPauseBursts)
        append(",\"longestPlanningPauseTicks\":").append(longestPlanningPauseTicks)
        append(",\"minPlayerY\":").append("%.3f".format(minPlayerY))
        append(",\"parkourExpectedLandings\":").append(parkourExpectedLandings)
        append(",\"parkourMatchedTargets\":").append(parkourMatchedTargets)
        append(",\"parkourMatchedLandings\":").append(parkourMatchedLandings)
        append(",\"parkourMismatches\":").append(parkourMismatches)
        append(",\"parkourRetries\":").append(parkourRetries)
        append(",\"parkourContractPassed\":").append(parkourContractPassed)
        append(",\"movementStallTicks\":").append(movementStallTicks)
        append(",\"movementStallBursts\":").append(movementStallBursts)
        append(",\"longestMovementStallTicks\":").append(longestMovementStallTicks)
        append(",\"executorLostTicks\":").append(executorLostTicks)
        append(",\"executorLostBursts\":").append(executorLostBursts)
        append(",\"longestExecutorLostBurstTicks\":").append(longestExecutorLostBurstTicks)
        append(",\"jumpLandingSuccesses\":").append(jumpLandingSuccesses)
        append(",\"jumpLandingFailures\":").append(jumpLandingFailures)
        append(",\"jumpLandingSuccessRate\":").append("%.3f".format(jumpLandingSuccessRate))
        append(",\"wallCollisionTicks\":").append(wallCollisionTicks)
        append(",\"replanLatencyTicks\":").append(replanLatencyTicks)
        append(",\"headBonkTicks\":").append(headBonkTicks)
        append(",\"arcDevMean\":").append("%.3f".format(arcDevMean))
        append(",\"arcDevMax\":").append("%.3f".format(arcDevMax))
        append(",\"planDevMean\":").append("%.3f".format(planDevMean))
        append(",\"planDevMax\":").append("%.3f".format(planDevMax))
        append(",\"maxCompletionTicks\":").append(maxCompletionTicks ?: "null")
        append(",\"qualityPassed\":").append(qualityPassed)
        append(",\"finalStatus\":\"").append(finalStatus).append('"')
        append(",\"failureReason\":").append(failureReason?.let { "\"$it\"" } ?: "null")
        append(",\"endDistanceToGoal\":").append("%.3f".format(endDistanceToGoal))
        append(',').append(plannerStats.toJsonFields())
        append(",\"coarsePath\":\"").append(coarsePathDump).append('"')
        append('}')
    }
}

/**
 * Executes [TraversalScenario]s inside a client gametest and records per-tick
 * telemetry as JSON Lines under `logs/benchmarks/<run-id>/` in the game
 * directory, plus a `summary.json` for the whole suite.
 */
object ScenarioRunner {
    /** Horizontal speed below which the agent counts as standing still. */
    private const val ORACLE_STOP_SPEED = 0.10

    /** Published-path length change that counts as a plan revision for the
     *  replan-latency clock (below this, refinement jitter). */
    private const val REPLAN_PATH_CHANGE_EPSILON = 0.5

    // Landing success = feet within the launch-sim gate's own acceptance
    // (0.9 of the target center). The former 1.15 counted block-off
    // touchdowns as successes — green numbers over visibly sloppy jumps.
    private const val LANDING_HORIZONTAL_TOLERANCE = 0.9
    private const val LANDING_VERTICAL_TOLERANCE = 0.30

    // The worker's carry-landing acceptance (±1 block, same level) for
    // momentum-class jumps; see the success computation.
    private const val LANDING_CARRY_TOLERANCE = 1.5
    // Template jumps cannot approach four blocks. At this distance the edge
    // is necessarily discovery-simulated, whose fast-entry contract permits
    // a same-platform ±1-block carry landing. Using 4.4 here misclassified a
    // validated 4.18-block bedrock jump as a strict node-centre miss.
    private const val MOMENTUM_JUMP_MIN_DISTANCE = 4.0
    private const val MOVEMENT_STALL_SPEED = 0.01
    private const val PARKOUR_FOOTPRINT_HALF_WIDTH = 0.31

    private data class PendingJump(
        val tick: Int,
        val launch: Vec3d,
        val target: Vec3d?,
        val segmentType: String,
        val launchSpeed: Double,
        val segmentIndex: Int,
        /** The planned edge this launch was serving, from executor telemetry. */
        val edgeStart: Vec3d? = null,
        val edgeEnd: Vec3d? = null,
        /** Ticks spent on this segment before the jump finally fired. */
        val approachTicks: Int = 0,
        /** Of those, grounded ticks where the jump gate actively refused. */
        val refusalTicks: Int = 0,
        /** The refusal repeated most at the lip — the motion-waste fingerprint. */
        val dominantGate: String = "",
        /** Heading change from the previous planned segment into this one. */
        val approachTurnDegrees: Double? = null,
        /** 1 for the first launch at this edge; 2+ means an earlier one missed. */
        val attemptIndex: Int = 1,
    )

    /** Heading change between two consecutive planned segments, in degrees. */
    private fun turnBetween(previous: Vec3d, current: Vec3d): Double? {
        val a = hypot(previous.x, previous.z)
        val b = hypot(current.x, current.z)
        if (a < 1.0E-6 || b < 1.0E-6) return null
        val cosine = ((previous.x * current.x + previous.z * current.z) / (a * b)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(kotlin.math.acos(cosine))
    }

    /**
     * A gate string is a REFUSAL unless it is the executor committing to fire.
     * Everything else — Misaligned, SimShort, CommitBuild, PastTakeoff,
     * BelowEnvelope, the walk-back dance — is the executor declining to launch
     * from the state the path delivered, which is the cost this program exists
     * to drive to zero.
     */
    private fun isRefusalGate(gate: String): Boolean = gate.isNotEmpty() &&
        !gate.contains("Commanded") &&
        !gate.contains("EdgeForced") &&
        gate != "noCommand" &&
        gate != "airborne" &&
        gate != "chainAirborne" &&
        gate != "notWalk" &&
        gate != "noRiseAhead" &&
        gate != "alreadyUp"

    private data class JumpLanding(
        val attempt: PendingJump,
        val tick: Int,
        val position: Vec3d,
        val success: Boolean,
        val horizontalError: Double?,
        val verticalError: Double?,
        /** Flight-mean/max tick-aligned deviation from the launch prediction. */
        val arcErrMean: Double? = null,
        val arcErrMax: Double? = null,
        /** Flight-mean/max nearest-distance to the planned arc. */
        val planErrMean: Double? = null,
        val planErrMax: Double? = null,
    )

    /** Per-flight accumulator for the trajectory-match samples. */
    private class FlightErrors {
        var arcSum = 0.0; var arcMax = 0.0; var arcN = 0
        var planSum = 0.0; var planMax = 0.0; var planN = 0
        fun add(arc: Double?, plan: Double?) {
            arc?.let { arcSum += it; arcN++; if (it > arcMax) arcMax = it }
            plan?.let { planSum += it; planN++; if (it > planMax) planMax = it }
        }
        val arcMean: Double? get() = if (arcN > 0) arcSum / arcN else null
        val planMean: Double? get() = if (planN > 0) planSum / planN else null
    }

    private val runId: String = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())

    val outputDir: File by lazy {
        FabricLoader.getInstance().gameDir.resolve("logs/benchmarks/$runId").toFile().apply { mkdirs() }
    }

    fun ClientGameTestContext.runScenario(scenario: TraversalScenario, server: TestServerContext): ScenarioReport {
        // --- Fixture + placement ---
        runOnClient<IllegalStateException> { PathfinderManager.cancelActiveTraversal() }
        scenario.fixture.forEach(server::runCommand)
        scenario.serverFixture?.let { fixture ->
            server.runOnServer<IllegalStateException> { minecraftServer -> fixture(minecraftServer) }
        }
        server.runCommand("/tp Steve ${scenario.start.x} ${scenario.start.y} ${scenario.start.z} ${scenario.startYaw} 0")
        // Each case starts from the same survival sprint eligibility instead
        // of inheriting hunger consumed by earlier scenarios in this world.
        server.runCommand("/effect give Steve minecraft:saturation 1 10 true")
        server.runCommand("/effect give Steve minecraft:instant_health 1 10 true")
        waitTicks(10) // chunk/physics settle after fill + teleport

        val goalCenter = Vec3d.ofBottomCenter(scenario.goal.toBlockPos())

        scenario.probeNode?.let { probe ->
            val edges = computeOnClient<String, IllegalStateException> {
                runSafe {
                    com.lambda.pathing.movement.WalkingMovementModel
                        .successors(com.lambda.worldview.LiveWorldView(world), probe, BenchPlannerConfig(allowJump = scenario.allowJump))
                        .entries.joinToString(" ") { (node, cost) ->
                            val b = node.toBlockPos()
		                    "(${b.x},${b.y},${b.z})=${"%.2f".format(cost)}"
                        }
                } ?: "unsafe"
            }
            val pb = probe.toBlockPos()
            LOG.info("[Bench] probe successors of (${pb.x},${pb.y},${pb.z}): $edges")
        }

        // --- Request traversal ---
        val plannerMetrics = BenchPlannerMetrics()
        PlannerMetrics.sink = plannerMetrics
        val requested = computeOnClient<Boolean, IllegalStateException> {
            val handle = with(PathfinderManager) {
                runSafeAutomated {
                    requestTraversal(
                        goal = TraversalGoal.Block(scenario.goal),
                        owner = ScenarioRunner,
                        config = BenchPlannerConfig(allowJump = scenario.allowJump),
                    )
                }
            }
            handle != null
        }

        val telemetry = StringBuilder()
        var ticks = 0
        var reached = false
        var failedCleanly = false
        var jumpInputTicks = 0
        var airborneJumps = 0
        var flightToggled = false
        var maxLostTicks = 0
        var maxReplansRequested = 0
        var wasOnGround = true
        var airborneImpulseApplied = false
        var pendingMutationTick = -1
        var preMutationPathLen = Double.NaN
        var replanLatencyMax = 0
        var wasJumpInput = false
        var traveled = 0.0
        var previousX = Double.NaN
        var previousZ = Double.NaN
        var deviationSum = 0.0
        var deviationMax = 0.0
        var deviationSamples = 0
        var referencePlannedLength = 0.0
        var firstReadyTick = 0
        var firstFollowingTick = 0
        var followingTicks = 0
        var planningPauseTicks = 0
        var planningPauseBursts = 0
        var currentPlanningPause = 0
        var longestPlanningPauseTicks = 0
        var movementStallTicks = 0
        var movementStallBursts = 0
        var currentMovementStall = 0
        var longestMovementStallTicks = 0
        var executorLostTicks = 0
        var executorLostBursts = 0
        var currentExecutorLost = 0
        var longestExecutorLostBurstTicks = 0
        var pendingJump: PendingJump? = null
        var flightErrors = FlightErrors()
        val jumpLandings = ArrayList<JumpLanding>()
        // Approach quality, tracked per active segment: how long the executor
        // sat on the takeoff segment and how much of that it spent refusing to
        // launch. Keyed on the planned edge's landing node, which survives the
        // segment renumbering a replan causes.
        var approachSegment = Int.MIN_VALUE
        var approachEntryTick = 0
        var approachRefusalTicks = 0
        val approachGates = HashMap<String, Int>()
        val edgeAttempts = HashMap<String, Int>()
        // Direction of the segment the executor is on, and of the one before
        // it: their angle is how sharply the PLAN turns into this takeoff.
        var currentSegmentDirection: Vec3d? = null
        var previousSegmentDirection: Vec3d? = null
        var wallCollisionTicks = 0
        var headBonkTicks = 0
        var minPlayerY = scenario.start.y
        val parkourExpected = scenario.parkourContract?.landings.orEmpty()
        val parkourAttempts = IntArray(parkourExpected.size)
        var parkourIndex = 0
        var parkourMatchedTargets = 0
        var parkourMatchedLandings = 0
        var parkourMismatches = 0
        var parkourRetries = 0

        if (requested) {
            while (ticks < scenario.timeoutTicks) {
                waitTick()
                ticks++

                scenario.mutations.forEach { (tick, command) ->
                    if (tick == ticks) {
                        server.runCommand(command)
                        pendingMutationTick = ticks
                    }
                }

                scenario.perturbCameraYaw?.let { yaw ->
                    // Simulate the user moving the camera while the executor
                    // drives: the rotation-manager split must keep movement
                    // unaffected, so the scenario still has to pass.
                    runOnClient<IllegalStateException> {
                        runSafe { player.yaw = yaw }
                    }
                }

                scenario.firstAirborneVelocityImpulse?.let { impulse ->
                    if (!airborneImpulseApplied && wasOnGround) {
                        airborneImpulseApplied = computeOnClient<Boolean, IllegalStateException> {
                            runSafe {
                                if (player.isOnGround) false
                                else {
                                    player.velocity = player.velocity.add(impulse)
                                    true
                                }
                            } ?: false
                        }
                    }
                }

                if (scenario.airborneVelocityImpulseCycle.isNotEmpty() && wasOnGround) {
                    val impulse = scenario.airborneVelocityImpulseCycle[
                        airborneJumps % scenario.airborneVelocityImpulseCycle.size
                    ]
                    runOnClient<IllegalStateException> {
                        runSafe {
                            if (!player.isOnGround) player.velocity = player.velocity.add(impulse)
                        }
                    }
                }

                val sample = computeOnClient<String, IllegalStateException> {
                    runSafe {
                        val state = PathfinderExecutor.state
                        val handle = PathfinderManager.activeTraversal
                        val pos = player.pos
                        val distance = hypot(hypot(pos.x - goalCenter.x, pos.z - goalCenter.z), pos.y - goalCenter.y)
                        buildString {
                            append('{')
                            append("\"t\":").append(ticks)
                            append(",\"x\":").append("%.3f".format(pos.x))
                            append(",\"y\":").append("%.3f".format(pos.y))
                            append(",\"z\":").append("%.3f".format(pos.z))
                            append(",\"vy\":").append("%.3f".format(player.velocity.y))
                            append(",\"spd\":").append("%.4f".format(hypot(player.velocity.x, player.velocity.z)))
                            append(",\"ground\":").append(player.isOnGround)
                            append(",\"hColl\":").append(player.horizontalCollision)
                            append(",\"vColl\":").append(player.verticalCollision && !player.isOnGround)
                            append(",\"flying\":").append(player.abilities.flying)
                            append(",\"status\":\"").append(state.status).append('"')
                            append(",\"seg\":").append(state.segmentIndex)
                            append(",\"segType\":\"").append(state.segmentType ?: "").append('"')
                            // The PLANNED edge under this jump. Classifying by
                            // launch->target displacement instead would fold the
                            // executor's own launch-position error into the class
                            // key; the matrix must be keyed on what was planned.
                            state.segmentStart?.let { start ->
                                append(",\"segStartX\":").append("%.3f".format(start.x))
                                append(",\"segStartY\":").append("%.3f".format(start.y))
                                append(",\"segStartZ\":").append("%.3f".format(start.z))
                            }
                            state.segmentEnd?.let { end ->
                                append(",\"segEndX\":").append("%.3f".format(end.x))
                                append(",\"segEndY\":").append("%.3f".format(end.y))
                                append(",\"segEndZ\":").append("%.3f".format(end.z))
                            }
                            append(",\"jump\":").append(state.jumpCommand)
                            append(",\"jumpCmdGate\":\"").append(state.jumpCommandGate).append('"')
                            append(",\"jumpInGate\":\"").append(state.jumpInputGate).append('"')
                            append(",\"sprintCmd\":").append(state.sprintCommand)
                            append(",\"sprinting\":").append(player.isSprinting)
                            append(",\"fwd\":").append("%.2f".format(state.commandedForward))
                            append(",\"strafe\":").append("%.2f".format(state.commandedStrafe))
                            append(",\"lost\":").append(state.lostTicks)
                            append(",\"replans\":").append(state.replansRequested)
                            append(",\"latErr\":").append("%.3f".format(state.lateralError))
                            append(",\"dist\":").append("%.3f".format(distance))
                            append(",\"handleStatus\":\"").append(handle?.status ?: "None").append('"')
                            append(",\"pathLen\":").append("%.3f".format(handle?.pathLength ?: 0.0))
                            state.jumpTarget?.let { target ->
                                append(",\"jumpTargetX\":").append("%.3f".format(target.x))
                                append(",\"jumpTargetY\":").append("%.3f".format(target.y))
                                append(",\"jumpTargetZ\":").append("%.3f".format(target.z))
                            }
                            state.arcTickError?.let { append(",\"arcErr\":").append("%.3f".format(it)) }
                            state.plannedArcError?.let { append(",\"planErr\":").append("%.3f".format(it)) }
                            append('}')
                        }
                    } ?: "{}"
                }
                telemetry.appendLine(sample)

                // Cheap stream-side aggregation from the JSON we just built.
                // Jump issuance is read from the input gate: the sampled
                // jumpCommand flag gets overwritten by the end-of-tick state
                // rebuild and is unreliable.
                val onGround = "\"ground\":true" in sample
                sample.numberField("y")?.let { if (it < minPlayerY) minPlayerY = it }
                val jumpInput = "\"jumpInGate\":\"issued\"" in sample
                val flying = "\"flying\":true" in sample
                val status = sample.substringAfter("\"status\":\"").substringBefore('"')
                val handleStatusNow = sample.substringAfter("\"handleStatus\":\"").substringBefore('"')
                if (firstReadyTick == 0 && handleStatusNow == "Ready") firstReadyTick = ticks
                if (status == "Following") {
                    followingTicks++
                    if (firstFollowingTick == 0) firstFollowingTick = ticks
                }
                if (status == "Lost") {
                    executorLostTicks++
                    if (currentExecutorLost == 0) executorLostBursts++
                    currentExecutorLost++
                    if (currentExecutorLost > longestExecutorLostBurstTicks) {
                        longestExecutorLostBurstTicks = currentExecutorLost
                    }
                } else {
                    currentExecutorLost = 0
                }

                val planningPaused = firstFollowingTick > 0 &&
                    (handleStatusNow == "Partial" || handleStatusNow == "Planning" ||
                        status == "PartialDisabled" || status == "WaitingForPath")
                if (planningPaused) {
                    planningPauseTicks++
                    if (currentPlanningPause == 0) planningPauseBursts++
                    currentPlanningPause++
                    if (currentPlanningPause > longestPlanningPauseTicks) {
                        longestPlanningPauseTicks = currentPlanningPause
                    }
                } else {
                    currentPlanningPause = 0
                }
                val sampleSpeedForStall = sample.numberField("spd") ?: Double.MAX_VALUE
                val distanceForStall = sample.numberField("dist") ?: 0.0
                val movementStalled = status == "Following" && onGround &&
                    sampleSpeedForStall < MOVEMENT_STALL_SPEED && distanceForStall > 1.0
                if (movementStalled) {
                    movementStallTicks++
                    if (currentMovementStall == 0) movementStallBursts++
                    currentMovementStall++
                    if (currentMovementStall > longestMovementStallTicks) {
                        longestMovementStallTicks = currentMovementStall
                    }
                } else {
                    currentMovementStall = 0
                }
                if (jumpInput && !wasJumpInput) jumpInputTicks++
                if (!onGround && wasOnGround && jumpInput) airborneJumps++
                if (flying) flightToggled = true
                // Contact-quality counters: wall scrapes and airborne head
                // bonks while actively following — the sloppy-motion gauges.
                if (status == "Following") {
                    if ("\"hColl\":true" in sample) wallCollisionTicks++
                    if ("\"vColl\":true" in sample) headBonkTicks++
                }
                sample.substringAfter("\"lost\":").substringBefore(",").toIntOrNull()?.let {
                    if (it > maxLostTicks) maxLostTicks = it
                }
                sample.substringAfter("\"replans\":").substringBefore(",").toIntOrNull()?.let {
                    if (it > maxReplansRequested) maxReplansRequested = it
                }

                if (pendingJump != null) {
                    flightErrors.add(sample.numberField("arcErr"), sample.numberField("planErr"))
                }

                // Approach bookkeeping for the jump rating matrix. A segment
                // change resets the clock; every grounded tick whose gate
                // refused to launch is motion the path did not have to spend.
                val segmentNow = sample.numberField("seg")?.toInt() ?: -1
                val gateNow = sample.substringAfter("\"jumpCmdGate\":\"").substringBefore('"')
                if (segmentNow != approachSegment) {
                    approachSegment = segmentNow
                    approachEntryTick = ticks
                    approachRefusalTicks = 0
                    approachGates.clear()
                    previousSegmentDirection = currentSegmentDirection
                    currentSegmentDirection = null
                }
                if (currentSegmentDirection == null) {
                    val from = vecField(sample, "segStart")
                    val to = vecField(sample, "segEnd")
                    if (from != null && to != null) {
                        currentSegmentDirection = to.subtract(from)
                    }
                }
                if (onGround && isRefusalGate(gateNow)) {
                    approachRefusalTicks++
                    approachGates[gateNow] = (approachGates[gateNow] ?: 0) + 1
                }

                if (jumpInput && !wasJumpInput) {
                    val x = sample.numberField("x")
                    val y = sample.numberField("y")
                    val z = sample.numberField("z")
                    val targetX = sample.numberField("jumpTargetX")
                    val targetY = sample.numberField("jumpTargetY")
                    val targetZ = sample.numberField("jumpTargetZ")
                    val edgeStart = vecField(sample, "segStart")
                    val edgeEnd = vecField(sample, "segEnd")
                    if (x != null && y != null && z != null) {
                        flightErrors = FlightErrors()
                        val edgeKey = edgeEnd?.let {
                            "${it.x.roundToInt()},${it.y.roundToInt()},${it.z.roundToInt()}"
                        } ?: "seg$segmentNow"
                        val attempt = (edgeAttempts[edgeKey] ?: 0) + 1
                        edgeAttempts[edgeKey] = attempt
                        pendingJump = PendingJump(
                            tick = ticks,
                            launch = Vec3d(x, y, z),
                            target = if (targetX != null && targetY != null && targetZ != null) {
                                Vec3d(targetX, targetY, targetZ)
                            } else null,
                            edgeStart = edgeStart,
                            edgeEnd = edgeEnd,
                            approachTicks = ticks - approachEntryTick,
                            refusalTicks = approachRefusalTicks,
                            dominantGate = approachGates.maxByOrNull { it.value }?.key.orEmpty(),
                            approachTurnDegrees = previousSegmentDirection?.let { previous ->
                                currentSegmentDirection?.let { turnBetween(previous, it) }
                            },
                            attemptIndex = attempt,
                            segmentType = sample.substringAfter("\"segType\":\"").substringBefore('"'),
                            launchSpeed = sample.numberField("spd") ?: 0.0,
                            segmentIndex = sample.numberField("seg")?.toInt() ?: -1,
                        )
                        if (parkourIndex < parkourExpected.size) {
                            parkourAttempts[parkourIndex]++
                            if (parkourAttempts[parkourIndex] > 1) parkourRetries++
                            val expected = parkourExpected[parkourIndex]
                            if (targetX != null && targetY != null && targetZ != null &&
                                expected.contains(targetX, targetY, targetZ)
                            ) {
                                if (parkourAttempts[parkourIndex] == 1) parkourMatchedTargets++
                            } else {
                                parkourMismatches++
                            }
                        } else if (parkourExpected.isNotEmpty()) {
                            // An extra jump after the complete intended chain
                            // is usually an overshoot/recovery maneuver.
                            parkourMismatches++
                        }
                    }
                }
                if (onGround && !wasOnGround) {
                    pendingJump?.let { attempt ->
                        val x = sample.numberField("x")
                        val y = sample.numberField("y")
                        val z = sample.numberField("z")
                        if (x != null && y != null && z != null) {
                            val landing = Vec3d(x, y, z)
                            val target = attempt.target
                            val horizontalError = target?.let { hypot(landing.x - it.x, landing.z - it.z) }
                            val verticalError = target?.let { kotlin.math.abs(landing.y - it.y) }
                            // Momentum-class jumps (≳4.5-block displacement)
                            // are worker-validated WITH same-level carry
                            // landings (±1 block of the node); the oracle
                            // holds them to the contract that admitted them,
                            // strict node-centering to everything shorter.
                            val jumpDistance = target?.let {
                                hypot(it.x - attempt.launch.x, it.z - attempt.launch.z)
                            } ?: 0.0
                            val horizontalTolerance =
                                if (jumpDistance >= MOMENTUM_JUMP_MIN_DISTANCE) LANDING_CARRY_TOLERANCE
                                else LANDING_HORIZONTAL_TOLERANCE
                            val genericSuccess = horizontalError != null && verticalError != null &&
                                horizontalError <= horizontalTolerance &&
                                verticalError <= LANDING_VERTICAL_TOLERANCE
                            val parkourPadSuccess = parkourExpected.getOrNull(parkourIndex)?.contains(
                                landing.x, landing.y, landing.z, margin = PARKOUR_FOOTPRINT_HALF_WIDTH,
                            )
                            // Generated courses define success by exact pad
                            // contact, not distance from its chosen target
                            // node. A wide pad intentionally permits braking
                            // carry onto an adjacent block; counting that as a
                            // generic miss while the exact contract passes is
                            // contradictory telemetry.
                            val success = parkourPadSuccess ?: genericSuccess
                            jumpLandings += JumpLanding(
                                attempt, ticks, landing, success, horizontalError, verticalError,
                                arcErrMean = flightErrors.arcMean,
                                arcErrMax = flightErrors.arcMax.takeIf { flightErrors.arcN > 0 },
                                planErrMean = flightErrors.planMean,
                                planErrMax = flightErrors.planMax.takeIf { flightErrors.planN > 0 },
                            )
                            if (parkourIndex < parkourExpected.size) {
                                // Contact is valid when the player's 0.6-wide
                                // footprint overlaps the intended quartz pad;
                                // requiring its centre inside the block falsely
                                // rejected legitimate rim touchdowns.
                                if (parkourPadSuccess == true) {
                                    parkourMatchedLandings++
                                    parkourIndex++
                                } else {
                                    parkourMismatches++
                                }
                            }
                        }
                    }
                    pendingJump = null
                }
                wasOnGround = onGround
                wasJumpInput = jumpInput

                // Path-following efficiency: total ground covered (waste vs
                // the planned length) and the executor's lateral error from
                // its active segment — segment-relative, so it stays honest
                // across replans, unlike a distance to the final path.
                val sampleX = sample.substringAfter("\"x\":").substringBefore(",").toDoubleOrNull()
                val sampleZ = sample.substringAfter("\"z\":").substringBefore(",").toDoubleOrNull()
                if (sampleX != null && sampleZ != null) {
                    if (!previousX.isNaN()) traveled += hypot(sampleX - previousX, sampleZ - previousZ)
                    previousX = sampleX
                    previousZ = sampleZ
                }
                if ("\"status\":\"Following\"" in sample) {
                    sample.substringAfter("\"latErr\":").substringBefore(",").toDoubleOrNull()?.let { latErr ->
                        deviationSum += latErr
                        deviationSamples++
                        if (latErr > deviationMax) deviationMax = latErr
                    }
                }
                sample.substringAfter("\"pathLen\":").substringBefore(",").substringBefore("}").toDoubleOrNull()?.let {
                    if (it > referencePlannedLength) referencePlannedLength = it
                    // Replan-reaction latency: ticks from a scheduled world
                    // mutation until the published plan first changes. Gated
                    // on the mutation scenarios — the async handoff bug that
                    // let the executor push a wall for 40 ticks while a
                    // computed reroute never landed is exactly this number.
                    if (pendingMutationTick >= 0) {
                        if (preMutationPathLen.isNaN()) {
                            preMutationPathLen = it
                        } else if (kotlin.math.abs(it - preMutationPathLen) > REPLAN_PATH_CHANGE_EPSILON) {
                            val latency = ticks - pendingMutationTick
                            if (latency > replanLatencyMax) replanLatencyMax = latency
                            pendingMutationTick = -1
                            preMutationPathLen = Double.NaN
                        }
                    } else {
                        preMutationPathLen = it
                    }
                }

                val distance = sample.substringAfter("\"dist\":").substringBefore(",").toDoubleOrNull()
                    ?: Double.MAX_VALUE
                val sampleY = sample.substringAfter("\"y\":").substringBefore(",").toDoubleOrNull()
                    ?: Double.MAX_VALUE
                val sampleSpeed = sample.substringAfter("\"spd\":").substringBefore(",").toDoubleOrNull()
                    ?: Double.MAX_VALUE
                val grounded = "\"ground\":true" in sample
                // Arrival = *standing still on the goal block*, not passing
                // through a tolerance sphere at speed. The 3D sphere alone
                // accepted both fly-throughs (overshoot counted as success)
                // and standing on the floor *under* a goal pad one block up
                // (observed bypass on the gauntlet).
                val atGoalLevel = kotlin.math.abs(sampleY - goalCenter.y) <= 0.9
                val standing = grounded && sampleSpeed <= ORACLE_STOP_SPEED
                val handleStatus = sample.substringAfter("\"handleStatus\":\"").substringBefore("\"")
                if ((distance <= scenario.goalTolerance && atGoalLevel && standing) || handleStatus == "Succeeded") {
                    reached = true
                    break
                }
                if (handleStatus == "Failed") {
                    failedCleanly = true
                    break
                }
            }
        }

        // An issued jump that never returned to ground before termination is
        // not a successful landing.
        pendingJump?.let { attempt ->
            jumpLandings += JumpLanding(attempt, ticks, attempt.launch, success = false, null, null)
        }

        // --- Final snapshot + teardown ---
        val report = computeOnClient<ScenarioReport, IllegalStateException> {
            val handle = PathfinderManager.activeTraversal
            val endDistance = runSafe {
                val pos = player.pos
                hypot(hypot(pos.x - goalCenter.x, pos.z - goalCenter.z), pos.y - goalCenter.y)
            } ?: Double.NaN
            ScenarioReport(
                name = scenario.name,
                expectSuccess = scenario.expectSuccess,
                gated = scenario.gated,
                reachedGoal = reached,
                failedCleanly = failedCleanly || handle?.status == TraversalHandle.Status.Failed,
                ticks = ticks,
                plannedNodes = handle?.path?.size ?: 0,
                coarseNodes = handle?.coarsePath?.size ?: 0,
                // A D* path shrinks as its start advances. Using only the
                // terminal suffix makes a successful long traversal look
                // hundreds of percent wasteful, so retain the longest adopted
                // path as the run's reference length.
                plannedLength = maxOf(handle?.pathLength ?: 0.0, referencePlannedLength),
                processedNodes = handle?.processedNodes ?: 0,
                graphSize = handle?.graphSize ?: 0,
                refinementSavedPercent = handle?.lastRefinement?.savedPercent ?: 0.0,
                traveledLength = traveled,
                pathDeviationMean = if (deviationSamples > 0) deviationSum / deviationSamples else 0.0,
                pathDeviationMax = deviationMax,
                jumpInputTicks = jumpInputTicks,
                airborneJumps = airborneJumps,
                flightToggled = flightToggled,
                maxLostTicks = maxLostTicks,
                // Terminal handling resets executor runtime counters before
                // the final snapshot, so use the run maximum from telemetry.
                replansRequested = maxReplansRequested,
                firstReadyTick = firstReadyTick,
                firstFollowingTick = firstFollowingTick,
                followingTicks = followingTicks,
                planningPauseTicks = planningPauseTicks,
                planningPauseBursts = planningPauseBursts,
                longestPlanningPauseTicks = longestPlanningPauseTicks,
                movementStallTicks = movementStallTicks,
                movementStallBursts = movementStallBursts,
                longestMovementStallTicks = longestMovementStallTicks,
                executorLostTicks = executorLostTicks,
                executorLostBursts = executorLostBursts,
                longestExecutorLostBurstTicks = longestExecutorLostBurstTicks,
                jumpLandingSuccesses = jumpLandings.count { it.success },
                jumpLandingFailures = jumpLandings.count { !it.success },
                wallCollisionTicks = wallCollisionTicks,
                replanLatencyTicks = replanLatencyMax,
                headBonkTicks = headBonkTicks,
                arcDevMean = jumpLandings.mapNotNull { it.arcErrMean }.let { if (it.isEmpty()) 0.0 else it.average() },
                arcDevMax = jumpLandings.mapNotNull { it.arcErrMax }.maxOrNull() ?: 0.0,
                planDevMean = jumpLandings.mapNotNull { it.planErrMean }.let { if (it.isEmpty()) 0.0 else it.average() },
                planDevMax = jumpLandings.mapNotNull { it.planErrMax }.maxOrNull() ?: 0.0,
                maxFirstFollowingTick = scenario.maxFirstFollowingTick,
                maxCompletionTicks = scenario.maxCompletionTicks,
                maxPlanningPauseTicks = scenario.maxPlanningPauseTicks,
                maxLongestPlanningPauseTicks = scenario.maxLongestPlanningPauseTicks,
                maxLongestMovementStallTicks = scenario.maxLongestMovementStallTicks,
                maxLongestExecutorLostBurstTicks = scenario.maxLongestExecutorLostBurstTicks,
                maxReplansRequested = scenario.maxReplansRequested,
                minJumpLandingSuccessRate = scenario.minJumpLandingSuccessRate,
                maxReplanLatencyTicks = scenario.maxReplanLatencyTicks,
                minAllowedY = scenario.minAllowedY,
                minPlayerY = minPlayerY,
                parkourExpectedLandings = parkourExpected.size,
                parkourMatchedTargets = parkourMatchedTargets,
                parkourMatchedLandings = parkourMatchedLandings,
                parkourMismatches = parkourMismatches,
                parkourRetries = parkourRetries,
                finalStatus = handle?.status?.toString() ?: "None",
                failureReason = handle?.failureReason,
                endDistanceToGoal = endDistance,
                coarsePathDump = handle?.coarsePath.orEmpty().take(16).joinToString(" ") {
                    val b = it.toBlockPos()
                    "(" + b.x + "," + b.y + "," + b.z + ")"
                },
                plannerStats = plannerMetrics.stats(),
                jumpRecords = jumpLandings.map { it.toRecord(scenario.name) },
            )
        }

        runOnClient<IllegalStateException> { PathfinderManager.cancelActiveTraversal() }
        waitTicks(5)
        PlannerMetrics.sink = PlannerMetrics.NoOp

        File(outputDir, "${scenario.name}.jsonl").writeText(telemetry.toString())
        File(outputDir, "${scenario.name}.jumps.jsonl").writeText(
            jumpLandings.joinToString("\n", postfix = if (jumpLandings.isEmpty()) "" else "\n") { landing ->
                buildString {
                    append("{\"launchTick\":").append(landing.attempt.tick)
                    append(",\"landingTick\":").append(landing.tick)
                    append(",\"segmentType\":\"").append(landing.attempt.segmentType).append('"')
                    append(",\"segmentIndex\":").append(landing.attempt.segmentIndex)
                    append(",\"launchSpeed\":").append("%.4f".format(landing.attempt.launchSpeed))
                    append(",\"launch\":[").append("%.3f".format(landing.attempt.launch.x)).append(',')
                        .append("%.3f".format(landing.attempt.launch.y)).append(',')
                        .append("%.3f".format(landing.attempt.launch.z)).append(']')
                    landing.attempt.target?.let { target ->
                        append(",\"target\":[").append("%.3f".format(target.x)).append(',')
                            .append("%.3f".format(target.y)).append(',')
                            .append("%.3f".format(target.z)).append(']')
                    }
                    append(",\"landing\":[").append("%.3f".format(landing.position.x)).append(',')
                        .append("%.3f".format(landing.position.y)).append(',')
                        .append("%.3f".format(landing.position.z)).append(']')
                    landing.horizontalError?.let { append(",\"horizontalError\":").append("%.3f".format(it)) }
                    landing.verticalError?.let { append(",\"verticalError\":").append("%.3f".format(it)) }
                    landing.arcErrMean?.let { append(",\"arcErrMean\":").append("%.3f".format(it)) }
                    landing.arcErrMax?.let { append(",\"arcErrMax\":").append("%.3f".format(it)) }
                    landing.planErrMean?.let { append(",\"planErrMean\":").append("%.3f".format(it)) }
                    landing.planErrMax?.let { append(",\"planErrMax\":").append("%.3f".format(it)) }
                    append(",\"success\":").append(landing.success).append('}')
                }
            }
        )
        File(outputDir, "${scenario.name}.planner.jsonl").writeText(plannerMetrics.jsonLines())
        return report
    }

    fun writeSummary(reports: List<ScenarioReport>) {
        File(outputDir, "summary.json").writeText(
            reports.joinToString(",\n", prefix = "[\n", postfix = "\n]") { it.toJson() }
        )
        JumpMatrix.write(outputDir, reports.flatMap(ScenarioReport::jumpRecords))
    }

    /**
     * The planned edge is what classifies a jump. Fall back to the executor's
     * launch->target displacement only when the segment telemetry is missing,
     * so a jump is never silently dropped from the matrix.
     */
    private fun JumpLanding.toRecord(scenario: String): JumpRecord {
        val start = attempt.edgeStart ?: attempt.launch
        val end = attempt.edgeEnd ?: attempt.target
        return JumpRecord(
            scenario = scenario,
            segmentType = attempt.segmentType.ifEmpty { "?" },
            edgeDx = end?.let { (it.x - start.x).roundToInt() },
            edgeDy = end?.let { (it.y - start.y).roundToInt() },
            edgeDz = end?.let { (it.z - start.z).roundToInt() },
            launchSpeed = attempt.launchSpeed,
            approachTicks = attempt.approachTicks,
            refusalTicks = attempt.refusalTicks,
            dominantGate = attempt.dominantGate,
            approachTurnDegrees = attempt.approachTurnDegrees,
            attemptIndex = attempt.attemptIndex,
            success = success,
            horizontalError = horizontalError,
            verticalError = verticalError,
            arcErrMax = arcErrMax,
            planErrMax = planErrMax,
        )
    }
}

/** Reads a `"<name>X"/"<name>Y"/"<name>Z"` triple out of a telemetry sample. */
private fun vecField(sample: String, name: String): Vec3d? {
    val x = sample.numberField("${name}X") ?: return null
    val y = sample.numberField("${name}Y") ?: return null
    val z = sample.numberField("${name}Z") ?: return null
    return Vec3d(x, y, z)
}

private fun String.numberField(name: String): Double? {
    val marker = "\"$name\":"
    val start = indexOf(marker)
    if (start < 0) return null
    return substring(start + marker.length).substringBefore(',').substringBefore('}').toDoubleOrNull()
}
