package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.prediction.simulation.MovementSimulationInput
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.MovementSimulator
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import pathing.ProbeScenarios.PROFILE

/** A rollout resumed from any frame's state must match the continuous one, bit for bit. */
class SlimeResumeDeterminismTest {
    @Test
    fun `resuming from a state snapshot reproduces the continuous rollout`() {
        val blocks = buildMap {
            for (x in -2..2) {
                for (z in -4..2) put(BlockPos(x, 99, z), SnapshotBlockPhysics.FULL_CUBE)
                for (z in 3..8) put(
                    BlockPos(x, 99, z),
                    SnapshotBlockPhysics.of(VoxelShapes.fullCube(), bounceFactor = 1.0, dampensSteppingSpeed = true),
                )
                for (z in 6..8) put(
                    BlockPos(x, 100, z),
                    SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.0625, 1.0)),
                )
            }
        }
        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 90, -16, 16, 115, 16), blocks,
        )
        val inputs = buildList {
            repeat(2) { add(MovementSimulationInput(forward = 1.0, sprint = true, rotation = Rotation(0.0, 0.0))) }
            add(MovementSimulationInput(forward = 1.0, sprint = true, jump = true, rotation = Rotation(0.0, 0.0)))
            repeat(20) { add(MovementSimulationInput(forward = 1.0, sprint = true, rotation = Rotation(0.0, 0.0))) }
            repeat(30) { add(MovementSimulationInput(rotation = Rotation(0.0, 0.0))) }
        }
        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(0.5, 100.0, 0.5),
            rotation = Rotation(0.0, 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        )
        val continuous = MovementSimulator(PROFILE, environment, initial)
        val states = inputs.map { continuous.tickMovement(it).simulator.state }

        for (resumeAt in inputs.indices) {
            val resumed = MovementSimulator(PROFILE, environment, if (resumeAt == 0) initial else states[resumeAt - 1])
            for (frame in resumeAt until inputs.size) {
                val state = resumed.tickMovement(inputs[frame]).simulator.state
                assertEquals(
                    states[frame].position, state.position,
                    "resume@$resumeAt frame $frame position",
                )
                assertEquals(
                    states[frame].velocity, state.velocity,
                    "resume@$resumeAt frame $frame velocity",
                )
            }
        }
    }

}
