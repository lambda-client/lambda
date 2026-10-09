package pathing

import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarseEdgeCache
import com.lambda.pathing.coarse.MomentumRules
import com.lambda.pathing.coarse.PackedStance
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.graph.EdgeList
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.shape.VoxelShapes
import pathing.ProbeScenarios.moveLibrary
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * D* Lite only stays correct under incremental repair while its estimate is consistent: no
 * coarse edge may cost less than the estimate across it. Chained moves are charged less
 * than their template ticks, so the estimate must be derived from that discounted floor --
 * an estimate from raw ticks expanded correctly once and then looped in the field.
 */
class SearchHeuristicTest {
	private val slime = SnapshotBlockPhysics.of(VoxelShapes.fullCube(), bounceFactor = 1.0, dampensSteppingSpeed = true)

	private fun height(x: Int, z: Int): Int {
		var h = x * 374761393 + z * 668265263
		h = (h xor (h ushr 13)) * 1274126177
		return (h ushr 28) % 3
	}

	private fun world(withSlime: Boolean): SnapshotSimulationEnvironment {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in 0..23) for (z in 0..23) {
			if ((x + 3 * z) % 11 == 0) continue
			for (y in 60..(62 + height(x, z))) blocks[BlockPos(x, y, z)] = SnapshotBlockPhysics.FULL_CUBE
		}
		if (withSlime) for (x in 10..12) for (z in 10..12) blocks[BlockPos(x, 58, z)] = slime
		return SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-8, 40, -8, 31, 90, 31), blocks)
	}

	@Test
	fun `no coarse edge costs less than the search estimate across it`() {
		val options = SimpleMoveOptions(maxJumpDrop = 2, maxJumpSpan = 5, allowClimbing = true, allowSlimeBounces = true)
		for (withSlime in listOf(false, true)) {
			val environment = world(withSlime)
			val moves = moveLibrary(options)
			val edges = CoarseEdgeCache(environment.cursor(), moves)
			val momentum = MomentumRules(moves.templates)
			val successors = EdgeList()
			var checked = 0
			for (x in 0..23) for (z in 0..23) for (y in 59..66) for (speed in SpeedClass.entries) {
				val node = PackedStance.pack(x, y, z, speed)
				successors.clear()
				momentum.successors(edges, node, successors)
				for (i in 0 until successors.size) {
					val target = successors.node(i)
					for (grounded in if (withSlime) listOf(false) else listOf(false, true)) {
						val estimate = momentum.heuristic(node, target, grounded)
						assertTrue(
							estimate <= successors.cost(i) + 1e-9,
							"${PackedStance.describe(node)} -> ${PackedStance.describe(target)} costs ${successors.cost(i)} but is estimated at $estimate (grounded=$grounded)",
						)
					}
					checked++
				}
			}
			assertTrue(checked > 10_000, "the floor must yield many edges, got $checked")
		}
	}
}
