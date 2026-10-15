/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.Stance
import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import pathing.ProbeScenarios.moveLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A one-block gap in a tunnel.
 *
 * Two blocks high, the head reaches the ceiling a fifth of a block into the jump, the climb stops
 * there and the body falls back almost at once, yet a sprint jump still carries it over the gap:
 * the "head hitter" every player makes without thinking. Three blocks high, the free parabola
 * grazes the ceiling by a twentieth of a block at its top. Either way the arc flown is not the
 * free one, and a planner that only knew free arcs found no jump in a tunnel at all.
 */
class HeadHitterJumpTest {
	@Test
	fun `a one block gap under a low ceiling is jumped`() {
		for (ceiling in listOf(66, 67)) {
			val environment = tunnel(ceilingY = ceiling, roofed = -5..7)
			val start = Stance(-3, 64, 0)
			val goal = Stance(5, 64, 0)
			val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), start, goal)
			planner.repair()
			planner.expandSearchField()
			val route = assertNotNull(planner.routePlan(0L), "the gap under a ceiling at $ceiling must carry a coarse route")

			val outcome = walkFrom(route, planner, resting(Vec3d(-2.5, 64.0, 0.5), -90.0), environment)
			val path = assertNotNull(
				(outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial },
				"a jump over the gap under a ceiling at $ceiling must be certified, got $outcome",
			)
			assertEquals(goal, Stance.of(path.plan.frames.last().state.position, onGround = true))
		}
	}

	/**
	 * The ceiling only begins over the landing. Nothing stops the climb at take-off, so the head rises
	 * past the tunnel's mouth unless the jump comes down again before it: a slow jump launched late on
	 * the take-off block is low enough at the mouth and lands just inside.
	 */
	@Test
	fun `a gap into a low tunnel from open ground is jumped on a late low arc`() {
		val environment = tunnel(ceilingY = 66, roofed = 2..7)
		val start = Stance(-3, 64, 0)
		val goal = Stance(5, 64, 0)
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions(maxJumpSpan = 4)), start, goal)
		planner.repair()
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "a low arc reaches under the tunnel's mouth")

		val outcome = walkFrom(route, planner, resting(Vec3d(-2.5, 64.0, 0.5), -90.0), environment)
		val path = assertNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }, "the jump into the tunnel must be certified, got $outcome")
		assertEquals(goal, Stance.of(path.plan.frames.last().state.position, onGround = true))
	}

	private fun tunnel(ceilingY: Int, roofed: IntRange): SnapshotSimulationEnvironment {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in -5..7) for (z in -1..1) {
			if (x != 1) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
			if (x in roofed) blocks[BlockPos(x, ceilingY, z)] = SnapshotBlockPhysics.FULL_CUBE
			if (z != 0) for (y in 64 until ceilingY) blocks[BlockPos(x, y, z)] = SnapshotBlockPhysics.FULL_CUBE
		}
		return SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-16, 50, -16, 24, 80, 16), blocks)
	}
}
