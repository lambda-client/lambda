package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.search.AttemptAccumulator
import com.lambda.pathing.search.FinishPlanner
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
import io.mockk.verify
import net.minecraft.util.math.Vec3d
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FinishPlannerBudgetTest {
    private val environment = SnapshotSimulationEnvironment.synthetic(
        SimulationSnapshotBounds(-2, 95, -2, 2, 105, 2), emptyMap(),
    )
    private val field = mockk<ValueField>()
    private val gate = mockk<RolloutGate>()
    private val attempts = AttemptAccumulator()
    private val state = MovementSimulationState.synthetic(
        profile = ProbeScenarios.PROFILE, position = Vec3d(0.5, 100.0, 0.5),
        rotation = Rotation(0.0, 0.0), velocity = Vec3d.ZERO, onGround = true,
    )
    private val anchor = ValueAnchor(state, Stance(0, 100, 0), 0, 0, 0, 0, null, emptyList(), 0)
    private val frames = listOf(SimulatedTrajectoryFrame(0, MovementSimulationInput(), state))
    private val successful = GatedRollout(
        TrajectoryRollout(state, frames, TrajectoryRolloutTermination.Completed), 0, false,
    )
    private val planner = FinishPlanner(
        field, environment, MotionConstraints(stableStopFrames = 1), gate, attempts, SearchProbe.NONE,
        goalPoint = { HorizontalPoint(0.5, 100.0, 0.5) }, routeLastIndex = { 1 }, progressOf = { 0 },
    )

    init {
        every { field.chain(any(), any(), any(), any()) } returns listOf(anchor.stance)
        every { field.reachesGoal(any()) } returns true
        every { field.view } returns environment
        every { gate.run(any(), any(), any(), any()) } returns successful
    }

    @Test
    fun `denied budget starts no simulator attempt`() {
        assertNull(planner.finishFrom(anchor) { false })
        assertEquals(0, attempts.count)
        verify(exactly = 0) { gate.run(any(), any(), any(), any()) }
    }

    @Test
    fun `budget exhaustion keeps the best already certified terminal`() {
        var remaining = 1
        val result = assertNotNull(planner.finishFrom(anchor) { remaining-- > 0 })
        assertEquals(frames, result.tailFrames)
        assertEquals(1, attempts.count)
        // Cached parameters still need a new rollout permit from a later call.
        assertNull(planner.finishFrom(anchor) { false })
        assertEquals(1, attempts.count)
        assertNotNull(planner.finishFrom(anchor))
        assertEquals(2, attempts.count)
    }

    @Test
    fun `failed parameters consume permits too`() {
        every { gate.run(any(), any(), any(), any()) } returns GatedRollout(successful.rollout, null, true)
        var remaining = 2
        assertNull(planner.finishFrom(anchor) { remaining-- > 0 })
        assertEquals(2, attempts.count)
        verify(exactly = 2) { gate.run(any(), any(), any(), any()) }
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
        val cached = FinishPlanner(
            field, environment, config, gate, attempts, SearchProbe.NONE,
            goalPoint = { HorizontalPoint(0.5, 100.0, 0.5) }, routeLastIndex = { 1 }, progressOf = { 0 },
        )
        reads = 0 // Ignore configuration validation; measure only finish parameter generation.
        assertNotNull(cached.finishFrom(anchor))
        assertEquals(14, reads)
        reads = 0
        assertNotNull(cached.finishFrom(anchor))
        assertEquals(0, reads)
    }

    @Test
    fun `unreachable terminal does not consume an attempt`() {
        every { field.reachesGoal(any()) } returns false
        var permits = 0
        assertNull(planner.finishFrom(anchor) { permits++; true })
        assertEquals(0, permits)
        assertEquals(0, attempts.count)
    }
}
