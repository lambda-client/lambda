package pathing

import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.CellClass
import com.lambda.pathing.world.Medium
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import com.lambda.pathing.world.store.BaritoneCache
import com.lambda.pathing.world.streaming.WorldEventBatch
import net.minecraft.util.math.ChunkSectionPos
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.BitSet
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Baritone's region cache, read as it writes it: 2 bits per block (air, water, avoid, solid)
 * for every chunk it ever loaded. The coarse planner sees that remembered terrain beyond the
 * captured world, and the exact capture of a chunk replaces what was remembered of it, in
 * either direction.
 */
class BaritoneCacheTest {
	@Test
	fun `a region file decodes to the blocks Baritone recorded`() {
		val directory = cacheOf { x, y, z ->
			when {
				x == 3 && y == 64 && z == 5 -> WATER
				x == 4 && y == 64 && z == 5 -> AVOID
				y <= 63 -> SOLID
				else -> AIR
			}
		}
		val cache = BaritoneCache(directory, MIN_Y, HEIGHT)
		assertNull(cache.section(ChunkSectionPos.asLong(40, 4, 0)), "a chunk outside every region file is not remembered")
		val surface = assertNotNull(cache.section(ChunkSectionPos.asLong(0, 4, 0)))
		assertEquals(Medium.WATER, surface[3, 64, 5].coarseVoxel.medium)
		assertEquals(CellClass.HAZARD, surface[4, 64, 5].cellClass)
		assertEquals(SnapshotBlockPhysics.AIR, surface[5, 64, 5])
		assertEquals(SnapshotBlockPhysics.FULL_CUBE, assertNotNull(cache.section(ChunkSectionPos.asLong(0, 3, 0)))[7, 63, 7])
		assertNull(cache.section(ChunkSectionPos.asLong(0, 24, 0)), "above the dimension nothing is remembered")
	}

	@Test
	fun `a remembered wall beyond the captured world bends the coarse route`() {
		val directory = cacheOf { x, y, z -> if (y <= 63 || x in 60..61 && z < GAP_Z && y <= 70) SOLID else AIR }
		val captured = SnapshotSimulationEnvironment(BOUNDS, floorSections(0..0, 2..2), defaultBlock = null)
		val start = Stance(8, 64, 40)
		val goal = Stance(120, 64, 40)

		val straight = assertNotNull(CoarsePlanningState(captured, SimpleMoveOptions(), start, goal).routePlan(0L, { false }, ROUTE_EXPANSIONS)).nodes
		assertTrue(straight.none { it.z >= GAP_Z }, "without memory the route heads straight for the goal: $straight")

		val cache = BaritoneCache(directory, MIN_Y, HEIGHT)
		val memory = captured.remembering(cache::section) { _, _ -> false }
		val remembering = CoarsePlanningState(memory, SimpleMoveOptions(), start, goal, memory = memory)
		val around = assertNotNull(remembering.routePlan(0L, { false }, ROUTE_EXPANSIONS)).nodes
		assertTrue(around.any { it.x in 55..66 && it.z >= GAP_Z }, "the route must go around the remembered wall: $around")
		assertEquals(goal, around.last())
	}

	@Test
	fun `a capture over remembered terrain corrects the route`() {
		val directory = cacheOf { _, y, _ -> if (y <= 63) SOLID else AIR }
		val captured = SnapshotSimulationEnvironment(BOUNDS, floorSections(0..0, 2..2), defaultBlock = null)
		val cache = BaritoneCache(directory, MIN_Y, HEIGHT)
		val start = Stance(8, 64, 40)
		val goal = Stance(120, 64, 40)
		val memory = captured.remembering(cache::section) { _, _ -> false }
		val state = CoarsePlanningState(memory, SimpleMoveOptions(), start, goal, memory = memory)
		assertTrue(assertNotNull(state.routePlan(0L, { false }, ROUTE_EXPANSIONS)).nodes.none { it.z >= CAPTURED_GAP_Z }, "remembered open ground carries a straight route")

		val wall = HashMap<Long, ImmutableSnapshotSection>()
		val arrivals = HashSet<PathingSection>()
		for (chunkZ in 0 until (CAPTURED_GAP_Z shr 4)) for (sectionY in 3..4) {
			val builder = ImmutableSnapshotSection.Builder()
			for (x in 48..63) for (z in 0..15) for (y in 0..15) {
				val worldY = (sectionY shl 4) + y
				builder.set(x, worldY, z, if (worldY <= 63 || x in 60..61 && worldY <= 70) SnapshotBlockPhysics.FULL_CUBE else SnapshotBlockPhysics.AIR)
			}
			wall[ChunkSectionPos.asLong(3, sectionY, chunkZ)] = builder.build()
			arrivals += PathingSection(3, sectionY, chunkZ)
		}
		wall.forEach(captured::install)
		state.applyEvents(WorldEventBatch(revision = 1, sections = arrivals, chunks = emptySet(), mutations = emptySet()))
		val route = assertNotNull(state.routePlan(1L, { false }, ROUTE_EXPANSIONS)).nodes
		assertTrue(route.any { it.x in 55..66 && it.z >= CAPTURED_GAP_Z }, "the captured wall replaces the remembered open ground: $route")
	}

	@Test
	fun `a remembered world without a route is forgotten rather than trusted`() {
		val directory = cacheOf { _, _, _ -> AIR }
		val captured = SnapshotSimulationEnvironment(BOUNDS, floorSections(3..3, 3..3), defaultBlock = null)
		val cache = BaritoneCache(directory, MIN_Y, HEIGHT)
		val start = Stance(56, 64, 56)
		val goal = Stance(120, 64, 120)
		val memory = captured.remembering(cache.sectionsExcept(listOf(goal))) { _, _ -> false }
		val state = CoarsePlanningState(memory, SimpleMoveOptions(), start, goal, memory = memory)
		assertNull(state.routePlan(0L, { false }, ROUTE_EXPANSIONS), "a stale void remembered around the start leaves no route")
		assertTrue(state.forgetMemory())
		val route = assertNotNull(state.routePlan(0L, { false }, ROUTE_EXPANSIONS), "without the memory the unknown world is open again")
		assertTrue(route.nodes.size > 1, "the route must leave the start: ${route.nodes}")
		assertFalse(state.forgetMemory(), "memory is forgotten once")
	}

	private fun floorSections(chunksX: IntRange, chunksZ: IntRange): Map<Long, ImmutableSnapshotSection> {
		val sections = HashMap<Long, ImmutableSnapshotSection>()
		for (chunkX in chunksX) for (chunkZ in chunksZ) {
			sections[ChunkSectionPos.asLong(chunkX, 3, chunkZ)] = ImmutableSnapshotSection.Builder().apply { fill(SnapshotBlockPhysics.FULL_CUBE) }.build()
			sections[ChunkSectionPos.asLong(chunkX, 4, chunkZ)] = ImmutableSnapshotSection.Builder().apply { fill(SnapshotBlockPhysics.AIR) }.build()
		}
		return sections
	}

	/** One region, r.0.0, holding chunks 0..7 x 0..7 in Baritone's on-disk layout. */
	private fun cacheOf(type: (x: Int, y: Int, z: Int) -> Int): Path {
		val directory = Files.createTempDirectory("baritone-cache")
		DataOutputStream(GZIPOutputStream(Files.newOutputStream(directory.resolve("r.0.0.bcr")))).use { out ->
			out.writeInt(MAGIC)
			val present = ArrayList<Pair<Int, Int>>()
			for (chunkX in 0 until 32) for (chunkZ in 0 until 32) {
				if (chunkX >= 8 || chunkZ >= 8) {
					out.write(0)
					continue
				}
				out.write(1)
				present += chunkX to chunkZ
				val bits = BitSet(2 * 16 * 16 * HEIGHT)
				for (yIndex in 0 until HEIGHT) for (z in 0..15) for (x in 0..15) {
					val code = type((chunkX shl 4) + x, yIndex + MIN_Y, (chunkZ shl 4) + z)
					val index = (x shl 1) or (z shl 5) or (yIndex shl 9)
					bits.set(index, code and 0b10 != 0)
					bits.set(index + 1, code and 0b01 != 0)
				}
				out.write(bits.toByteArray().copyOf(2 * 16 * 16 * HEIGHT / 8))
			}
			for (chunk in present) repeat(256) { out.writeUTF("minecraft:stone") }
			for (chunk in present) out.writeShort(0)
			for (chunk in present) out.writeLong(0L)
		}
		return directory
	}

	private companion object {
		const val MAGIC = 456022911
		const val MIN_Y = -64
		const val HEIGHT = 384
		const val AIR = 0b00
		const val WATER = 0b01
		const val AVOID = 0b10
		const val SOLID = 0b11
		const val GAP_Z = 91
		const val CAPTURED_GAP_Z = 80
		const val ROUTE_EXPANSIONS = 1_000_000
		val BOUNDS = SimulationSnapshotBounds(-1024, MIN_Y, -1024, 1024, MIN_Y + HEIGHT - 1, 1024)
	}
}
