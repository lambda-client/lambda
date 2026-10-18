/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SharedEdges
import com.lambda.pathing.core.CellBox
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.shape.VoxelShapes
import pathing.ProbeScenarios.moveLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedEdgesTest {

	@Test
	fun `a wall seen through one leg reroutes every leg sharing the edges`() {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (z in -6..40) for (x in -6..40) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
		for (z in listOf(20, 40)) blocks[BlockPos(WALL_X, 79, z)] = SnapshotBlockPhysics.FULL_CUBE
		val environment = SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-10, 58, -10, 44, 90, 44), blocks)
		val moves = moveLibrary(SimpleMoveOptions())
		val waypoint = Stance(0, 64, 30)
		val goal = Stance(30, 64, 30)
		val shared = SharedEdges(listOf(waypoint, goal))
		val first = CoarsePlanner(environment, moves, Stance(0, 64, 0), waypoint, shared = shared)
		val second = CoarsePlanner(environment, moves, waypoint, goal, shared = shared)
		first.repair()
		second.repair()
		val before = checkNotNull(second.routePlan(0L))

		val wall = CellBox(WALL_X..WALL_X, 64..65, 18..42)
		for (z in wall.zs) for (y in wall.ys) {
			val key = ChunkSectionPos.asLong(WALL_X shr 4, y shr 4, z shr 4)
			val section = checkNotNull(environment.section(key))
			environment.install(key, section.with(WALL_X, y, z, SnapshotBlockPhysics.of(VoxelShapes.fullCube())))
		}
		first.terrainChanged(listOf(wall))
		first.repair()
		second.repair()
		val after = checkNotNull(second.routePlan(0L))
		assertTrue(after.lowerBoundTicks > before.lowerBoundTicks, "the second leg ignores the wall: ${before.lowerBoundTicks} -> ${after.lowerBoundTicks}")

		val fresh = CoarsePlanner(environment, moves, waypoint, goal)
		fresh.repair()
		val expected = checkNotNull(fresh.routePlan(0L))
		assertEquals(expected.lowerBoundTicks, after.lowerBoundTicks, 1e-9, "a shared leg costs the same as a fresh planner")
	}

	private companion object {
		const val WALL_X = 15
	}
}
