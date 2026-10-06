package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.MovementSimulator
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import pathing.ProbeScenarios.PROFILE

/**
 * The closed-form movement model, checked against the simulator rather than against itself.
 *
 * Every claim here is one the planner would otherwise answer by rolling out physics, so
 * the only test worth writing is whether the arithmetic and the simulator agree. Where
 * they do, a rollout can be replaced by a division.
 */
class HorizontalDynamicsTest {

    @Test
    fun `the reachable radius is exactly what the simulator accelerates to`() {
        val dynamics = BallisticProfile.VANILLA
        val simulated = accelerateFromRest(ticks = 12, sprint = true)

        simulated.forEachIndexed { index, speed ->
            val predicted = dynamics.groundSpeedReach(0.0, 0.0, 1.0, 0.0, index + 1, sprint = true).endInclusive
            assertTrue(
                abs(predicted - speed) < 1e-6,
                "tick ${index + 1}: model says %.6f, simulator says %.6f".format(predicted, speed),
            )
        }
    }

    @Test
    fun `cruise is the limit of the reachable radius and the speed the body settles at`() {
        val dynamics = BallisticProfile.VANILLA
        val settled = accelerateFromRest(ticks = 60, sprint = true).last()

        assertTrue(
            abs(dynamics.cruiseSpeed(sprint = true) - settled) < 1e-6,
            "cruise %.6f should be the settled speed %.6f".format(dynamics.cruiseSpeed(sprint = true), settled),
        )
        assertTrue(
            dynamics.groundSpeedReach(0.0, 0.0, 1.0, 0.0, 64, sprint = true).endInclusive <= dynamics.cruiseSpeed(sprint = true) + 1e-9,
            "the reachable radius must never exceed cruise",
        )
    }

    @Test
    fun `the projection bounds the speeds the simulator can produce along a direction`() {
        val dynamics = BallisticProfile.VANILLA
        val entry = accelerateState(ticks = 6, sprint = true)
        val from = entry.velocity
        val radians = Math.toRadians(40.0)
        val unitX = -kotlin.math.sin(radians)
        val unitZ = kotlin.math.cos(radians)

        for (ticks in 1..4) {
            val range = dynamics.groundSpeedReach(from.x, from.z, unitX, unitZ, ticks, sprint = true)
            // Drive hard along the direction, and hard against it, and the simulator must
            // land inside the bounds the projection promises.
            listOf(40.0, 220.0, 130.0).forEach { yaw ->
                var state = entry
                repeat(ticks) { state = tick(state, yaw, throttle = 1.0, sprint = true) }
                val along = state.velocity.x * unitX + state.velocity.z * unitZ
                assertTrue(
                    along >= range.start - 1e-6 && along <= range.endInclusive + 1e-6,
                    ("after %d tick(s) at yaw %.0f the simulator reached %.5f along, " +
                        "outside the projected %.5f..%.5f").format(
                        ticks, yaw, along, range.start, range.endInclusive,
                    ),
                )
            }
        }
    }

    @Test
    fun `coasting sheds speed at the rate the simulator does`() {
        val dynamics = BallisticProfile.VANILLA
        var state = accelerateState(ticks = 12, sprint = true)
        var predicted = state.velocity.horizontalLength()

        repeat(5) {
            state = tick(state, state.rotation.yaw, throttle = 0.0, sprint = false)
            predicted *= dynamics.groundFriction
            assertTrue(
                abs(predicted - state.velocity.horizontalLength()) < 1e-6,
                "coasting: model %.6f, simulator %.6f".format(
                    predicted, state.velocity.horizontalLength(),
                ),
            )
        }
    }

    private fun accelerateFromRest(ticks: Int, sprint: Boolean): List<Double> {
        var state = rest()
        return (1..ticks).map {
            state = tick(state, YAW, throttle = 1.0, sprint = sprint)
            state.velocity.horizontalLength()
        }
    }

    private fun accelerateState(ticks: Int, sprint: Boolean): MovementSimulationState {
        var state = rest()
        repeat(ticks) { state = tick(state, YAW, throttle = 1.0, sprint = sprint) }
        return state
    }

    private fun tick(
        from: MovementSimulationState,
        yaw: Double,
        throttle: Double,
        sprint: Boolean,
    ): MovementSimulationState {
        return MovementSimulator(PROFILE, GROUND, from).tickMovement(
            MovementSimulationInput(
                forward = throttle,
                sprint = sprint && throttle > 0.0,
                rotation = Rotation(yaw, 0.0),
            ),
        )
    }

    private fun rest() = MovementSimulationState.synthetic(
        profile = PROFILE,
        position = Vec3d(0.5, 100.0, 0.5),
        rotation = Rotation(YAW, 0.0),
        velocity = Vec3d(0.0, -0.0784, 0.0),
        onGround = true,
    )

    private companion object {
        const val YAW = -90.0

        /** Flat ground, wide enough that nothing here ever meets a wall. */
        val GROUND: SnapshotSimulationEnvironment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-200, 90, -200, 200, 120, 200),
            buildMap {
                for (x in -180..180) for (z in -180..180) {
                    put(BlockPos(x, 99, z), SnapshotBlockPhysics.FULL_CUBE)
                }
            },
        )
    }
}
