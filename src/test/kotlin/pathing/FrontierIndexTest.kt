package pathing

import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import pathing.ProbeScenarios.moveLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The frontier is every stance that borders terrain the planner has not captured, each with
 * its optimistic edge to the goal, kept exact by terrain events alone. A partial sweep from
 * the body used to mint anchors: when it found nothing (the body mid-jump, in water, on
 * fresh terrain) the forward anchors retired while the stale ones behind survived, and a
 * journey to (1M, 1M) turned around and walked 1100 blocks back.
 */
class FrontierIndexTest {
	private class World {
		val delivered = HashSet<PathingChunk>()

		val view = object : CoarseVoxelView {
			override val simulableStanceY = 0..32

			override fun isKnown(x: Int, y: Int, z: Int): Boolean = PathingChunk(x shr 4, z shr 4) in delivered

			override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel = when {
				!isKnown(x, y, z) -> CoarseVoxel.UNKNOWN
				y <= 4 -> CoarseVoxel.FULL_BLOCK
				else -> CoarseVoxel.AIR
			}
		}

		fun deliver(centerX: Int, centerZ: Int, radius: Int): List<PathingChunk> =
			(centerX - radius..centerX + radius).flatMap { x -> (centerZ - radius..centerZ + radius).map { z -> PathingChunk(x, z) } }
				.filter(delivered::add)

		fun bordersFrontier(stance: Stance): Boolean =
			listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).any { (dx, dz) -> !view.isKnown(stance.x + dx, stance.y, stance.z + dz) }
	}

	@Test
	fun `arrivals alone keep the frontier complete and exact`() {
		val world = World()
		val goal = Stance(0, 5, 2000)
		val planner = CoarsePlanner(world.view, moveLibrary(), Stance(8, 5, 8), goal)
		planner.chunksChanged(world.deliver(0, 0, 2))
		planner.chunksChanged(world.deliver(0, 4, 2))
		planner.chunksChanged(world.deliver(1, 7, 1))

		val expected = world.delivered.flatMap { chunk ->
			(0..15).flatMap { dx -> (0..15).map { dz -> Stance((chunk.x shl 4) + dx, 5, (chunk.z shl 4) + dz) } }
		}.filter(world::bordersFrontier).toSet()
		assertEquals(expected, planner.optimisticAnchors.keys, "every stance at the edge of the delivered world, and nothing else")
	}

	@Test
	fun `a route never falls back on the frontier it left behind`() {
		val world = World()
		val start = Stance(8, 5, 8)
		val goal = Stance(8, 5, 2000)
		val planner = CoarsePlanner(world.view, moveLibrary(), start, goal)
		planner.chunksChanged(world.deliver(0, 0, 3))
		planner.repair()
		val first = assertNotNull(planner.routePlan(1L))

		// The world streams on ahead while nothing else happens: no sweep, no valid body.
		var revision = 1L
		for (center in 4..40 step 4) {
			planner.chunksChanged(world.deliver(0, center, 3))
			planner.repair()
			val route = assertNotNull(planner.routePlan(++revision), "a route must survive arrival $center")
			assertTrue(route.goal.z >= ((center + 3) shl 4) - 1 || route.goal == goal, "the route must end at the forward edge, ended at ${route.goal}")
			assertTrue(route.goal.z > first.goal.z, "the route turned back to ${route.goal}")
		}
		assertTrue(planner.anchorCount > 0)
	}
}
