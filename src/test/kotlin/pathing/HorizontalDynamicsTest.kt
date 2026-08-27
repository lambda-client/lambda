package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.HorizontalDynamics
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.MovementSimulationStepResult
import com.lambda.util.player.prediction.MovementSimulator
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotBlockPhysics
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        val dynamics = HorizontalDynamics.ground(BallisticProfile.VANILLA, sprint = true)
        val simulated = accelerateFromRest(ticks = 12, sprint = true)

        simulated.forEachIndexed { index, speed ->
            val predicted = dynamics.reachable(0.0, 0.0, index + 1).radius
            assertTrue(
                abs(predicted - speed) < 1e-6,
                "tick ${index + 1}: model says %.6f, simulator says %.6f".format(predicted, speed),
            )
        }
    }

    @Test
    fun `cruise is the limit of the reachable radius and the speed the body settles at`() {
        val dynamics = HorizontalDynamics.ground(BallisticProfile.VANILLA, sprint = true)
        val settled = accelerateFromRest(ticks = 60, sprint = true).last()

        assertTrue(
            abs(dynamics.cruise - settled) < 1e-6,
            "cruise %.6f should be the settled speed %.6f".format(dynamics.cruise, settled),
        )
        assertTrue(
            dynamics.reachable(0.0, 0.0, 64).radius <= dynamics.cruise + 1e-9,
            "the reachable radius must never exceed cruise",
        )
    }

    @Test
    fun `an inverted directive lands the body on the velocity it was solved for`() {
        val dynamics = HorizontalDynamics.ground(BallisticProfile.VANILLA, sprint = true)
        // Start with real momentum, then ask for a spread of achievable next velocities.
        val entry = accelerateState(ticks = 8, sprint = true)
        val from = entry.velocity

        val disc = dynamics.reachable(from.x, from.z, 1)
        val targets = listOf(0.0, 45.0, 90.0, 150.0, 225.0, 300.0).map { degrees ->
            val (ux, uz) = HorizontalDynamics.heading(degrees)
            disc.fastestAlong(ux, uz)
        }

        targets.forEach { (targetX, targetZ) ->
            val directive = assertNotNull(
                dynamics.directiveFor(from.x, from.z, targetX, targetZ),
                "a velocity on the reachable boundary must be achievable",
            )
            val after = tick(entry, directive.yawDegrees, directive.throttle, sprint = true)
            val error = hypot(after.velocity.x - targetX, after.velocity.z - targetZ)
            assertTrue(
                error < 2e-3,
                ("solved for (%.5f, %.5f) with yaw %.1f throttle %.3f, " +
                    "simulator produced (%.5f, %.5f); error %.5f").format(
                    targetX, targetZ, directive.yawDegrees, directive.throttle,
                    after.velocity.x, after.velocity.z, error,
                ),
            )
        }
    }

    @Test
    fun `a velocity outside the reachable disc has no directive`() {
        val dynamics = HorizontalDynamics.ground(BallisticProfile.VANILLA, sprint = true)
        val disc = dynamics.reachable(0.0, 0.0, 1)

        assertNotNull(dynamics.directiveFor(0.0, 0.0, disc.radius * 0.99, 0.0))
        assertNull(
            dynamics.directiveFor(0.0, 0.0, disc.radius * 1.05, 0.0),
            "asking for more than one tick of input must be refused, not clamped",
        )
    }

    @Test
    fun `the turn budget is the exact speed at which a turn stops being free`() {
        val dynamics = HorizontalDynamics.ground(BallisticProfile.VANILLA, sprint = true)

        for (degrees in listOf(15.0, 30.0, 45.0, 90.0)) {
            for (ticks in 1..3) {
                val radians = Math.toRadians(degrees)
                val limit = dynamics.maxTurnSpeed(radians, ticks)

                assertTrue(
                    dynamics.canReach(limit, 0.0, rotatedX(limit, radians), rotatedZ(limit, radians), ticks, 1e-6),
                    "at the budget speed the turn must be reachable ($degrees deg, $ticks ticks)",
                )
                val over = limit * 1.02
                assertTrue(
                    !dynamics.canReach(over, 0.0, rotatedX(over, radians), rotatedZ(over, radians), ticks, 0.0),
                    "two percent above the budget the same turn must not be ($degrees deg, $ticks ticks)",
                )
            }
        }
    }

    @Test
    fun `a sharper turn is never cheaper and more ticks are never worse`() {
        val dynamics = HorizontalDynamics.ground(BallisticProfile.VANILLA, sprint = true)

        val angles = listOf(0.0, 15.0, 30.0, 45.0, 60.0, 90.0, 135.0, 180.0).map(Math::toRadians)
        angles.zipWithNext().forEach { (gentle, sharp) ->
            assertTrue(
                dynamics.maxTurnSpeed(gentle, 1) >= dynamics.maxTurnSpeed(sharp, 1) - 1e-12,
                "a sharper turn cannot allow more speed",
            )
        }
        (1..7).zipWithNext().forEach { (fewer, more) ->
            assertTrue(
                dynamics.maxTurnSpeed(Math.toRadians(90.0), more) >=
                    dynamics.maxTurnSpeed(Math.toRadians(90.0), fewer) - 1e-12,
                "spending another tick on a turn cannot make it harder",
            )
        }
        assertTrue(
            abs(dynamics.maxTurnSpeed(Math.toRadians(180.0), 64) - dynamics.cruise) < 1e-6,
            "given enough ticks any turn is free up to cruise",
        )
    }

    @Test
    fun `the projection bounds the speeds the simulator can produce along a direction`() {
        val dynamics = HorizontalDynamics.ground(BallisticProfile.VANILLA, sprint = true)
        val entry = accelerateState(ticks = 6, sprint = true)
        val from = entry.velocity
        val (unitX, unitZ) = HorizontalDynamics.heading(40.0)

        for (ticks in 1..4) {
            val range = dynamics.reachable(from.x, from.z, ticks).projectOnto(unitX, unitZ)
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
        val dynamics = HorizontalDynamics.ground(BallisticProfile.VANILLA, sprint = true)
        var state = accelerateState(ticks = 12, sprint = true)
        var predicted = state.velocity.horizontalLength()

        repeat(5) {
            state = tick(state, state.rotation.yaw, throttle = 0.0, sprint = false)
            predicted *= dynamics.friction
            assertTrue(
                abs(predicted - state.velocity.horizontalLength()) < 1e-6,
                "coasting: model %.6f, simulator %.6f".format(
                    predicted, state.velocity.horizontalLength(),
                ),
            )
        }
    }

    private fun rotatedX(speed: Double, radians: Double) = speed * kotlin.math.cos(radians)

    private fun rotatedZ(speed: Double, radians: Double) = speed * kotlin.math.sin(radians)

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
        val simulator = MovementSimulator(PROFILE, GROUND, from, skipEntityCollisions = true)
        val result = simulator.tryTickMovement(
            MovementSimulationInput(
                forward = throttle,
                sprint = sprint && throttle > 0.0,
                rotation = Rotation(yaw, 0.0),
            ),
        )
        check(result is MovementSimulationStepResult.Advanced) { "the flat fixture must never block" }
        return simulator.state
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

        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8, eyeHeight = 1.62,
        )

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
