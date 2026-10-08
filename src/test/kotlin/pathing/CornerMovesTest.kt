package pathing

import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import pathing.ProbeScenarios.moveLibrary
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Moves that pass a corner instead of running a straight line between stance centres:
 * diagonal blocks touching only at their corners with a wall on one side of every step
 * (crossed by jumps that pass the first wall corner and land on the open corner of a block
 * further on, or walked around a single corner), and gap jumps whose straight line clips
 * the corner of a block (flown past a point beside that corner).
 */
class CornerMovesTest {
	@Test
	fun `diagonal blocks along a wall are crossed corner to corner`() {
		val chain = (0..5).map { BlockPos(it, 100, it) }
		val wall = (0..4).flatMap { listOf(BlockPos(it + 1, 101, it), BlockPos(it + 1, 102, it)) }
		val route = arrives(chain + wall, Stance(0, 101, 0), Stance(5, 101, 5))
		assertTrue(route.all { it.via != null }, "every edge must pass beside a wall corner, got ${route.map { it.movement to it.via }}")
		assertTrue(route.size < chain.size - 1, "the chain must be jumped, not walked block by block, got ${route.map { it.movement }}")
	}

	@Test
	fun `a gap in diagonal blocks along a wall is jumped from corner to corner`() {
		val chain = listOf(0, 1, 2, 5).map { BlockPos(it, 100, -it) } + BlockPos(9, 100, -5)
		val wall = (0..4).flatMap { k -> (100..103).map { BlockPos(k + 1, it, -k) } }
		for (offset in listOf(0.52 to 0.38, 0.23 to 0.44, 0.21 to 0.27, 0.5 to 0.5)) {
			val route = arrives(chain + wall, Stance(0, 101, 0), Stance(9, 101, -5), -145.0, offset)
			assertTrue(route.any { it.via != null }, "the gap must be crossed past the wall corners, got ${route.map { it.movement }}")
		}
	}

	@Test
	fun `diagonal blocks along a wall stepping one up and one down are crossed`() {
		val heights = listOf(0, 0, 0, 1, 1, 0)
		val chain = heights.flatMapIndexed { k, h -> (100..100 + h).map { BlockPos(k, it, -k) } } + BlockPos(9, 100, -5)
		val wall = (0..4).flatMap { k -> (100..103).map { BlockPos(k + 1, it, -k) } }
		for (offset in listOf(0.52 to 0.38, 0.23 to 0.44, 0.5 to 0.5)) {
			arrives(chain + wall, Stance(0, 101, 0), Stance(9, 101, -5), -145.0, offset)
		}
	}

	@Test
	fun `a single diagonal step past a wall is walked around the corner`() {
		val blocks = listOf(BlockPos(0, 100, 0), BlockPos(1, 100, 1), BlockPos(1, 101, 0), BlockPos(1, 102, 0))
		val route = arrives(blocks, Stance(0, 101, 0), Stance(1, 101, 1))
		val step = route.single()
		assertTrue(step.movement == MovementId.WALK && step.via != null, "the step must be a corner walk, got ${step.movement} ${step.via}")
	}

	@Test
	fun `a diagonal jump bends past a block clipping its corner`() {
		val blocks = listOf(BlockPos(0, 100, 0), BlockPos(2, 100, 2), BlockPos(1, 101, 0), BlockPos(1, 102, 0))
		val route = arrives(blocks, Stance(0, 101, 0), Stance(2, 101, 2))
		val jump = route.single()
		val via = assertNotNull(jump.via, "the clipped diagonal must be offered as a bent jump")
		assertTrue(via.z > via.x, "the bend must pass on the open side of the block, got $via")
	}

	@Test
	fun `an off-axis jump bends past a pillar beside its line`() {
		val blocks = listOf(BlockPos(0, 100, 0), BlockPos(3, 100, 1), BlockPos(2, 101, 0), BlockPos(2, 102, 0))
		val route = arrives(blocks, Stance(0, 101, 0), Stance(3, 101, 1))
		assertNotNull(route.single().via, "the clipped knight jump must be offered as a bent jump")
	}

	private fun arrives(
		blocks: List<BlockPos>,
		start: Stance,
		goal: Stance,
		yaw: Double? = null,
		offset: Pair<Double, Double> = 0.5 to 0.5,
	): List<com.lambda.pathing.actions.CoarseEdge> {
		val environment = SnapshotSimulationEnvironment.synthetic(
			SimulationSnapshotBounds(-8, 90, -8, 16, 115, 16),
			blocks.associateWith { SnapshotBlockPhysics.FULL_CUBE },
		)
		val planner = CoarsePlanner(environment, moveLibrary(), start, goal)
		planner.repair()
		val route = assertNotNull(planner.routePlan(0L), "the coarse layer must route ${planner.routeFailureReport()}")
		val outcome = ProbeScenarios.plan(ProbeScenarios.Scenario("corner", environment, start, goal, SimpleMoveOptions()), initialYaw = yaw, initialOffset = offset)
		val path = assertNotNull((outcome.result as? PathPlanResult.Planned)?.path, "the search must arrive: ${outcome.result}")
		val end = path.plan.frames.last().state.position
		assertTrue(
			end.y == goal.y.toDouble() && end.x > goal.x - 0.3 && end.x < goal.x + 1.3 && end.z > goal.z - 0.3 && end.z < goal.z + 1.3,
			"the tape must end on the goal block, ended at $end",
		)
		return route.edges
	}
}
