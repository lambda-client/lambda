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
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import pathing.ProbeScenarios.moveLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A doorway in a wall that has no other way through.
 *
 * A door is a panel three pixels thick in both halves of its doorway. Open, it lies folded against
 * one jamb and the body passes beside it with a hand's breadth to spare; closed, it spans the
 * doorway along one face. Either way the middle of the cell is free, so whether the doorway is a
 * route depends on the face the body crosses to get in, not on where it stands.
 */
class DoorPassageTest {
	@Test
	fun `an open door is walked through whichever jamb it folds against`() {
		for (alongX in listOf(false, true)) for (jamb in listOf(0.0, 13 / 16.0)) for (turn in listOf(-4, 0, 4)) {
			val panel = if (alongX) VoxelShapes.cuboid(0.0, 0.0, jamb, 1.0, 1.0, jamb + 3 / 16.0)
			else VoxelShapes.cuboid(jamb, 0.0, 0.0, jamb + 3 / 16.0, 1.0, 1.0)
			val environment = wallWithDoor(panel, alongX)
			val start = if (alongX) Stance(-4, 64, -turn) else Stance(-turn, 64, -4)
			val goal = if (alongX) Stance(4, 64, turn) else Stance(turn, 64, 4)
			val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), start, goal)
			assertTrue(planner.repair().converged, "the doorway must converge")
			planner.expandSearchField()
			val route = assertNotNull(planner.routePlan(0L), "an open door must carry a coarse route")
			assertTrue(Stance(0, 64, 0) in route.nodes, "the route must pass the doorway: ${route.nodes}")

			val outcome = walkFrom(route, planner, resting(Vec3d(start.x + 0.5, 64.0, start.z + 0.5)), environment)
			val path = assertNotNull(
				(outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial },
				"a walk through the door folded at $jamb (along x: $alongX, turning $turn) must be certified, got $outcome",
			)
			assertEquals(goal, Stance.of(path.plan.frames.last().state.position, onGround = true))
		}
	}

	/**
	 * The panel of a closed door lies along the face of its cell, clear of the middle a body stands
	 * in. A planner that only asked whether the body fits in each cell walked straight into it and
	 * jumped at it from the side, and the search dead-ended against the panel.
	 */
	@Test
	fun `a closed door is a wall`() {
		for (alongX in listOf(false, true)) {
			val panel = if (alongX) VoxelShapes.cuboid(0.0, 0.0, 0.0, 3 / 16.0, 1.0, 1.0)
			else VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 1.0, 3 / 16.0)
			val start = if (alongX) Stance(-4, 64, 0) else Stance(0, 64, -4)
			val goal = if (alongX) Stance(4, 64, 0) else Stance(0, 64, 4)
			val planner = CoarsePlanner(wallWithDoor(panel, alongX), moveLibrary(SimpleMoveOptions()), start, goal)
			planner.repair()
			assertNull(planner.routePlan(0L), "a closed door leaves no way through the wall (along x: $alongX)")
		}
	}

	/**
	 * The field course that showed it: a raised doorway between two walls with trapdoors hanging
	 * open against its far face, and the floor beyond one block lower. Walking in is fine; the only
	 * way on is a drop or jump out through the far face, which the trapdoors close.
	 */
	@Test
	fun `trapdoors hanging on the far face close a raised doorway`() {
		val panel = SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.0, 0.0, 0.0, 3 / 16.0, 1.0, 1.0))
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in -6..6) blocks[BlockPos(x, 62, 0)] = SnapshotBlockPhysics.FULL_CUBE
		for (x in 0..6) blocks[BlockPos(x, 63, 0)] = SnapshotBlockPhysics.FULL_CUBE
		for (y in 63..66) for (z in listOf(-1, 1)) blocks[BlockPos(0, y, z)] = SnapshotBlockPhysics.FULL_CUBE
		blocks[BlockPos(0, 64, 0)] = panel
		blocks[BlockPos(0, 65, 0)] = panel
		val environment = SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-16, 55, -16, 16, 80, 16), blocks)
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), Stance(4, 64, 0), Stance(-4, 63, 0))
		planner.repair()
		assertNull(planner.routePlan(0L), "the trapdoors leave no way out of the doorway")
	}

	private fun wallWithDoor(panel: VoxelShape, alongX: Boolean): SnapshotSimulationEnvironment {
		val door = SnapshotBlockPhysics.of(panel)
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in -6..6) for (z in -6..6) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
		for (along in -6..6) for (y in 64..67) blocks[if (alongX) BlockPos(0, y, along) else BlockPos(along, y, 0)] = SnapshotBlockPhysics.FULL_CUBE
		blocks[BlockPos(0, 64, 0)] = door
		blocks[BlockPos(0, 65, 0)] = door
		return SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-16, 55, -16, 16, 80, 16), blocks)
	}
}
