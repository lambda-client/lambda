package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.search.AttemptAccumulator
import com.lambda.pathing.search.Finisher
import com.lambda.pathing.search.GatedRollout
import com.lambda.pathing.search.RolloutGate
import com.lambda.pathing.search.SearchProbe
import com.lambda.pathing.rollout.SimulatedTrajectoryFrame
import com.lambda.pathing.rollout.TrajectoryRollout
import com.lambda.pathing.rollout.TrajectoryRolloutTermination
import com.lambda.pathing.search.ValueAnchor
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import io.mockk.every
import io.mockk.mockk
import net.minecraft.util.math.Vec3d
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FinisherTest {
    private val environment = SnapshotSimulationEnvironment.synthetic(
        SimulationSnapshotBounds(-2, 95, -2, 2, 105, 2), emptyMap(),
    )
    private val field = mockk<ValueField>()
    private val gate = mockk<RolloutGate>()
    private val attempts = AttemptAccumulator(SearchProbe.NONE)
    private val state = MovementSimulationState.synthetic(
        profile = ProbeScenarios.PROFILE, position = Vec3d(0.5, 100.0, 0.5),
        rotation = Rotation(0.0, 0.0), velocity = Vec3d.ZERO, onGround = true,
    )
    private val anchor = ValueAnchor(state, Stance(0, 100, 0), 0, 0, 0, null, emptyList())
    private val frames = listOf(SimulatedTrajectoryFrame(0, MovementSimulationInput(), state))
    private val successful = GatedRollout(
        TrajectoryRollout(state, frames, TrajectoryRolloutTermination.Completed), 0, false,
    )

    init {
        every { field.chain(any(), any(), any(), any()) } returns listOf(anchor.stance)
        every { field.reachesGoal(any()) } returns true
        every { field.view } returns environment
        every { gate.run(any(), any(), any(), any()) } returns successful
    }

    @Test
    fun `a successful cached finish never enumerates the fallback parameter grid`() {
        var reads = 0
        val brakes = object : AbstractList<Double>() {
            override val size = 7
            override fun get(index: Int): Double {
                reads++
                return 0.25 + index * 0.1
            }
        }
        val config = MotionConstraints(stableStopFrames = 1, brakeDistances = brakes)
        val cached = Finisher(
            field, environment, config, gate, attempts,
            goalPoint = { HorizontalPoint(0.5, 100.0, 0.5) },
        )
        reads = 0
        assertNotNull(cached.finishFrom(anchor))
        assertEquals(14, reads)
        reads = 0
        assertNotNull(cached.finishFrom(anchor))
        assertEquals(0, reads)
    }
}
