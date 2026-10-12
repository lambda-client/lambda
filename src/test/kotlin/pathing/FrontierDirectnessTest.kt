package pathing

import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import pathing.ProbeScenarios.moveLibrary
import net.minecraft.util.math.ChunkSectionPos
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FrontierDirectnessTest {
	private val coast = object : CoarseVoxelView {
		override val simulableStanceY = -8..16

		override fun isKnown(x: Int, y: Int, z: Int): Boolean = x in -LOADED until LOADED && z in -LOADED until LOADED

		override fun forEachKnownSection(action: (Long) -> Unit) {
			for (sx in (-LOADED shr 4) until (LOADED shr 4)) for (sz in (-LOADED shr 4) until (LOADED shr 4)) for (sy in -1..0) {
				action(ChunkSectionPos.asLong(sx, sy, sz))
			}
		}

		override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel = when {
			!isKnown(x, y, z) -> CoarseVoxel.UNKNOWN
			y < 0 -> CoarseVoxel.FULL_BLOCK
			y == 0 -> if (z < 0) CoarseVoxel.FULL_BLOCK else CoarseVoxel.WATER
			else -> CoarseVoxel.AIR
		}
	}

	private fun routeEnd(options: SimpleMoveOptions): Stance {
		val planner = CoarsePlanner(coast, moveLibrary(options), Stance(-LOADED + 4, 1, -4), Stance(1_000_000, 1, 1_000_000))
		assertTrue(planner.repair(maxExpansions = 2_000_000).converged)
		planner.expandSearchField()
		return assertNotNull(planner.routePlan(0L), "anchors=${planner.anchorCount} ${planner.unreachableReport} ${planner.routeFailureReport()}").goal
	}

	@Test
	fun `a far goal across open water heads for the corner of the loaded world instead of the coast`() {
		val coastal = routeEnd(SimpleMoveOptions(frontierTicksPerBlock = 0.0))
		val direct = routeEnd(SimpleMoveOptions())
		println("[frontier] land-rate end=$coastal default end=$direct")
		assertTrue(coastal.z < 0, "pricing unknown terrain at walking speed keeps the route on the coast: $coastal")
		assertTrue(direct.z > LOADED / 2 && direct.x > LOADED / 2, "the default prices progress above swimming and swims for the corner: $direct")
	}

	private companion object {
		const val LOADED = 64
	}
}
