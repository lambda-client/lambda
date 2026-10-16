package pathing

import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarsePlanningState
import com.lambda.pathing.core.Stance
import com.lambda.pathing.find.AccessStances
import com.lambda.pathing.find.BlockQuery
import com.lambda.pathing.find.BlockSource
import com.lambda.pathing.find.CachedBlocks
import com.lambda.pathing.find.NearestBlocks
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import com.lambda.pathing.session.CoarseService
import com.lambda.pathing.session.InterestPrimer
import com.lambda.pathing.world.store.BaritoneCache
import com.lambda.pathing.world.streaming.PathingWorld
import com.lambda.pathing.world.store.TerrainStore
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import minecraft.MinecraftBootstrap
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.StairsBlock
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.argument.BlockPredicateArgumentType
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.Registries
import net.minecraft.resource.featuretoggle.FeatureFlags
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.chunk.PaletteProvider
import net.minecraft.world.chunk.PalettedContainer
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.BitSet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `path find` looks for the nearest block of a type in what the client has loaded, in Lambda's terrain cache (the
 * block states of every captured section) and in Baritone's cache (the exact spots of the blocks it tracks and the
 * top block of every column), and walks to a stance beside it that the planner accepts.
 */
class BlockFinderTest {
	@Test
	fun `a block filter takes ids, tags and states as the command parses them`() {
		val beds = query("#beds")
		assertTrue(beds.matches(Blocks.RED_BED.defaultState))
		assertFalse(beds.matches(Blocks.STONE.defaultState))
		assertTrue(beds.mayMatchName("red_bed"), "Baritone writes vanilla names without their namespace")
		assertTrue(beds.mayMatchName("minecraft:white_bed"))
		assertFalse(beds.mayMatchName("chest"))

		val north = query("oak_stairs[facing=north]")
		assertTrue(north.matches(Blocks.OAK_STAIRS.defaultState.with(StairsBlock.FACING, Direction.NORTH)))
		assertFalse(north.matches(Blocks.OAK_STAIRS.defaultState.with(StairsBlock.FACING, Direction.SOUTH)))
		assertTrue(north.mayMatch(Blocks.OAK_STAIRS), "some state of the block matches")
	}

	@Test
	fun `the game's registries suggest block ids and tags and resolve tags`() {
		MinecraftBootstrap.ensure()
		val access = CommandRegistryAccess.of(DynamicRegistryManager.of(Registries.REGISTRIES), FeatureFlags.FEATURE_MANAGER.featureSet)
		val argument = BlockPredicateArgumentType.blockPredicate(access)
		val ids = argument.listSuggestions<Any>(null, SuggestionsBuilder("che", 0)).join().list.map { it.text }
		assertTrue("minecraft:chest" in ids, "block ids complete: $ids")
		val tags = argument.listSuggestions<Any>(null, SuggestionsBuilder("#be", 0)).join().list.map { it.text }
		assertTrue("#minecraft:beds" in tags, "tags complete: $tags")
		val beds = BlockQuery("#beds", null, argument.parse(StringReader("#beds")))
		assertTrue(beds.matches(Blocks.CYAN_BED.defaultState))
	}

	@Test
	fun `the coarse planner heads for the alternative goal that is nearest by path`() {
		val sections = HashMap<Long, ImmutableSnapshotSection>()
		for (chunkX in 0..2) for (chunkZ in 0..2) for (sectionY in 3..4) {
			val builder = ImmutableSnapshotSection.Builder()
			for (y in 0..15) for (z in 0..15) for (x in 0..15) {
				val worldX = (chunkX shl 4) + x
				val worldY = (sectionY shl 4) + y
				val worldZ = (chunkZ shl 4) + z
				val ring = worldX in 16..24 && worldZ in 20..28 && (worldX == 16 || worldX == 24 || worldZ == 20 || worldZ == 28) && !(worldX == 24 && worldZ == 24)
				builder.set(worldX, worldY, worldZ, if (worldY <= 63 || ring && worldY <= 66) SnapshotBlockPhysics.FULL_CUBE else SnapshotBlockPhysics.AIR)
			}
			sections[ChunkSectionPos.asLong(chunkX, sectionY, chunkZ)] = builder.build()
		}
		val view = SnapshotSimulationEnvironment(BOUNDS, sections, defaultBlock = null)
		val start = Stance(8, 64, 24)
		val walled = Stance(20, 64, 24)
		val open = Stance(8, 64, 42)
		val moves = ProbeScenarios.moveLibrary()
		val alone = CoarsePlanner(view, moves, start, walled).also { it.repair() }
		assertEquals(walled, assertNotNull(alone.routePlan(0L)).goal, "without alternatives the route goes round the ring")
		val either = CoarsePlanner(view, moves, start, walled, listOf(open)).also { it.repair() }
		val route = assertNotNull(either.routePlan(0L))
		assertEquals(open, route.goal, "the open goal is nearer by path than the walled one: ${route.nodes}")
		assertTrue(route.lowerBoundTicks < assertNotNull(alone.routePlan(0L)).lowerBoundTicks)
	}

	@Test
	fun `the planning probe settles on the target nearest by path`() {
		val source = FakeCaptureSource(groundY = 64).apply {
			loadSquare(4)
			for (x in 16..24) for (z in 20..28) {
				if ((x == 16 || x == 24 || z == 20 || z == 28) && !(x == 24 && z == 24)) for (y in 64..66) set(x, y, z, SnapshotBlockPhysics.FULL_CUBE)
			}
		}
		val world = PathingWorld(BOUNDS, source)
		val running = AtomicBoolean(true)
		val start = Stance(8, 64, 24)
		val walled = Stance(20, 64, 24)
		val open = Stance(8, 64, 42)
		val client = thread(name = "fake-client", isDaemon = true) {
			while (running.get()) {
				InterestPrimer.primeBody(world, BlockPos(start.x, start.y, start.z))
				world.advance(15.0)
				Thread.sleep(10)
			}
		}
		val coarse = CoarseService(world, listOf(CoarsePlanningState(world.snapshot, SimpleMoveOptions(), start, walled, alternatives = listOf(open))), listOf(start), expansionBudget = 200_000)
		try {
			InterestPrimer.primeJourney(world, start, walled)
			InterestPrimer.primeJourney(world, start, open)
			coarse.start()
			val route = assertNotNull(coarse.awaitRoute(0, start, cancelled = { false }) { it == walled || it == open }).route
			assertEquals(open, route.goal, "the probe must settle on the open target: ${route.nodes}")
		} finally {
			coarse.close()
			running.set(false)
			client.join()
		}
	}

	@Test
	fun `the nearest stored block is found and the stance beside it faces the player`() {
		val store = storeOf { x, y, z ->
			when {
				y <= 63 -> Blocks.STONE.defaultState
				x == 5 && y == 64 && z == 5 || x == 12 && y == 64 && z == 12 -> Blocks.CHEST.defaultState
				else -> Blocks.AIR.defaultState
			}
		}
		val (found, stance) = assertNotNull(search(query("chest"), store = store))
		assertEquals(BlockPos(5, 64, 5), found)
		assertEquals(Stance(4, 64, 5), stance, "the nearer of the free sides beside the chest")
	}

	@Test
	fun `a block in a wall is reached from beside it at head height`() {
		val store = storeOf { x, y, z ->
			when {
				y <= 63 || x >= 5 && y <= 66 -> if (x == 5 && y == 65 && z == 5) Blocks.DIAMOND_ORE.defaultState else Blocks.STONE.defaultState
				else -> Blocks.AIR.defaultState
			}
		}
		val (found, stance) = assertNotNull(search(query("diamond_ore"), store = store))
		assertEquals(BlockPos(5, 65, 5), found)
		assertEquals(Stance(4, 64, 5), stance, "the ore sits at head height beside the stance")
	}

	@Test
	fun `a block in the floor is stood on`() {
		val store = storeOf { x, y, z ->
			when {
				x == 3 && y == 63 && z == 3 -> Blocks.GOLD_BLOCK.defaultState
				y <= 63 -> Blocks.STONE.defaultState
				else -> Blocks.AIR.defaultState
			}
		}
		val (found, stance) = assertNotNull(search(query("gold_block"), store = store))
		assertEquals(BlockPos(3, 63, 3), found)
		assertEquals(Stance(3, 64, 3), stance)
	}

	@Test
	fun `a buried block with nowhere to stand gives way to the next one`() {
		val store = storeOf { x, y, z ->
			when {
				x == 2 && y == 60 && z == 2 || x == 10 && y == 64 && z == 10 -> Blocks.EMERALD_ORE.defaultState
				y <= 63 -> Blocks.STONE.defaultState
				else -> Blocks.AIR.defaultState
			}
		}
		val (found, _) = assertNotNull(search(query("emerald_ore"), store = store, origin = Vec3d(2.5, 64.0, 2.5)))
		assertEquals(BlockPos(10, 64, 10), found, "the nearer ore sealed in stone has no stance")
	}

	@Test
	fun `Baritone's tracked blocks and column tops are found where the store knows nothing`() {
		val baritone = baritoneOf(
			type = { x, y, z -> if (y <= 63 || x == 3 && y == 64 && z == 3 || x == 9 && y == 64 && z == 9) SOLID else AIR },
			overview = { x, z -> if (x == 9 && z == 9) "diamond_block" else "stone" },
			tracked = mapOf("chest" to listOf(BlockPos(3, 64, 3))),
		)
		val chest = assertNotNull(search(query("chest"), baritone = baritone))
		assertEquals(BlockPos(3, 64, 3), chest.first)
		assertEquals(Stance(2, 64, 3), chest.second)
		val top = assertNotNull(search(query("diamond_block"), baritone = baritone))
		assertEquals(BlockPos(9, 64, 9), top.first, "the column top sits on the highest block Baritone recorded")
	}

	@Test
	fun `a stored section overrules Baritone and a loaded chunk overrules both`() {
		val store = storeOf { _, y, _ -> if (y <= 63) Blocks.STONE.defaultState else Blocks.AIR.defaultState }
		val baritone = baritoneOf(
			type = { x, y, z -> if (y <= 63 || x == 3 && y == 64 && z == 3) SOLID else AIR },
			overview = { _, _ -> "stone" },
			tracked = mapOf("chest" to listOf(BlockPos(3, 64, 3))),
		)
		assertNull(search(query("chest"), store = store, baritone = baritone), "the store recorded the section after the chest was gone")
		val loaded = LongOpenHashSet().apply { for (chunkX in 0..1) for (chunkZ in 0..1) add(ChunkPos.toLong(chunkX, chunkZ)) }
		assertNull(search(query("stone"), store = store, loaded = loaded), "a loaded chunk is searched live, never from the caches")
	}

	private fun search(
		query: BlockQuery,
		store: Path? = null,
		baritone: BaritoneCache? = null,
		loaded: LongOpenHashSet = LongOpenHashSet(),
		origin: Vec3d = Vec3d(0.5, 64.0, 0.5),
	): Pair<BlockPos, Stance>? {
		val blocks = CachedBlocks(store, baritone, loaded, MIN_Y, MIN_Y + HEIGHT - 1)
		val nearest = NearestBlocks(origin, 8, emptySet())
		blocks.scan(query, nearest) { false }
		for (found in nearest.sorted()) {
			val view = AccessStances.view(found.pos, MIN_Y, MIN_Y + HEIGHT - 1) { blocks.section(it, ignoreHazards = false) }
			val stance = AccessStances.choose(view, found.pos, origin) ?: continue
			assertEquals(if (baritone == null) BlockSource.STORE else BlockSource.BARITONE, found.source)
			return found.pos to stance
		}
		return null
	}

	private fun query(filter: String): BlockQuery {
		MinecraftBootstrap.ensure()
		return BlockQuery(filter, null, BlockPredicateArgumentType.parse(Registries.BLOCK, StringReader(filter)))
	}

	/** Chunks 0..1 x 0..1, sections y 32..95, as the terrain recorder stores them. */
	private fun storeOf(state: (x: Int, y: Int, z: Int) -> BlockState): Path {
		MinecraftBootstrap.ensure()
		val directory = Files.createTempDirectory("terrain-store")
		val store = TerrainStore(directory)
		for (chunkX in 0..1) for (chunkZ in 0..1) for (sectionY in 2..5) {
			val container = PalettedContainer(Blocks.AIR.defaultState, PaletteProvider.forBlockStates(Block.STATE_IDS))
			for (y in 0..15) for (z in 0..15) for (x in 0..15) container.set(x, y, z, state((chunkX shl 4) + x, (sectionY shl 4) + y, (chunkZ shl 4) + z))
			store.record(ChunkSectionPos.asLong(chunkX, sectionY, chunkZ), container, 1L)
		}
		store.sync()
		store.close()
		return directory
	}

	/** One region, r.0.0, holding chunks 0..1 x 0..1 in Baritone's on-disk layout. */
	private fun baritoneOf(type: (x: Int, y: Int, z: Int) -> Int, overview: (x: Int, z: Int) -> String, tracked: Map<String, List<BlockPos>>): BaritoneCache {
		MinecraftBootstrap.ensure()
		val directory = Files.createTempDirectory("baritone-cache")
		DataOutputStream(GZIPOutputStream(Files.newOutputStream(directory.resolve("r.0.0.bcr")))).use { out ->
			out.writeInt(MAGIC)
			val present = ArrayList<Pair<Int, Int>>()
			for (chunkX in 0 until 32) for (chunkZ in 0 until 32) {
				if (chunkX >= 2 || chunkZ >= 2) {
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
			for ((chunkX, chunkZ) in present) for (z in 0..15) for (x in 0..15) out.writeUTF(overview((chunkX shl 4) + x, (chunkZ shl 4) + z))
			for ((chunkX, chunkZ) in present) {
				val here = tracked.mapValues { (_, positions) -> positions.filter { it.x shr 4 == chunkX && it.z shr 4 == chunkZ } }.filterValues { it.isNotEmpty() }
				out.writeShort(here.size)
				for ((name, positions) in here) {
					out.writeUTF(name)
					out.writeShort(positions.size)
					for (pos in positions) {
						out.writeByte(((pos.z and 15) shl 4) or (pos.x and 15))
						out.writeInt(pos.y - MIN_Y)
					}
				}
			}
			for (chunk in present) out.writeLong(1L)
		}
		return BaritoneCache(directory, MIN_Y, HEIGHT)
	}

	private companion object {
		const val MAGIC = 456022911
		const val MIN_Y = -64
		const val HEIGHT = 384
		const val AIR = 0b00
		const val SOLID = 0b11
		val BOUNDS = SimulationSnapshotBounds(-1024, MIN_Y, -1024, 1024, MIN_Y + HEIGHT - 1, 1024)
	}
}
