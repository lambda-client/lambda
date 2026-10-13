package pathing

import com.lambda.pathing.actions.CoarseEdge
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarseEdgeCache
import com.lambda.pathing.coarse.EdgeStamper
import com.lambda.pathing.coarse.PackedStance
import com.lambda.pathing.coarse.SpeedClass
import com.lambda.pathing.coarse.StanceMemo
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.launch.ArcSample
import com.lambda.pathing.physics.UnsupportedPhysics
import com.lambda.pathing.physics.UnsupportedPhysicsKind
import com.lambda.pathing.debug.ParkourCourseLayout
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.shape.VoxelShapes
import pathing.ProbeScenarios.moveLibrary
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bulk edge stamper must reproduce the per-stance edge computation exactly: every
 * template at every stance yields the same edge (or none), on plain terrain where the
 * bit path decides, and next to slabs, mud, fences, carpets, slime, ladders, water, phantom
 * blocks and unstreamed sections where it must hand over to the exact path.
 */
class EdgeStamperTest {
	@Test
	fun `stamped edges equal exact edges on the bedrock field`() {
		val environment = ProbeScenarios.bedrockEnvironment()
		val compared = compare(environment, moveLibrary(SimpleMoveOptions(maxJumpDrop = 2)), -2..BedrockFieldLayout.LENGTH, 56..70, -BedrockFieldLayout.HALF_WIDTH..BedrockFieldLayout.HALF_WIDTH)
		assertTrue(compared > 10_000, "the field must yield many edges to compare, got $compared")
	}

	@Test
	fun `stamped edges equal exact edges on parkour courses`() {
		for (seed in 1..3) {
			val scenario = ProbeScenarios.parkour(seed)
			val pads = ParkourCourseLayout.course(jumps = 20, seed = seed).pads
			compare(
				scenario.environment, moveLibrary(scenario.options),
				pads.minOf { it.x } - 2..pads.maxOf { it.x } + 2, 98..112, pads.minOf { it.z } - 2..pads.maxOf { it.z } + 2,
			)
		}
	}

	@Test
	fun `stamped edges equal exact edges next to special blocks and unknown sections`() {
		for (seed in 1..4) {
			val environment = specialWorld(Random(seed))
			for (options in listOf(SimpleMoveOptions(), FIELD_OPTIONS)) {
				compare(environment, moveLibrary(options), 0..47, 60..72, 0..47)
			}
		}
	}

	/**
	 * Ceilings two, three and four blocks above uneven floors with holes: jumps under them fly
	 * arcs cut short where the head meets the ceiling, which the stamper must find exactly where
	 * the exact probe does.
	 */
	@Test
	fun `stamped edges equal exact edges under ceilings`() {
		for (seed in 1..3) {
			val random = Random(seed)
			val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
			for (x in -4..51) for (z in -4..51) {
				val floor = 62 + random.nextInt(2)
				if (random.nextInt(5) != 0) for (y in 58..floor) blocks[BlockPos(x, y, z)] = SnapshotBlockPhysics.FULL_CUBE
				val ceiling = floor + 3 + random.nextInt(3)
				for (y in ceiling..ceiling + 1) blocks[BlockPos(x, y, z)] = SnapshotBlockPhysics.FULL_CUBE
			}
			val environment = SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-16, 48, -16, 63, 95, 63), blocks)
			for (options in listOf(SimpleMoveOptions(), FIELD_OPTIONS)) {
				compare(environment, moveLibrary(options), 0..47, 60..68, 0..47)
			}
		}
	}

	private fun compare(environment: SnapshotSimulationEnvironment, moves: SimpleMoveLibrary, xs: IntRange, ys: IntRange, zs: IntRange): Int {
		val view = environment.cursor()
		val stamper = EdgeStamper(view, moves, StanceMemo(view, moves))
		val cache = CoarseEdgeCache(view, moves)
		var compared = 0
		for (y in ys) for (z in zs) for (x in xs) {
			val origin = Stance(x, y, z)
			val node = PackedStance.pack(origin, SpeedClass.STOPPED)
			val tabled = ArrayList<String>()
			cache.forEachEdgeFrom(node) { template, ticks, standing -> tabled += "${template.id.value} $ticks $standing" }
			val incoming = ArrayList<String>()
			cache.forEachEdgeTo(node) { template, ticks, standing -> incoming += "${template.id.value} $ticks $standing" }
			if (!moves.isStance(view, origin)) {
				assertTrue(tabled.isEmpty() && incoming.isEmpty(), "a non-stance has no edges: $origin")
				continue
			}
			val exactEdges = ArrayList<String>()
			for (template in moves.templates(view, origin)) {
				val exact = template.edge(view, origin)
				val stamped = stamper.edge(template, origin)
				assertSame(exact, stamped, "template ${template.id} ${template.movement}(${template.dx},${template.dy},${template.dz}) at $origin")
				if (exact != null) {
					compared++
					if (template.movement != MovementId.DIVE || moves.isStance(view, exact.to)) exactEdges += "${template.id.value} ${exact.lowerBoundTicks} ${exact.standingStart}"
				}
				if (exact != null && compared % READ_SET_SAMPLE == 0) {
					assertEquals(exact.readSet.toSet(), stamped!!.readSet.toSet(), "read set of template ${template.id} at $origin")
				}
			}
			assertEquals(exactEdges, tabled, "outgoing edges at $origin")
			val exactIncoming = moves.templates.mapNotNull { template ->
				val from = origin.offset(-template.dx, -template.dy, -template.dz)
				if (!moves.isStance(view, from) || template !in moves.templates(view, from)) return@mapNotNull null
				template.edge(view, from)?.let { "${template.id.value} ${it.lowerBoundTicks} ${it.standingStart}" }
			}
			assertEquals(exactIncoming, incoming, "incoming edges at $origin")
		}
		return compared
	}

	private fun assertSame(exact: CoarseEdge?, stamped: CoarseEdge?, context: String) {
		if (exact == null || stamped == null) return assertEquals(exact == null, stamped == null, "edge presence: $context, exact=$exact stamped=$stamped")
		assertEquals(exact.comparable(), stamped.comparable(), context)
		val exactArc = exact.bounce?.arc
		val stampedArc = stamped.bounce?.arc
		if (exactArc != null && stampedArc != null) {
			assertTrue(
				exactArc.heights.contentEquals(stampedArc.heights) && exactArc.distances.contentEquals(stampedArc.distances) &&
					exactArc.exitSpeed == stampedArc.exitSpeed,
				"bounce arc: $context",
			)
		}
	}

	private fun CoarseEdge.comparable() = copy(readSet = emptySet(), bounce = bounce?.copy(arc = NO_ARC))

	private fun specialWorld(random: Random): SnapshotSimulationEnvironment {
		val slab = SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.5, 1.0))
		val fence = SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.375, 0.0, 0.375, 0.625, 1.5, 0.625), fenceLike = true)
		val carpet = SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.0625, 1.0))
		val slime = SnapshotBlockPhysics.of(VoxelShapes.fullCube(), bounceFactor = 1.0, dampensSteppingSpeed = true)
		val ladder = SnapshotBlockPhysics(VoxelShapes.empty(), coarseVoxel = CoarseVoxel.CLIMBABLE)
		val phantom = SnapshotBlockPhysics(VoxelShapes.empty(), coarseVoxel = CoarseVoxel.FULL_BLOCK)
		val water = SnapshotBlockPhysics.water()
		val deepWater = SnapshotBlockPhysics.water(1.0f)
		val lava = SnapshotBlockPhysics(VoxelShapes.empty(), unsupportedPhysics = UnsupportedPhysics(UnsupportedPhysicsKind.FLUID))
		val mud = SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.875, 1.0))
		val bounds = SimulationSnapshotBounds(-16, 48, -16, 63, 95, 63)
		val builders = HashMap<Long, ImmutableSnapshotSection.Builder>()
		fun set(x: Int, y: Int, z: Int, physics: SnapshotBlockPhysics) =
			builders.getOrPut(ChunkSectionPos.toLong(BlockPos(x, y, z))) { ImmutableSnapshotSection.Builder() }.set(x, y, z, physics)
		for (x in -4..51) for (z in -4..51) {
			if (random.nextInt(9) == 0) continue
			val top = 62 + random.nextInt(4)
			for (y in 58..top) set(x, y, z, SnapshotBlockPhysics.FULL_CUBE)
			when (random.nextInt(40)) {
				0, 1 -> set(x, top + 1, z, slab)
				2 -> set(x, top + 1, z, fence)
				3 -> set(x, top + 1, z, carpet)
				4 -> set(x, top, z, slime)
				5 -> for (y in top + 1..top + 3) set(x, y, z, ladder)
				11 -> set(x, top, z, water)
				12 -> set(x, top, z, lava)
				13 -> {
					set(x, top + 1, z, deepWater)
					set(x, top + 2, z, water)
				}
				19, 20, 21 -> {
					for (y in top - 2 until top) set(x, y, z, deepWater)
					set(x, top, z, water)
				}
				10 -> {
					set(x, top + 1, z, ladder)
					set(x, top + 2, z, SnapshotBlockPhysics.FULL_CUBE)
				}
				6 -> set(x, top + 1, z, phantom)
				7, 8, 9 -> for (y in top + 1..top + 2) set(x, y, z, SnapshotBlockPhysics.FULL_CUBE)
				14, 15, 16, 17 -> set(x, top, z, mud)
				18 -> set(x, top + 2 + random.nextInt(2), z, mud)
			}
		}
		for (sectionX in -1..3) for (sectionY in 3..5) for (sectionZ in -1..3) {
			builders.getOrPut(ChunkSectionPos.asLong(sectionX, sectionY, sectionZ)) { ImmutableSnapshotSection.Builder() }
		}
		val missing = ChunkSectionPos.asLong(1 + random.nextInt(2), 3 + random.nextInt(2), 1 + random.nextInt(2))
		return streamedSnapshot(bounds, builders.filterKeys { it != missing }.mapValues { it.value.build() })
	}

	private companion object {
		const val READ_SET_SAMPLE = 97

		val NO_ARC = ArcSample(DoubleArray(0), DoubleArray(0), 0.0)

		val FIELD_OPTIONS = SimpleMoveOptions(
			maxJumpDrop = 2, maxJumpSpan = 3, allowClimbing = true, allowSlimeBounces = true, maxBounceDrop = 5, allowDeepDropJumps = true,
		)
	}
}
