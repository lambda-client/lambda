package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import com.lambda.pathing.world.streaming.EntityObstacles
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import minecraft.MinecraftBootstrap
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import pathing.ProbeScenarios.moveLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Mobs as obstacles of the trajectory: the cells a pushing entity stands in are closed while it stands there,
 * and a walk plans around them instead of through them.
 */
class EntityObstaclesTest {
	init {
		MinecraftBootstrap.ensure()
	}

	@Test
	fun `a cell reopens as soon as the mob leaves it`() {
		val obstacles = EntityObstacles()
		val cell = BlockPos.asLong(4, 64, 0)
		obstacles.update(LongOpenHashSet(longArrayOf(cell)))
		assertTrue(cell in obstacles, "an occupied cell is closed")
		obstacles.update(LongOpenHashSet())
		assertFalse(cell in obstacles, "the cell reopens the tick the mob leaves")
	}

	@Test
	fun `unchanged occupancy keeps publishing the same cells`() {
		val obstacles = EntityObstacles()
		obstacles.update(LongOpenHashSet(longArrayOf(BlockPos.asLong(4, 64, 0))))
		val published = obstacles.cells
		obstacles.update(LongOpenHashSet(longArrayOf(BlockPos.asLong(4, 64, 0))))
		assertSame(published, obstacles.cells, "consumers detect obstacle changes by identity")
	}

	@Test
	fun `a walk keeps out of a cell a mob stands in while the coarse route runs through it`() {
		val mob = BlockPos(4, 64, 0)
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in -4..12) for (z in -4..4) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
		val environment = SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-8, 56, -8, 16, 72, 8), blocks)
		environment.entityCells(longArrayOf(mob.asLong()))
		val start = MovementSimulationState.synthetic(ProbeScenarios.PROFILE, Vec3d(0.5, 64.0, 0.5), Rotation(-90.0, 0.0), onGround = true)
		val path = certifiedWalk(environment, Stance(0, 64, 0), Stance(8, 64, 0), start)
		val frames = path.plan.frames
		assertTrue(frames.none { it.state.boundingBox.intersects(mob.x.toDouble(), mob.y.toDouble(), mob.z.toDouble(), mob.x + 1.0, mob.y + 1.0, mob.z + 1.0) }, "the walk must keep out of the mob's cell")
		assertEquals(Stance(8, 64, 0), Stance.of(frames.last().state.position, onGround = true))
	}

	private fun certifiedWalk(environment: SnapshotSimulationEnvironment, from: Stance, to: Stance, start: MovementSimulationState) = run {
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), from, to)
		assertTrue(planner.repair().converged, "the route must converge")
		planner.expandSearchField()
		val route = checkNotNull(planner.routePlan(0L)) { "the floor must carry a route around the mob" }
		val outcome = walkFrom(route, planner, start, environment)
		checkNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }) { "the walk must be certified, got $outcome" }
	}
}
