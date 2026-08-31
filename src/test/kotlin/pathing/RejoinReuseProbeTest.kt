/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.MovementSimulationStepResult
import com.lambda.pathing.prediction.simulation.MovementSimulator
import com.lambda.pathing.trajectory.TrajectoryPlan
import kotlin.math.abs
import kotlin.test.Test
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Tag

/**
 * Whether a spine's remaining tape survives being rejoined from a slightly different body.
 *
 * The spine-and-shortcut design rests on one assumption: an improvement that rejoins the
 * committed tape does not force the suffix to be re-searched, because replaying the
 * suffix's recorded inputs from the rejoin state is cheap and almost always still valid.
 * If that holds, a shortcut costs one replay instead of a fresh search of everything
 * behind it. If it does not, the whole scheme collapses into re-planning suffixes.
 *
 * Perturbations are scaled by the beam's own notion of "the same body" (Frontier's
 * POSITION_BUCKET_BLOCKS = 0.25, speedBucketBlocks = 0.075), since that is the tolerance
 * a rejoin would actually be matched at. Non-gating report.
 */
@Tag("bedrock-corpus")
class RejoinReuseProbeTest {

    private class Verdict(var ok: Int = 0, var diverged: Int = 0, var rejected: Int = 0) {
        val total: Int get() = ok + diverged + rejected
        val rate: Double get() = if (total == 0) 0.0 else 100.0 * ok / total
    }

    @Test
    fun `suffix replay survives a perturbed rejoin`() {
        val plans = ProbeScenarios.all().mapNotNull { scenario ->
            val path = (ProbeScenarios.planned(scenario).result as? PathPlanResult.Planned)?.path
            path?.takeIf { !it.partial }?.let { Triple(scenario.name, it.plan, scenario.environment) }
        }
        check(plans.isNotEmpty()) { "no scenario produced a full plan" }

        println("[rejoin] tolerance scale: position bucket 0.25 blocks, speed bucket 0.075 b/t")
        for ((label, posDelta, velDelta) in LEVELS) {
            val overall = Verdict()
            val perScenario = StringBuilder()
            var worstDeviation = 0.0
            for ((name, plan, environment) in plans) {
                val verdict = Verdict()
                for (frame in rejoinFrames(plan)) {
                    for ((dx, dz) in DIRECTIONS) {
                        val deviation =
                            replay(plan, environment, frame, dx, dz, posDelta, velDelta, verdict)
                        if (deviation > worstDeviation && deviation.isFinite()) worstDeviation = deviation
                    }
                }
                overall.ok += verdict.ok
                overall.diverged += verdict.diverged
                overall.rejected += verdict.rejected
                perScenario.append(" %s=%.0f%%".format(name, verdict.rate))
            }
            println(
                "[rejoin] dpos=%-6.4f dvel=%-6.4f (%s)  valid=%5.1f%%  (%d ok / %d diverged / %d rejected)  worstDev=%.3f |%s"
                    .format(
                        posDelta, velDelta, label, overall.rate,
                        overall.ok, overall.diverged, overall.rejected, worstDeviation, perScenario,
                    ),
            )
        }
    }

    @Test
    fun `sensitivity by what the body is about to do`() {
        val plans = ProbeScenarios.all().mapNotNull { scenario ->
            val path = (ProbeScenarios.planned(scenario).result as? PathPlanResult.Planned)?.path
            path?.takeIf { !it.partial }?.let { Triple(scenario.name, it.plan, scenario.environment) }
        }
        for ((label, posDelta, velDelta) in LEVELS.drop(1)) {
            val byLead = LEAD_BUCKETS.associateWith { Verdict() }
            for ((_, plan, environment) in plans) {
                for (frame in rejoinFrames(plan)) {
                    val lead = framesToNextJump(plan, frame)
                    val bucket = LEAD_BUCKETS.first { lead <= it }
                    for ((dx, dz) in DIRECTIONS) {
                        replay(plan, environment, frame, dx, dz, posDelta, velDelta, byLead.getValue(bucket))
                    }
                }
            }
            println(
                "[lead]   dpos=%-6.4f (%-10s) %s".format(
                    posDelta, label,
                    LEAD_BUCKETS.joinToString("  ") { bucket ->
                        val v = byLead.getValue(bucket)
                        val name = if (bucket == Int.MAX_VALUE) "noJump" else "jumpIn<=$bucket"
                        "%s: %5.1f%% (n=%d)".format(name, v.rate, v.total)
                    },
                ),
            )
        }
    }

    /** How many frames until the tape next presses jump, or [Int.MAX_VALUE] if it never does. */
    private fun framesToNextJump(plan: TrajectoryPlan, frame: Int): Int {
        for (index in frame + 1..plan.frames.lastIndex) {
            if (plan.tape[index].jump) return index - frame
        }
        return Int.MAX_VALUE
    }

    /** Grounded frames are where a shortcut would realistically rejoin. */
    private fun rejoinFrames(plan: TrajectoryPlan): List<Int> =
        plan.frames.indices.filter { index ->
            index > 0 && index < plan.frames.lastIndex - MIN_SUFFIX_FRAMES &&
                plan.frames[index].state.onGround && index % REJOIN_STRIDE == 0
        }

    /** Returns the max deviation from the certified suffix, or +inf when the replay was rejected. */
    private fun replay(
        plan: TrajectoryPlan,
        environment: SnapshotSimulationEnvironment,
        frame: Int,
        dx: Double,
        dz: Double,
        posDelta: Double,
        velDelta: Double,
        verdict: Verdict,
    ): Double {
        val origin = plan.frames[frame].state
        val start = origin.perturbed(dx * posDelta, dz * posDelta, dx * velDelta, dz * velDelta)
        val simulator = MovementSimulator(
            profile = plan.physicsProfile, environment = environment, initialState = start,
        )
        var deviation = 0.0
        for (index in frame + 1..plan.frames.lastIndex) {
            when (simulator.tryTickMovement(plan.tape[index])) {
                is MovementSimulationStepResult.Rejected -> {
                    verdict.rejected++
                    return Double.POSITIVE_INFINITY
                }

                is MovementSimulationStepResult.Advanced -> {
                    val expected = plan.frames[index].state.position
                    val actual = simulator.state.position
                    deviation = maxOf(deviation, expected.distanceTo(actual))
                }
            }
        }
        val end = simulator.state
        val certified = plan.frames.last().state
        val settled = end.onGround &&
            end.velocity.horizontalLength() <= STOP_SPEED &&
            abs(end.position.y - certified.position.y) <= 0.25
        if (deviation <= DEVIATION_TOLERANCE && settled) verdict.ok++ else verdict.diverged++
        return deviation
    }

    private fun MovementSimulationState.perturbed(px: Double, pz: Double, vx: Double, vz: Double):
        MovementSimulationState {
        val moved = Vec3d(position.x + px, position.y, position.z + pz)
        return copy(
            position = moved,
            velocity = Vec3d(velocity.x + vx, velocity.y, velocity.z + vz),
            boundingBox = boundingBox.offset(px, 0.0, pz),
        )
    }

    private companion object {
        const val REJOIN_STRIDE = 7
        const val MIN_SUFFIX_FRAMES = 12
        const val DEVIATION_TOLERANCE = 0.35
        const val STOP_SPEED = 0.02

        val DIRECTIONS = listOf(
            1.0 to 0.0, -1.0 to 0.0, 0.0 to 1.0, 0.0 to -1.0,
            0.707 to 0.707, -0.707 to 0.707, 0.707 to -0.707, -0.707 to -0.707,
        )

        val LEAD_BUCKETS = listOf(4, 12, 30, Int.MAX_VALUE)

        val LEVELS = listOf(
            Triple("control", 0.0, 0.0),
            Triple("1/8 bucket", 0.03125, 0.009375),
            Triple("1/4 bucket", 0.0625, 0.01875),
            Triple("1/2 bucket", 0.125, 0.0375),
            Triple("1 bucket", 0.25, 0.075),
        )
    }
}
