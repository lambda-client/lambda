package pathing

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.Stance
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.session.rebased
import com.lambda.pathing.session.walkHorizon
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.Vec3d
import pathing.ProbeScenarios.PROFILE
import pathing.ProbeScenarios.moveLibrary
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PushRebaseTest {
    private val environment = SnapshotSimulationEnvironment(
        bounds = SimulationSnapshotBounds(-16, 0, -16, 63, 15, 31),
        sections = buildMap {
            for (sx in -1..3) for (sz in -1..1) put(ChunkSectionPos.asLong(sx, 0, sz), floor())
        },
        defaultBlock = SnapshotBlockPhysics.AIR,
    )

    private fun floor(): ImmutableSnapshotSection = ImmutableSnapshotSection.Builder().apply {
        for (x in 0..15) for (z in 0..15) for (y in 0..15) set(x, y, z, if (y <= 3) SnapshotBlockPhysics.FULL_CUBE else SnapshotBlockPhysics.AIR)
    }.build(expectedWrites = 4096)

    private fun walk(): PublishedPath {
        val start = Stance(2, 4, 0)
        val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), start, Stance(30, 4, 0))
        assertTrue(planner.repair().converged)
        planner.expandSearchField()
        val outcome = walkHorizon(
            checkNotNull(planner.routePlan(0L)), planner, resting(Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5), -90.0), PROFILE,
            environment, MotionConstraints(), publish = { _, _ -> }, started = System.currentTimeMillis(),
        )
        return assertNotNull((outcome as? PathPlanResult.Planned)?.path, "no plan: $outcome")
    }

    @Test
    fun `a push replays the rest of the tape from the pushed state`() {
        val path = walk()
        val from = 20
        val reached = path.plan.frames[from - 1].state
        val pushed = reached.copy(velocity = reached.velocity.add(0.0, 0.0, 0.04))
        val bridge = assertNotNull(rebased(path, from, pushed, environment, 0L), "a small push must keep the tape")
        assertEquals(path.plan.tape.frameCount - from, bridge.plan.tape.frameCount)
        assertEquals(pushed.velocity, bridge.plan.initialState.velocity)
        assertTrue(bridge.partial)
        bridge.plan.frames.forEachIndexed { index, frame ->
            val planned = path.plan.frames[from + index].state.position
            val actual = frame.state.position
            assertTrue(hypot(actual.x - planned.x, actual.z - planned.z) <= 0.5 && abs(actual.y - planned.y) <= 0.25, "frame $index drifted to $actual from $planned")
        }
    }

    @Test
    fun `a shove that leaves the certified line is refused`() {
        val path = walk()
        val reached = path.plan.frames[19].state
        assertNull(rebased(path, 20, reached.copy(velocity = reached.velocity.add(0.0, 0.0, 0.6)), environment, 0L))
    }
}
