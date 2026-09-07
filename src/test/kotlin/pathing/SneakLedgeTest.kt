/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.MovementSimulator
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.test.Test
import kotlin.test.assertTrue
import pathing.ProbeScenarios.PROFILE

/**
 * Vanilla's sneak ledge clip, in a worker simulation.
 *
 * This used to be unavailable off the client thread: the probe needed a live entity, so a
 * planning simulation answered "no ledge anywhere" and quietly let a sneaking body walk off
 * a block the real client stops dead on. Anything a control program did with sneak would
 * therefore have certified against physics the client does not have.
 */
class SneakLedgeTest {
    @Test
    fun `a sneaking body stops at the lip instead of walking off`() {
        val walked = runToEdge(sneak = false)
        val sneaked = runToEdge(sneak = true)

        assertTrue(
            walked.y < DECK_Y - 0.5,
            "the control run must actually leave the deck, but ended at $walked",
        )
        assertTrue(
            sneaked.y >= DECK_Y - 1.0E-6,
            "a sneaking body must stay on the deck, but ended at $sneaked",
        )
        // Vanilla clips when the box would have *nothing* under it, not when the centre
        // passes the face -- which is why a sneaking player's toes hang over the edge. So
        // the bound is the last position that still overlaps the deck at all.
        assertTrue(
            sneaked.x < DECK_EDGE_X + BODY_HALF_WIDTH,
            "a sneaking body must stay overlapping the deck, but reached x = ${sneaked.x}",
        )
        assertTrue(
            sneaked.x > DECK_EDGE_X - BODY_HALF_WIDTH,
            "the clip must let the body reach the lip, not stop it short at ${sneaked.x}",
        )
    }

    /** Walks east off a deck that ends at x = 4, with or without sneak held. */
    private fun runToEdge(sneak: Boolean): Vec3d {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -4..3) for (z in -2..2) blocks[BlockPos(x, 62, z)] = SnapshotBlockPhysics.FULL_CUBE
        val environment = SnapshotSimulationEnvironment.synthetic(
	        SimulationSnapshotBounds(-10, 50, -8, 12, 80, 8), blocks,
        )
        val simulator = MovementSimulator(
	        profile = PROFILE,
	        environment = environment,
	        initialState = MovementSimulationState.synthetic(
		        profile = PROFILE,
		        position = Vec3d(0.5, DECK_Y, 0.5),
		        rotation = FACING_EAST,
		        onGround = true,
	        ),
        )

        repeat(TICKS) {
            simulator.tryTickMovement(
	            MovementSimulationInput(forward = 1.0, sneak = sneak, rotation = FACING_EAST),
            )
        }
        return simulator.state.position
    }

    private companion object {
        const val DECK_Y = 63.0

        /** The deck's last block is x = 3, so its face is at x = 4. */
        const val DECK_EDGE_X = 4.0

        const val BODY_HALF_WIDTH = 0.3

        const val TICKS = 60

        val FACING_EAST = Rotation(-90.0, 0.0)

    }
}
