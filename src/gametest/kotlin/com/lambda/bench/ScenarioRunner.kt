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
    val finalStatus: String,
    val failureReason: String?,
    val endDistanceToGoal: Double,
    val coarsePathDump: String,
    val plannerStats: BenchPlannerMetrics.PlannerStats = BenchPlannerMetrics.PlannerStats.EMPTY,
) {
    // In an unbounded world a truly unreachable goal manifests as
    // Partial-forever (the backward search can never exhaust the goal's
    // component), so expect-failure scenarios pass when the agent neither
    // reaches the goal nor misbehaves (creative-flight toggle).
    val passed: Boolean get() = if (expectSuccess) reachedGoal else !reachedGoal && !flightToggled

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
        append(" initUs=").append(plannerStats.initialWallMicros)
        append(" repairs=").append(plannerStats.repairs)
        append("(p50=").append(plannerStats.repairWallUsP50).append("us)")
        if (flightToggled) append(" FLIGHT-TOGGLED")
        if (plannerStalled) append(" PLANNER-STALLED")
        append(" status=").append(finalStatus)
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

    private val runId: String = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())

    val outputDir: File by lazy {
        FabricLoader.getInstance().gameDir.resolve("logs/benchmarks/$runId").toFile().apply { mkdirs() }
    }

    fun ClientGameTestContext.runScenario(scenario: TraversalScenario, server: TestServerContext): ScenarioReport {
        // --- Fixture + placement ---
        runOnClient<IllegalStateException> { PathfinderManager.cancelActiveTraversal() }
        scenario.fixture.forEach(server::runCommand)
        server.runCommand("/tp Steve ${scenario.start.x} ${scenario.start.y} ${scenario.start.z} ${scenario.startYaw} 0")
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
        var wasOnGround = true
        var wasJumpInput = false
        var traveled = 0.0
        var previousX = Double.NaN
        var previousZ = Double.NaN
        var deviationSum = 0.0
        var deviationMax = 0.0
        var deviationSamples = 0

        if (requested) {
            while (ticks < scenario.timeoutTicks) {
                waitTick()
                ticks++

                scenario.mutations.forEach { (tick, command) ->
                    if (tick == ticks) server.runCommand(command)
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
                            append(",\"flying\":").append(player.abilities.flying)
                            append(",\"status\":\"").append(state.status).append('"')
                            append(",\"seg\":").append(state.segmentIndex)
                            append(",\"segType\":\"").append(state.segmentType ?: "").append('"')
                            append(",\"jump\":").append(state.jumpCommand)
                            append(",\"jumpCmdGate\":\"").append(state.jumpCommandGate).append('"')
                            append(",\"jumpInGate\":\"").append(state.jumpInputGate).append('"')
                            append(",\"fwd\":").append("%.2f".format(state.commandedForward))
                            append(",\"strafe\":").append("%.2f".format(state.commandedStrafe))
                            append(",\"lost\":").append(state.lostTicks)
                            append(",\"latErr\":").append("%.3f".format(state.lateralError))
                            append(",\"dist\":").append("%.3f".format(distance))
                            append(",\"handleStatus\":\"").append(handle?.status ?: "None").append('"')
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
                val jumpInput = "\"jumpInGate\":\"issued\"" in sample
                val flying = "\"flying\":true" in sample
                if (jumpInput && !wasJumpInput) jumpInputTicks++
                if (!onGround && wasOnGround && jumpInput) airborneJumps++
                if (flying) flightToggled = true
                sample.substringAfter("\"lost\":").substringBefore(",").toIntOrNull()?.let {
                    if (it > maxLostTicks) maxLostTicks = it
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

        // --- Final snapshot + teardown ---
        val report = computeOnClient<ScenarioReport, IllegalStateException> {
            val handle = PathfinderManager.activeTraversal
            val state = PathfinderExecutor.state
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
                plannedLength = handle?.pathLength ?: 0.0,
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
                replansRequested = state.replansRequested,
                finalStatus = handle?.status?.toString() ?: "None",
                failureReason = handle?.failureReason,
                endDistanceToGoal = endDistance,
                coarsePathDump = handle?.coarsePath.orEmpty().take(16).joinToString(" ") {
                    val b = it.toBlockPos()
                    "(" + b.x + "," + b.y + "," + b.z + ")"
                },
                plannerStats = plannerMetrics.stats(),
            )
        }

        runOnClient<IllegalStateException> { PathfinderManager.cancelActiveTraversal() }
        waitTicks(5)
        PlannerMetrics.sink = PlannerMetrics.NoOp

        File(outputDir, "${scenario.name}.jsonl").writeText(telemetry.toString())
        File(outputDir, "${scenario.name}.planner.jsonl").writeText(plannerMetrics.jsonLines())
        return report
    }

    fun writeSummary(reports: List<ScenarioReport>) {
        File(outputDir, "summary.json").writeText(
            reports.joinToString(",\n", prefix = "[\n", postfix = "\n]") { it.toJson() }
        )
    }
}
