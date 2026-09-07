package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.MovementSimulator
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import kotlin.test.Test
import kotlin.test.assertTrue
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import pathing.ProbeScenarios.PROFILE

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
            val state = sim.tickMovement(input)
            println("f=$frame pos=${state.position} vy=${state.velocity.y} ground=${state.onGround} support=${state.supportingBlockPos}")
            if (state.velocity.y > 0.2 && frame > 6) reflected = true
        }
        assertTrue(reflected, "the landing on slime must reflect the fall")
    }

}
