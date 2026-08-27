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

package com.lambda.util.player.prediction

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.prediction.MovementInputProvider
import com.lambda.pathing.prediction.simulation.MovementSimulationInput
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.MovementSimulator
import com.lambda.pathing.prediction.simulation.PlayerPhysicsProfile
import com.lambda.pathing.prediction.SimulationEnvironment
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MovementSimulatorTest {
    @Test
    fun `reset replays an explicit input tape exactly`() {
        val initial = groundedState()
        val simulator = simulator(initial)
        val tape = buildList {
            repeat(5) { add(MovementSimulationInput(forward = 1.0)) }
            add(MovementSimulationInput(forward = 1.0, sprint = true, jump = true))
            repeat(10) { add(MovementSimulationInput(forward = 1.0, sprint = true)) }
            repeat(6) { add(MovementSimulationInput()) }
        }

        val first = tape.map { simulator.tickMovement(it).simulator.state }
        simulator.reset(initial)
        val replay = tape.map { simulator.tickMovement(it).simulator.state }

        first.zip(replay).forEachIndexed { index, (expected, actual) ->
            assertStateEquals(expected, actual, "frame $index")
        }
    }

    @Test
    fun `input provider and explicit inputs use the same simulation path`() {
        val initial = groundedState()
        val tape = listOf(
	        MovementSimulationInput(forward = 1.0),
	        MovementSimulationInput(forward = 1.0, strafe = 0.5),
	        MovementSimulationInput(forward = 1.0, sprint = true),
	        MovementSimulationInput(forward = 1.0, sprint = true, jump = true),
	        MovementSimulationInput(forward = 0.5, strafe = -0.5, sprint = true),
        )
        var cursor = 0
        val provided = MovementSimulator(
	        profile = PROFILE,
	        environment = FlatGroundEnvironment,
	        initialState = initial,
	        inputProvider = MovementInputProvider { tape[cursor++] },
        )
        val explicit = simulator(initial)

        tape.forEachIndexed { index, input ->
            val expected = explicit.tickMovement(input).simulator.state
            val actual = provided.tickMovement().simulator.state
            assertStateEquals(expected, actual, "frame $index")
        }
    }

    @Test
    fun `jump sets vertical velocity instead of adding grounded fall velocity`() {
        val simulator = simulator(groundedState())

        val tick = simulator.tickMovement(MovementSimulationInput(jump = true))

        assertClose(0.42, tick.position.y, "launch position")
        assertClose((0.42 - 0.08) * 0.98, tick.velocity.y, "post-gravity launch velocity")
        assertTrue(tick.isJumping)
        assertTrue(!tick.onGround)
    }

    @Test
    fun `floor collision preserves grounded state and vanilla stored fall velocity`() {
        val simulator = simulator(groundedState())

        val state = simulator.tickMovement(MovementSimulationInput()).simulator.state

        assertClose(0.0, state.position.y, "grounded y")
        assertClose(-0.0784, state.velocity.y, "stored grounded velocity")
        assertTrue(state.onGround)
        assertTrue(state.verticalCollision)
    }

    @Test
    fun `player horizontal dead zone uses current vanilla threshold`() {
        val initial = groundedState().copy(
            velocity = Vec3d(0.01, -0.0784, 0.0),
        )

        val tick = simulator(initial).tickMovement(MovementSimulationInput())

        assertTrue(tick.position.x > 0.0, "0.01 horizontal velocity must not be discarded")
    }

    @Test
    fun `releasing jump clears its cooldown`() {
        val simulator = simulator(groundedState())
        simulator.tickMovement(MovementSimulationInput(jump = true))

        val state = simulator.tickMovement(MovementSimulationInput()).simulator.state

        assertEquals(0, state.jumpingCooldown)
        assertTrue(!state.isJumping)
    }

    @Test
    fun `sprint input starts sprinting and increases ordinary ground progress`() {
        val walking = simulator(groundedState())
            .tickMovement(MovementSimulationInput(forward = 1.0))
        val sprinting = simulator(groundedState())
            .tickMovement(MovementSimulationInput(forward = 1.0, sprint = true))

        assertTrue(sprinting.position.z > walking.position.z)
        assertClose(
            walking.position.z * PlayerPhysicsProfile.SPRINT_SPEED_MULTIPLIER,
            sprinting.position.z,
            "sprint ground acceleration",
        )
    }

    @Test
    fun `releasing sprint while holding forward preserves vanilla sprint state`() {
        val simulator = simulator(groundedState())
        simulator.tickMovement(MovementSimulationInput(forward = 1.0, sprint = true))

        val released = simulator.tickMovement(MovementSimulationInput(forward = 1.0)).simulator.state

        assertTrue(released.isSprinting)
    }

    @Test
    fun `releasing forward clears vanilla sprint state`() {
        val simulator = simulator(groundedState())
        simulator.tickMovement(MovementSimulationInput(forward = 1.0, sprint = true))

        val coast = simulator.tickMovement(MovementSimulationInput()).simulator.state

        assertTrue(!coast.isSprinting)
    }

    @Test
    fun `a glancing edge collision is soft and does not cancel sprint`() {
        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(0.3001, 0.0, 0.5),
            rotation = Rotation(0.7, 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        )
        val simulator = MovementSimulator(PROFILE, glancingWallEnvironment(), initial)
        val input = MovementSimulationInput(
	        forward = 1.0,
	        sprint = true,
	        rotation = Rotation(0.7, 0.0),
        )

        val collision = simulator.tickMovement(input).simulator.state
        assertTrue(collision.horizontalCollision)
        assertTrue(collision.collidedSoftly)

        val retained = simulator.tickMovement(input).simulator.state
        assertTrue(retained.isSprinting)
    }

    @Test
    fun `current vanilla directional factor is applied to diagonal input`() {
        val cardinal = simulator(groundedState())
            .tickMovement(MovementSimulationInput(forward = 1.0))
        val diagonal = simulator(groundedState())
            .tickMovement(MovementSimulationInput(forward = 1.0, strafe = 1.0))

        assertTrue(
            diagonal.position.horizontalLength() > cardinal.position.horizontalLength(),
            "full diagonal input recovers magnitude 1 after cardinal input is damped to 0.98",
        )
    }

    @Test
    fun `sneak speed attribute is the complete multiplier`() {
        // Started already in the sneaking pose. Vanilla latches the pose before it takes
        // the tick's input, so the tick a body first presses sneak still moves at walking
        // speed -- measuring that tick would be measuring the lag, not the multiplier.
        val walking = simulator(groundedState())
            .tickMovement(MovementSimulationInput(forward = 1.0))
        val sneaking = simulator(groundedState(sneaking = true))
            .tickMovement(MovementSimulationInput(forward = 1.0, sneak = true))

        assertClose(
            walking.position.z * PROFILE.sneakSpeedModifier,
            sneaking.position.z,
            "sneak movement multiplier",
        )
    }

    /**
     * The crouch box, and the two bits of timing that make it match the client.
     *
     * This is the divergence a live descent failed on with every other number identical to
     * the last digit -- `Position(axis=box.maxY, expected=30.8, actual=30.5)`. The body was
     * moving correctly and simply was not the right height, because the simulator had no
     * pose at all.
     */
    @Test
    fun `the body takes the crouch box at the end of the tick it presses sneak`() {
        val sim = simulator(groundedState())
        assertClose(PROFILE.height, sim.state.boundingBox.lengthY, "a standing body starts standing")

        // Vanilla reads updatePose() off the live sneak key, not off the lagged pose, so
        // the box shrinks on the very tick the key goes down -- one tick before the speed
        // multiplier arrives.
        sim.tickMovement(MovementSimulationInput(forward = 1.0, sneak = true))
        assertClose(PROFILE.crouchHeight, sim.state.boundingBox.lengthY, "sneak tick ends crouched")

        sim.tickMovement(MovementSimulationInput(forward = 1.0, sneak = true))
        assertClose(PROFILE.crouchHeight, sim.state.boundingBox.lengthY, "still crouched while held")

        sim.tickMovement(MovementSimulationInput(forward = 1.0, sneak = false))
        assertClose(PROFILE.height, sim.state.boundingBox.lengthY, "releasing sneak stands back up")
    }

    /** The feet stay put: only the top of the box moves, or the body would sink. */
    @Test
    fun `crouching lowers the head and never the feet`() {
        val sim = simulator(groundedState())
        val standingFeet = sim.state.boundingBox.minY

        sim.tickMovement(MovementSimulationInput(sneak = true))
        val crouched = sim.state.boundingBox

        assertClose(standingFeet, crouched.minY, "the feet must not move")
        assertClose(standingFeet + PROFILE.crouchHeight, crouched.maxY, "the head comes down to the crouch height")
        assertClose(PROFILE.width, crouched.lengthX, "crouching does not change the width")
    }

    /**
     * A body cannot stand up into a ceiling -- and an environment that cannot answer the
     * question must not be the reason it stays down.
     *
     * [FlatGroundEnvironment] does not implement the space query, so it answers `null`.
     * Under a single boolean default that reads as "blocked", and a body that ever sneaked
     * would be stuck in a crouch for the rest of the tape.
     */
    @Test
    fun `an environment that cannot answer space queries still lets the body stand up`() {
        val sim = simulator(groundedState())
        sim.tickMovement(MovementSimulationInput(sneak = true))
        assertClose(PROFILE.crouchHeight, sim.state.boundingBox.lengthY, "crouched")

        sim.tickMovement(MovementSimulationInput(sneak = false))
        assertClose(PROFILE.height, sim.state.boundingBox.lengthY, "must not be trapped crouching")
    }

    private fun simulator(initial: MovementSimulationState) = MovementSimulator(
	    profile = PROFILE,
	    environment = FlatGroundEnvironment,
	    initialState = initial,
    )

    private fun groundedState(sneaking: Boolean = false) = MovementSimulationState.synthetic(
        profile = PROFILE,
        position = Vec3d.ZERO,
        rotation = Rotation(0.0, 0.0),
        velocity = Vec3d(0.0, -0.0784, 0.0),
        onGround = true,
        isSneaking = sneaking,
    )

    private fun glancingWallEnvironment(): SnapshotSimulationEnvironment {
        val blocks = buildMap {
            for (x in -3..3) for (z in -3..6) {
                put(BlockPos(x, -1, z), SnapshotBlockPhysics.FULL_CUBE)
            }
            for (z in -2..5) put(BlockPos(-1, 0, z), SnapshotBlockPhysics.FULL_CUBE)
        }
        return SnapshotSimulationEnvironment.synthetic(
	        SimulationSnapshotBounds(-3, -3, -3, 3, 4, 6),
            blocks,
        )
    }

    private fun assertStateEquals(
	    expected: MovementSimulationState,
	    actual: MovementSimulationState,
	    message: String,
    ) {
        assertVecEquals(expected.position, actual.position, "$message position")
        assertVecEquals(expected.velocity, actual.velocity, "$message velocity")
        assertEquals(expected.rotation, actual.rotation, "$message rotation")
        assertEquals(expected.onGround, actual.onGround, "$message onGround")
        assertEquals(expected.isJumping, actual.isJumping, "$message isJumping")
        assertEquals(expected.isSprinting, actual.isSprinting, "$message isSprinting")
        assertEquals(expected.isSneaking, actual.isSneaking, "$message isSneaking")
        assertEquals(expected.jumpingCooldown, actual.jumpingCooldown, "$message jumpingCooldown")
        assertEquals(expected.velocityAffectingPos, actual.velocityAffectingPos, "$message velocityAffectingPos")
        assertEquals(expected.horizontalCollision, actual.horizontalCollision, "$message horizontalCollision")
        assertEquals(expected.collidedSoftly, actual.collidedSoftly, "$message collidedSoftly")
        assertEquals(expected.verticalCollision, actual.verticalCollision, "$message verticalCollision")
        assertClose(expected.boundingBox.minX, actual.boundingBox.minX, "$message box minX")
        assertClose(expected.boundingBox.minY, actual.boundingBox.minY, "$message box minY")
        assertClose(expected.boundingBox.minZ, actual.boundingBox.minZ, "$message box minZ")
        assertClose(expected.boundingBox.maxX, actual.boundingBox.maxX, "$message box maxX")
        assertClose(expected.boundingBox.maxY, actual.boundingBox.maxY, "$message box maxY")
        assertClose(expected.boundingBox.maxZ, actual.boundingBox.maxZ, "$message box maxZ")
    }

    private fun assertVecEquals(expected: Vec3d, actual: Vec3d, message: String) {
        assertClose(expected.x, actual.x, "$message x")
        assertClose(expected.y, actual.y, "$message y")
        assertClose(expected.z, actual.z, "$message z")
    }

    private fun assertClose(expected: Double, actual: Double, message: String) {
        assertTrue(abs(expected - actual) <= EPSILON, "$message: expected <$expected>, actual <$actual>")
    }

    private object FlatGroundEnvironment : SimulationEnvironment {
        override fun slipperiness(pos: BlockPos) = 0.6
        override fun velocityMultiplier(pos: BlockPos) = 1.0
        override fun jumpVelocityMultiplier(pos: BlockPos) = 1.0

        override fun adjustMovementForCollisions(
            movement: Vec3d,
            boundingBox: Box,
            onGround: Boolean,
            stepHeight: Double,
        ): Vec3d {
            val adjustedY = if (boundingBox.minY + movement.y < FLOOR_Y) {
                FLOOR_Y - boundingBox.minY
            } else {
                movement.y
            }
            return Vec3d(movement.x, adjustedY, movement.z)
        }

        /** An infinite floor at [FLOOR_Y]: whatever column the feet are over supports them. */
        override fun findSupportingBlockPos(box: Box, entityPos: Vec3d): BlockPos? =
            if (box.minY <= FLOOR_Y) {
                BlockPos(MathHelper.floor(entityPos.x), MathHelper.floor(FLOOR_Y) - 1, MathHelper.floor(entityPos.z))
            } else {
                null
            }

        override fun isFenceLike(pos: BlockPos) = false
    }

    private companion object {
        const val EPSILON = 1.0E-7
        const val FLOOR_Y = 0.0

        val PROFILE = PlayerPhysicsProfile(
	        movementSpeed = 0.1,
	        sneakSpeedModifier = 0.3,
	        gravity = 0.08,
	        jumpStrength = 0.42,
	        stepHeight = 0.6,
	        jumpBoostVelocityModifier = 0.0,
	        slowFalling = false,
	        width = 0.6,
	        height = 1.8,
	        eyeHeight = 1.62,
        )
    }
}
