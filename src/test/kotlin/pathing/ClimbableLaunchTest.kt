/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.Medium
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
import kotlin.test.assertTrue

/**
 * A body whose feet are in a ladder or vine cell is climbing: vanilla clamps its horizontal speed
 * to 0.15 a tick on each axis and catches its fall, and a body hanging on a vine has no ground to
 * push off at all. No jump starts there, and an arc whose feet pass through such a cell is caught by
 * it instead of flying on; letting go and dropping off the side still works. The field case: a swamp
 * tree's vines hanging over water, where the planner jumped onwards from the vines.
 */
class ClimbableLaunchTest {
	private val moves = moveLibrary(SimpleMoveOptions(allowClimbing = true, maxJumpDrop = 2))

	@Test
	fun `no jump starts from a vine hanging over open water`() {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in -4..8) for (z in -4..4) blocks[BlockPos(x, 61, z)] = SnapshotBlockPhysics.water()
		for (x in -1..1) for (z in -1..1) blocks[BlockPos(x, 67, z)] = LEAVES
		for (y in 62..66) blocks[BlockPos(2, y, 0)] = VINE
		for (x in 4..6) blocks[BlockPos(x, 62, 0)] = SnapshotBlockPhysics.FULL_CUBE
		val environment = SnapshotSimulationEnvironment.synthetic(BOUNDS, blocks)
		val launches = moves.edgesFrom(environment, Stance(2, 64, 0)).filter { it.movement in LAUNCHES }
		assertTrue(launches.isEmpty(), "a body holding a vine has nothing to jump from: ${launches.map { "${it.movement} ${it.to}" }}")
	}

	@Test
	fun `no jump starts from ground inside a vine`() {
		val blocks = floor()
		blocks[BlockPos(0, 64, 0)] = VINE
		val environment = SnapshotSimulationEnvironment.synthetic(BOUNDS, blocks)
		val launches = moves.edgesFrom(environment, Stance(0, 64, 0)).filter { it.movement in LAUNCHES }
		assertTrue(launches.isEmpty(), "a body standing in a vine is climbing: ${launches.map { "${it.movement} ${it.to}" }}")
	}

	/** Letting go of a ladder and stepping off its side needs no push: the drop below stays a move. */
	@Test
	fun `a drop off the side of a ladder is still offered`() {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (z in -1..1) blocks[BlockPos(0, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
		for (x in 1..4) for (z in -1..1) blocks[BlockPos(x, 60, z)] = SnapshotBlockPhysics.FULL_CUBE
		blocks[BlockPos(0, 64, 0)] = VINE
		val environment = SnapshotSimulationEnvironment.synthetic(BOUNDS, blocks)
		val drops = moves.edgesFrom(environment, Stance(0, 64, 0)).filter { it.movement == MovementId.DROP }
		assertTrue(drops.any { it.to == Stance(1, 61, 0) }, "the drop beside the ladder is offered: ${drops.map { it.to }}")
	}

	/**
	 * The field course: a vine column, a one-block gap, and a block one lower beyond it. Pressing
	 * forward alone slides the body down the vine at up to 0.15 a tick while it slowly gathers
	 * speed, and it leaves the column too low to reach the block; holding jump while still in the
	 * vine climbs instead, so the body leaves higher and carries across.
	 */
	@Test
	fun `a vine is left across a one block gap by climbing out of it`() {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (z in -3..0) for (x in -1..1) blocks[BlockPos(x, 60, z)] = SnapshotBlockPhysics.FULL_CUBE
		for (y in 61..68) blocks[BlockPos(0, y, 0)] = VINE
		for (z in 2..4) for (x in -1..1) blocks[BlockPos(x, 64, z)] = SnapshotBlockPhysics.FULL_CUBE
		val environment = SnapshotSimulationEnvironment.synthetic(BOUNDS, blocks)
		val start = Stance(0, 61, -2)
		val goal = Stance(0, 65, 4)
		val planner = CoarsePlanner(environment, moves, start, goal)
		assertTrue(planner.repair().converged, "the vine must converge")
		planner.expandField(extraTicks = 60.0, maxExpansions = 40_000)
		val route = assertNotNull(planner.routePlan(0L), "the vine and the block beyond the gap must carry a coarse route")
		assertTrue(route.edges.any { it.movement == MovementId.DROP && environment.medium(it.from.x, it.from.y, it.from.z) == Medium.CLIMBABLE }, "the route leaves the vine by dropping across the gap: ${route.edges.map { "${it.movement} ${it.to}" }}")

		val outcome = walkFrom(route, planner, resting(Vec3d(0.5, 61.0, -1.5), 0.0), environment)
		val path = assertNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }, "the crossing must be certified, got $outcome")
		assertEquals(goal, Stance.of(path.plan.frames.last().state.position, onGround = true))
	}

	@Test
	fun `a jump whose feet pass through a vine is caught by it`() {
		val open = SnapshotSimulationEnvironment.synthetic(BOUNDS, gap())
		assertTrue(jumpsAcross(open), "the open gap is jumped")
		val hung = gap().apply { for (y in 64..66) put(BlockPos(2, y, 0), VINE) }
		assertTrue(!jumpsAcross(SnapshotSimulationEnvironment.synthetic(BOUNDS, hung)), "a vine hanging in the gap catches the body")
	}

	private fun jumpsAcross(environment: SnapshotSimulationEnvironment): Boolean =
		moves.edgesFrom(environment, Stance(0, 64, 0)).any { it.movement == MovementId.JUMP && it.to == Stance(4, 64, 0) }

	/**
	 * The swamp field case: a vine column hanging from a mangrove's leaves ends a block below their
	 * top. Pushing into the block a ladder hangs on carries the body over its ledge; around a corner
	 * there is nothing to push against, the feet leave the vine and the body falls back down it.
	 */
	@Test
	fun `no diagonal step up leaves a hanging vine`() {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (y in 64..71) blocks[BlockPos(0, y, 0)] = VINE
		for (y in 70..72) blocks[BlockPos(1, y, 0)] = LEAVES
		blocks[BlockPos(1, 71, -1)] = LEAVES
		val environment = SnapshotSimulationEnvironment.synthetic(BOUNDS, blocks)
		val rising = moves.edgesFrom(environment, Stance(0, 71, 0)).filter { it.to.y > 71 && (it.to.x != 0 || it.to.z != 0) }
		assertTrue(rising.isEmpty(), "a hanging vine has no ledge to step up from: ${rising.map { "${it.movement} ${it.to}" }}")
	}

	@Test
	fun `a vine standing on ground still steps up diagonally`() {
		val blocks = floor()
		blocks[BlockPos(0, 64, 0)] = VINE
		blocks[BlockPos(1, 64, 1)] = SnapshotBlockPhysics.FULL_CUBE
		val environment = SnapshotSimulationEnvironment.synthetic(BOUNDS, blocks)
		val steps = moves.edgesFrom(environment, Stance(0, 64, 0)).filter { it.movement == MovementId.STEP_UP && it.to == Stance(1, 65, 1) }
		assertTrue(steps.isNotEmpty(), "the ground under the vine carries the step up")
	}

	private fun floor() = HashMap<BlockPos, SnapshotBlockPhysics>().apply {
		for (x in -4..8) for (z in -4..4) put(BlockPos(x, 63, z), SnapshotBlockPhysics.FULL_CUBE)
	}

	private fun gap() = HashMap<BlockPos, SnapshotBlockPhysics>().apply {
		for (x in listOf(-2, -1, 0, 4, 5, 6)) for (z in -1..1) put(BlockPos(x, 63, z), SnapshotBlockPhysics.FULL_CUBE)
	}

	private companion object {
		val BOUNDS = SimulationSnapshotBounds(-16, 50, -16, 24, 80, 16)

		val VINE = SnapshotBlockPhysics(collisionShape = VoxelShapes.empty(), coarseVoxel = CoarseVoxel.CLIMBABLE)

		val LEAVES = SnapshotBlockPhysics.FULL_CUBE

		val LAUNCHES = setOf(MovementId.JUMP, MovementId.BOUNCE, MovementId.LADDER_CATCH)
	}
}
