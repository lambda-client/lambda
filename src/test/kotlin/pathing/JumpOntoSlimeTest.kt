package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.prediction.simulation.MovementSimulationInput
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.MovementSimulator
import com.lambda.pathing.prediction.simulation.PlayerPhysicsProfile
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import kotlin.test.Test
import kotlin.test.assertTrue
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes

class JumpOntoSlimeTest {
    @Test
    fun `a sprint jump landing on slime reflects the fall`() {
        val blocks = buildMap {
            for (x in -2..2) {
                for (z in -4..2) put(BlockPos(x, 99, z), SnapshotBlockPhysics.FULL_CUBE)
                for (z in 3..8) put(
                    BlockPos(x, 99, z),
                    SnapshotBlockPhysics.of(VoxelShapes.fullCube(), bounceFactor = 1.0, dampensSteppingSpeed = true),
                )
            }
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 90, -16, 16, 115, 16), blocks,
        )
        val sim = MovementSimulator(
            profile = PROFILE,
            environment = environment,
            initialState = MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(0.5, 100.0, 0.5),
                rotation = Rotation(0.0, 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0),
                onGround = true,
            ),
        )
        var reflected = false
        repeat(16) { frame ->
            val input = MovementSimulationInput(
                forward = 1.0, sprint = true, jump = frame == 2, rotation = Rotation(0.0, 0.0),
            )
            val state = sim.tickMovement(input).simulator.state
            println("f=$frame pos=${state.position} vy=${state.velocity.y} ground=${state.onGround} support=${state.supportingBlockPos}")
            if (state.velocity.y > 0.2 && frame > 6) reflected = true
        }
        assertTrue(reflected, "the landing on slime must reflect the fall")
    }

    private companion object {
        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8, eyeHeight = 1.62,
        )
    }
}
