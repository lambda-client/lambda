/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.Stance
import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.world.center
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import pathing.ProbeScenarios.moveLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Whatever stands on a block leaves the rest of that block's top as a ledge.
 *
 * A fence, a wall or the next segment of a dripstone blocks the middle of its cell, so the body
 * cannot stand where the cell's centre is. It stands beside the obstacle instead, flush against it
 * and overhanging the edge of the block underneath, on any side where that block's top reaches past
 * the obstacle far enough to hold the body and the cell beside leaves room for the overhang. Each
 * side is its own ledge: a column that narrows as it rises is climbed ledge by ledge.
 */
class LedgeStanceTest {
	@Test
	fun `a bridge with a fence along it is walked beside the fence`() {
		val environment = bridge(wallSide = null)
		val moves = moveLibrary(SimpleMoveOptions())
		assertTrue(moves.isStance(environment, Stance(4, 64, 0)), "the bridge beside its fence is a stance")

		val path = assertNotNull(walkAcross(environment), "a walk along the fence must be certified")
		assertTrue(path.none { it.y < 63.9 }, "the body must never fall off the bridge")
		assertEquals(GOAL, Stance.of(path.last(), onGround = true))
	}

	@Test
	fun `a fence against a wall is walked on its open side`() {
		val environment = bridge(wallSide = -1)
		assertEquals(0.9375, Stance(4, 64, 0).center(environment).z, 1e-6, "the body stands flush against the fence, away from the wall")
		val path = assertNotNull(walkAcross(environment), "a walk along the open side must be certified")
		assertTrue(path.filter { it.x in 1.0..7.0 }.all { it.z > 0.9 }, "the body must keep to the open side of the fence")
	}

	@Test
	fun `a wall along a bridge leaves a ledge the body hangs over`() {
		val environment = bridge(wallSide = null, railing = WALL_ALONG_X)
		val ledge = Stance(4, 64, 0).center(environment).z
		assertTrue(ledge < 0.0 || ledge > 1.0, "the body stands flush against the wall, its centre past the bridge's edge: $ledge")
		val path = assertNotNull(walkAcross(environment), "a walk along the wall's ledge must be certified")
		assertTrue(path.none { it.y < 63.9 }, "the body must never fall off the ledge")
	}

	@Test
	fun `a post in a corridor one block wide closes it`() {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in -4..8) {
			blocks[BlockPos(x, 63, 0)] = SnapshotBlockPhysics.FULL_CUBE
			for (y in 64..65) for (z in listOf(-1, 1)) blocks[BlockPos(x, y, z)] = SnapshotBlockPhysics.FULL_CUBE
		}
		blocks[BlockPos(2, 64, 0)] = POST
		val environment = SnapshotSimulationEnvironment.synthetic(BOUNDS, blocks)
		val moves = moveLibrary(SimpleMoveOptions())
		assertTrue(moves.isStance(environment, Stance(2, 64, 0)), "the body still stands in front of the post")
		val planner = CoarsePlanner(environment, moves, Stance(-2, 64, 0), Stance(6, 64, 0))
		planner.repair()
		assertNull(planner.routePlan(0L), "but no ledge runs past it, so the post closes the corridor")
	}

	/**
	 * The field course without its trapdoors: posts along a raised row, the last one a pane whose
	 * arms close its east side, then a doorway one block wide between two blocks. The row is walked
	 * along its north side, but getting from there into the doorway means turning the pane's corner
	 * where the doorway's north block stands, which no body fits through.
	 */
	@Test
	fun `a ledge line does not turn a corner a block fills`() {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in -4..6) blocks[BlockPos(x, 63, 0)] = SnapshotBlockPhysics.FULL_CUBE
		for (x in 5..6) for (z in -1..1) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
		for (x in 1..3) blocks[BlockPos(x, 64, 0)] = SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.375, 0.0, 0.375, 0.625, 1.5, 1.0), fenceLike = true)
		for (y in 64..65) {
			blocks[BlockPos(0, y, 0)] = PANE_EAST_SOUTH
			for (z in listOf(-1, 1)) blocks[BlockPos(-1, y, z)] = SnapshotBlockPhysics.FULL_CUBE
		}
		val environment = SnapshotSimulationEnvironment.synthetic(BOUNDS, blocks)
		val moves = moveLibrary(SimpleMoveOptions())
		assertTrue(moves.isStance(environment, Stance(2, 64, 0)), "the posts leave a ledge along their north side")
		val planner = CoarsePlanner(environment, moves, Stance(5, 64, 0), Stance(-3, 64, 0))
		planner.repair()
		assertNull(planner.routePlan(0L), "the doorway's north block closes the corner between the ledge and the doorway")
	}

	/**
	 * A path one block wide turns a corner where a wall stands on the corner block, and a column of
	 * blocks fills the inside of the bend so closely that no body turns it there. The body goes round
	 * the outside instead, on the rim of the corner block, overhanging the drop on two sides.
	 */
	@Test
	fun `a wall on the corner block of a narrow path is walked round its outside`() {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (z in 0..4) blocks[BlockPos(0, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
		for (x in -4..0) blocks[BlockPos(x, 63, 0)] = SnapshotBlockPhysics.FULL_CUBE
		for (z in 1..3) for (y in 64..66) blocks[BlockPos(-1, y, z)] = SnapshotBlockPhysics.FULL_CUBE
		for (y in 64..66) blocks[BlockPos(0, y, 0)] = WALL_POST
		val environment = SnapshotSimulationEnvironment.synthetic(BOUNDS, blocks)
		val start = Stance(0, 64, 3)
		val goal = Stance(-3, 64, 0)
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), start, goal)
		assertTrue(planner.repair().converged, "the bend must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "the bend must carry a coarse route")
		assertTrue(Stance(0, 64, 0) in route.nodes, "the route must pass the corner block: ${route.nodes}")

		val outcome = walkFrom(route, planner, resting(Vec3d(0.5, 64.0, 3.5), 180.0), environment)
		val path = assertNotNull(
			(outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial },
			"a walk round the wall must be certified, got $outcome",
		)
		assertTrue(path.plan.frames.none { it.state.position.y < 63.9 }, "the body must never fall off the path")
		assertEquals(goal, Stance.of(path.plan.frames.last().state.position, onGround = true))
	}

	/**
	 * Two stairs side by side with their steps on the outer halves, and two upside-down stairs
	 * above them the same way round, leave a doorway two blocks high and one wide that straddles
	 * the line between the cells. The body walks through on the lower halves of the stairs.
	 */
	@Test
	fun `stairs leave a doorway between two cells`() {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in -4..4) for (z in 0..1) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
		for (z in -1..2) for (y in 64..66) blocks[BlockPos(0, y, z)] = SnapshotBlockPhysics.FULL_CUBE
		blocks[BlockPos(0, 64, 0)] = stairs(stepNorth = true, upsideDown = false)
		blocks[BlockPos(0, 64, 1)] = stairs(stepNorth = false, upsideDown = false)
		blocks.remove(BlockPos(0, 65, 0))
		blocks.remove(BlockPos(0, 65, 1))
		blocks[BlockPos(0, 66, 0)] = stairs(stepNorth = true, upsideDown = true)
		blocks[BlockPos(0, 66, 1)] = stairs(stepNorth = false, upsideDown = true)
		val environment = SnapshotSimulationEnvironment.synthetic(BOUNDS, blocks)
		val start = Stance(3, 64, 0)
		val goal = Stance(-3, 64, 0)
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), start, goal)
		assertTrue(planner.repair().converged, "the doorway must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "the stairs must carry a coarse route")

		val outcome = walkFrom(route, planner, resting(Vec3d(3.5, 64.0, 0.5), 90.0), environment)
		val path = assertNotNull(
			(outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial },
			"a walk through the stairs must be certified, got $outcome",
		)
		assertEquals(goal, Stance.of(path.plan.frames.last().state.position, onGround = true))
	}

	/**
	 * Pointed dripstone narrows from base to middle to tip, each segment leaving a rim of the one
	 * below: a sixteenth of a block per side at the top. The platform beside it is three blocks up,
	 * out of reach of any jump; the only way onto it is up the column, ledge by ledge.
	 */
	@Test
	fun `a dripstone column is climbed ledge by ledge`() {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in -4..4) for (z in -1..1) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
		for (x in -4..-1) for (z in -1..1) for (y in 64..66) blocks[BlockPos(x, y, z)] = SnapshotBlockPhysics.FULL_CUBE
		blocks[BlockPos(0, 64, 0)] = dripstone(2, 16)
		blocks[BlockPos(0, 65, 0)] = dripstone(4, 16)
		blocks[BlockPos(0, 66, 0)] = dripstone(5, 11)
		val environment = SnapshotSimulationEnvironment.synthetic(BOUNDS, blocks)
		val start = Stance(3, 64, 0)
		val goal = Stance(-3, 67, 0)
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), start, goal)
		assertTrue(planner.repair().converged, "the column must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "the column must carry a coarse route")
		assertTrue(Stance(0, 65, 0) in route.nodes, "the route must climb onto the base's ledge: ${route.nodes}")

		val outcome = walkFrom(route, planner, resting(Vec3d(3.5, 64.0, 0.5), 90.0), environment)
		val path = assertNotNull(
			(outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial },
			"a climb up the column must be certified, got $outcome",
		)
		assertEquals(goal, Stance.of(path.plan.frames.last().state.position, onGround = true))
	}

	/**
	 * The field course's gap: a path one block wide with a pane on one side of a missing block and
	 * a fence on the other, each two high and joined to a block beside the path, so the middle of
	 * both cells is taken. The body takes off from the ledge beside the fence and lands on the ledge
	 * beside the pane, a jump any player makes without slowing down.
	 */
	@Test
	fun `a gap between a pane and a fence is jumped from ledge to ledge`() {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in -4..8) if (x != 2) blocks[BlockPos(x, 63, 0)] = SnapshotBlockPhysics.FULL_CUBE
		for (y in 64..65) {
			blocks[BlockPos(1, y, 0)] = SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.4375, 0.0, 0.4375, 0.5625, 1.0, 1.0))
			blocks[BlockPos(3, y, 0)] = SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.375, 0.0, 0.375, 0.625, 1.5, 1.0), fenceLike = true)
			for (x in listOf(1, 3)) blocks[BlockPos(x, y, 1)] = SnapshotBlockPhysics.FULL_CUBE
		}
		val environment = SnapshotSimulationEnvironment.synthetic(BOUNDS, blocks)
		val start = Stance(6, 64, 0)
		val goal = Stance(-2, 64, 0)
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), start, goal)
		assertTrue(planner.repair().converged, "the gap must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "the gap between the ledges must carry a coarse route")

		val outcome = walkFrom(route, planner, resting(Vec3d(6.5, 64.0, 0.5), 90.0), environment)
		val path = assertNotNull(
			(outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial },
			"a jump from ledge to ledge must be certified, got $outcome",
		)
		assertTrue(path.plan.frames.none { it.state.position.y < 63.0 }, "the body must never fall into the gap")
		assertEquals(goal, Stance.of(path.plan.frames.last().state.position, onGround = true))
	}

	private fun bridge(wallSide: Int?, railing: SnapshotBlockPhysics = FENCE_ALONG_X): SnapshotSimulationEnvironment {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in -4..-1) for (z in -2..2) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
		for (x in 9..12) for (z in -2..2) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
		for (x in 0..8) {
			blocks[BlockPos(x, 63, 0)] = SnapshotBlockPhysics.FULL_CUBE
			blocks[BlockPos(x, 64, 0)] = railing
			if (wallSide != null) for (y in 63..66) blocks[BlockPos(x, y, wallSide)] = SnapshotBlockPhysics.FULL_CUBE
		}
		return SnapshotSimulationEnvironment.synthetic(BOUNDS, blocks)
	}

	private fun walkAcross(environment: SnapshotSimulationEnvironment): List<Vec3d>? {
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), START, GOAL)
		assertTrue(planner.repair().converged, "the bridge must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "the bridge must carry a coarse route")
		assertTrue(route.nodes.any { it.x in 0..8 } && route.nodes.all { it.x !in 0..8 || it.z == 0 }, "the route must cross the bridge: ${route.nodes}")
		val outcome = walkFrom(route, planner, resting(Vec3d(-1.5, 64.0, 0.5), -90.0), environment)
		return (outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }?.plan?.frames?.map { it.state.position }
	}

	/** A stair along x: a bottom slab with the step on one half, or the same upside down. */
	private fun stairs(stepNorth: Boolean, upsideDown: Boolean): SnapshotBlockPhysics {
		val slab = if (upsideDown) VoxelShapes.cuboid(0.0, 0.5, 0.0, 1.0, 1.0, 1.0) else VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.5, 1.0)
		val (low, high) = if (upsideDown) 0.0 to 0.5 else 0.5 to 1.0
		val step = if (stepNorth) VoxelShapes.cuboid(0.0, low, 0.0, 1.0, high, 0.5) else VoxelShapes.cuboid(0.0, low, 0.5, 1.0, high, 1.0)
		return SnapshotBlockPhysics.of(VoxelShapes.union(slab, step))
	}

	/** A pointed dripstone segment, centred, [inset] sixteenths in from each side and [height] sixteenths tall. */
	private fun dripstone(inset: Int, height: Int) =
		SnapshotBlockPhysics.of(VoxelShapes.cuboid(inset / 16.0, 0.0, inset / 16.0, 1 - inset / 16.0, height / 16.0, 1 - inset / 16.0))

	private companion object {
		val START = Stance(-2, 64, 0)
		val GOAL = Stance(10, 64, 0)
		val BOUNDS = SimulationSnapshotBounds(-16, 50, -16, 24, 80, 16)

		/** A fence connected east and west: the post and both arms, 1.5 tall. */
		val FENCE_ALONG_X = SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.0, 0.0, 0.375, 1.0, 1.5, 0.625), fenceLike = true)

		val POST = SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.375, 0.0, 0.375, 0.625, 1.5, 0.625), fenceLike = true)

		/** A wall standing alone: its post, 1.5 tall as walls collide. */
		val WALL_POST = SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.25, 0.0, 0.25, 0.75, 1.5, 0.75), fenceLike = true)

		/** A glass pane connected east and south. */
		val PANE_EAST_SOUTH = SnapshotBlockPhysics.of(
			VoxelShapes.union(
				VoxelShapes.cuboid(0.4375, 0.0, 0.4375, 0.5625, 1.0, 1.0),
				VoxelShapes.cuboid(0.5625, 0.0, 0.4375, 1.0, 1.0, 0.5625),
			),
		)

		/** A wall connected east and west: its post and both arms, 1.5 tall as walls collide. */
		val WALL_ALONG_X = SnapshotBlockPhysics.of(
			VoxelShapes.union(
				VoxelShapes.cuboid(0.25, 0.0, 0.25, 0.75, 1.5, 0.75),
				VoxelShapes.cuboid(0.0, 0.0, 0.3125, 1.0, 1.5, 0.6875),
			),
			fenceLike = true,
		)
	}
}
