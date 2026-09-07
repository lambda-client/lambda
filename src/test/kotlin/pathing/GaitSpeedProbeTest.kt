/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.MovementSimulationStepResult
import com.lambda.pathing.physics.MovementSimulator
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import pathing.ProbeScenarios.PROFILE

/** What is actually the fastest way to cross flat ground? */
@Tag("bedrock-corpus")
class GaitSpeedProbeTest {
    /** The best any tape could do on a 14-block straight: accelerate, hop, brake to a stop. */
    @Test
    fun `reference tape for a short straight run`() {
        for (distance in listOf(14.0, 30.0)) {
            for (hop in listOf(false, true)) {
                val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
                for (z in -6..60) for (x in -4..4) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
                val environment = SnapshotSimulationEnvironment.synthetic(
	                SimulationSnapshotBounds(-8, 58, -8, 8, 90, 70), blocks,
                )
                val simulator = MovementSimulator(
	                profile = PROFILE, environment = environment,
	                initialState = MovementSimulationState.synthetic(
		                profile = PROFILE, position = Vec3d(0.5, 64.0, 0.5),
		                rotation = Rotation(0.0, 0.0), velocity = Vec3d(0.0, -0.0784, 0.0), onGround = true,
	                ),
                )
                var ticks = 0
                var jumps = 0
                // Drive to the goal, then release and let it settle: the same terminal
                // condition a certified tape has to satisfy.
                while (ticks < 400) {
                    val travelled = simulator.state.position.z - 0.5
                    val remaining = distance - travelled
                    val braking = remaining <= BRAKE_LOOKAHEAD * simulator.state.velocity.horizontalLength()
                    if (braking && simulator.state.onGround &&
                        simulator.state.velocity.horizontalLength() <= 0.012
                    ) break
                    val jump = hop && !braking && simulator.state.onGround
                    if (jump) jumps++
                    val input = MovementSimulationInput(
	                    forward = if (braking) 0.0 else 1.0,
	                    sprint = !braking, jump = jump, rotation = Rotation(0.0, 0.0),
                    )
                    if (simulator.tryTickMovement(input) !is MovementSimulationStepResult.Advanced) break
                    ticks++
                }
                println(
                    "[reference] %.0f blocks %-9s -> %3d ticks, stopped at %.2f (%d jumps)"
                        .format(distance, if (hop) "hopping" else "sprinting", ticks,
                            simulator.state.position.z - 0.5, jumps)
                )
            }
        }
    }

    @Test
    fun `sustained gait speeds`() {
        for (gait in listOf("sprint", "sprint-jump-every-landing", "walk")) {
            val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
            for (z in -4..140) for (x in -4..4) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
            val environment = SnapshotSimulationEnvironment.synthetic(
	            SimulationSnapshotBounds(-8, 58, -8, 8, 90, 150), blocks,
            )
            val simulator = MovementSimulator(
	            profile = PROFILE, environment = environment,
	            initialState = MovementSimulationState.synthetic(
		            profile = PROFILE, position = Vec3d(0.5, 64.0, 0.5),
		            rotation = Rotation(0.0, 0.0), velocity = Vec3d(0.0, -0.0784, 0.0), onGround = true,
	            ),
            )
            var ticks = 0
            var jumps = 0
            while (simulator.state.position.z - 0.5 < 100.0 && ticks < 2000) {
                val jump = when (gait) {
                    "sprint-jump-every-landing" -> simulator.state.onGround
                    else -> false
                }
                if (jump) jumps++
                val input = MovementSimulationInput(
	                forward = 1.0, sprint = gait != "walk", jump = jump,
	                rotation = Rotation(0.0, 0.0),
                )
                if (simulator.tryTickMovement(input) !is MovementSimulationStepResult.Advanced) break
                ticks++
            }
            val blocksTravelled = simulator.state.position.z - 0.5
            println(
                "[gait] %-26s %6.2f blocks in %4d ticks = %.4f b/t  (%d jumps)"
                    .format(gait, blocksTravelled, ticks, blocksTravelled / ticks, jumps)
            )
        }
    }

    private companion object {
        /** Ticks of coasting a release buys, measured well enough to place a brake. */
        const val BRAKE_LOOKAHEAD = 8.0

    }
}
