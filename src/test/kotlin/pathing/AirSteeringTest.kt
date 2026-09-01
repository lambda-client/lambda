/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.launch.AirSteering
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.prediction.simulation.MovementSimulationInput
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.MovementSimulationStepResult
import com.lambda.pathing.prediction.simulation.MovementSimulator
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.Vec3d
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pathing.ProbeScenarios.PROFILE

/**
 * The flight controller, checked against the simulator rather than against itself.
 *
 * The claims that matter: a flight already on course is left alone (certified tapes with
 * an exact arc model do not change under the controller's existence); every emitted
 * input is a legal key press; and steering toward an offset aim actually moves the
 * simulated flight onto it, closed-loop, despite the model being an approximation.
 */
class AirSteeringTest {

    @Test
    fun `a flight already landing on its aim is left alone every tick`() {
        val flight = fly(ticks = 12, steerTo = null)
        val drift = flight.last()
        var state = flight.first()
        for (tick in 0 until 12) {
            val keys = AirSteering.steer(
                positionX = state.position.x, positionZ = state.position.z,
                velocityX = state.velocity.x, velocityZ = state.velocity.z,
                yawDegrees = YAW,
                aimX = drift.position.x, aimZ = drift.position.z,
                unitX = 0.0, unitZ = 1.0,
                remainingTicks = 12 - tick,
                plannedHoldTicks = 0,
                sprintAcceleration = AIR_ACCELERATION,
                walkAcceleration = AIR_ACCELERATION,
            )
            assertNull(keys, "tick $tick: an on-course coast must not be corrected")
            state = tick(state, forward = 0.0, strafe = 0.0)
        }
    }

    @Test
    fun `every emitted input is a legal key press`() {
        val offsets = listOf(0.4 to 0.0, -0.7 to 0.3, 0.0 to 0.9, -0.5 to -0.5)
        for ((dx, dz) in offsets) {
            var state = launchState()
            for (tick in 0 until 12) {
                val keys = AirSteering.steer(
                    positionX = state.position.x, positionZ = state.position.z,
                    velocityX = state.velocity.x, velocityZ = state.velocity.z,
                    yawDegrees = YAW,
                    aimX = state.position.x + dx, aimZ = state.position.z + 2.0 + dz,
                    unitX = 0.0, unitZ = 1.0,
                    remainingTicks = 12 - tick,
                    plannedHoldTicks = 0,
                    sprintAcceleration = AIR_ACCELERATION,
                    walkAcceleration = AIR_ACCELERATION,
                )
                if (keys != null) {
                    assertTrue(
                        keys.forward in LEGAL && keys.strafe in LEGAL,
                        "(${keys.forward}, ${keys.strafe}) is not a key press",
                    )
                }
                state = tick(state, keys?.forward ?: 0.0, keys?.strafe ?: 0.0)
            }
        }
    }

    @Test
    fun `steering moves the simulated flight onto a laterally offset aim`() {
        val ticks = 12
        val drift = fly(ticks, steerTo = null).last()
        val aimX = drift.position.x + 0.8
        val aimZ = drift.position.z

        val steered = fly(ticks, steerTo = aimX to aimZ).last()
        val steeredMiss = hypot(steered.position.x - aimX, steered.position.z - aimZ)
        val driftMiss = hypot(drift.position.x - aimX, drift.position.z - aimZ)

        assertTrue(
            steeredMiss < 0.15,
            "steered flight misses the aim by %.3f blocks".format(steeredMiss),
        )
        assertTrue(
            steeredMiss < driftMiss - 0.5,
            "steering must claim most of the 0.8-block offset (drift %.3f, steered %.3f)"
                .format(driftMiss, steeredMiss),
        )
    }

    @Test
    fun `steering pulls an overshooting hold schedule back onto a short aim`() {
        val ticks = 12
        // Full hold overshoots a drift-length aim; the controller must trim it.
        val aim = fly(ticks, steerTo = null).last()
        var state = launchState()
        repeat(ticks) { tick ->
            val keys = AirSteering.steer(
                positionX = state.position.x, positionZ = state.position.z,
                velocityX = state.velocity.x, velocityZ = state.velocity.z,
                yawDegrees = YAW,
                aimX = aim.position.x, aimZ = aim.position.z,
                unitX = 0.0, unitZ = 1.0,
                remainingTicks = ticks - tick,
                plannedHoldTicks = ticks - tick,
                sprintAcceleration = AIR_ACCELERATION,
                walkAcceleration = AIR_ACCELERATION,
            )
            state = tick(state, keys?.forward ?: 1.0, keys?.strafe ?: 0.0)
        }
        val miss = hypot(state.position.x - aim.position.x, state.position.z - aim.position.z)
        assertTrue(
            miss < 0.15,
            "a held schedule steered onto a coasting aim misses by %.3f blocks".format(miss),
        )
    }

    /** [ticks] of free flight; with an aim the controller flies it, without it coasts. */
    private fun fly(ticks: Int, steerTo: Pair<Double, Double>?): List<MovementSimulationState> {
        var state = launchState()
        val states = ArrayList<MovementSimulationState>(ticks + 1)
        states += state
        repeat(ticks) { tick ->
            val keys = steerTo?.let { (aimX, aimZ) ->
                AirSteering.steer(
                    positionX = state.position.x, positionZ = state.position.z,
                    velocityX = state.velocity.x, velocityZ = state.velocity.z,
                    yawDegrees = YAW,
                    aimX = aimX, aimZ = aimZ,
                    unitX = 0.0, unitZ = 1.0,
                    remainingTicks = ticks - tick,
                    plannedHoldTicks = 0,
                    sprintAcceleration = AIR_ACCELERATION,
                    walkAcceleration = AIR_ACCELERATION,
                )
            }
            state = tick(state, keys?.forward ?: 0.0, keys?.strafe ?: 0.0)
            states += state
        }
        return states
    }

    private fun launchState() = MovementSimulationState.synthetic(
        profile = PROFILE,
        position = Vec3d(0.5, 120.0, 0.5),
        rotation = Rotation(YAW, 0.0),
        velocity = Vec3d(0.0, 0.42, 0.25),
        onGround = false,
    )

    private fun tick(
        from: MovementSimulationState,
        forward: Double,
        strafe: Double,
    ): MovementSimulationState {
        val simulator = MovementSimulator(PROFILE, AIR, from, skipEntityCollisions = true)
        val result = simulator.tryTickMovement(
            MovementSimulationInput(
                forward = forward,
                strafe = strafe,
                rotation = Rotation(YAW, 0.0),
            ),
        )
        check(result is MovementSimulationStepResult.Advanced) { "the empty fixture must never block" }
        return simulator.state
    }

    private companion object {
        /** Facing +Z so forward presses accelerate along the flight. */
        const val YAW = 0.0

        val LEGAL = setOf(-1.0, 0.0, 1.0)

        /** Walking air acceleration everywhere: the fixture never sprints. */
        const val AIR_ACCELERATION = BallisticProfile.WALK_AIR_ACCELERATION

        val AIR: SnapshotSimulationEnvironment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-64, 0, -64, 64, 200, 64), emptyMap(),
        )
    }
}
