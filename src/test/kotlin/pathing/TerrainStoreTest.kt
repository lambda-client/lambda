package pathing

import com.lambda.pathing.world.store.TerrainStore
import minecraft.MinecraftBootstrap
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.world.chunk.PaletteProvider
import net.minecraft.world.chunk.PalettedContainer
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The terrain store keeps the block states of captured sections in vanilla's palette encoding,
 * one version per physics change, and only the versions a dump may still need: a version
 * superseded before any dump was written is replaced, never accumulated.
 */
class TerrainStoreTest {
	@Test
	fun `a recaptured section that did not change keeps its version`() {
		val directory = Files.createTempDirectory("terrain-store")
		val store = TerrainStore(directory)
		store.record(SECTION, section(Blocks.STONE.defaultState), T0)
		store.record(SECTION, section(Blocks.STONE.defaultState), T0 + 10)
		store.reference(T0 + 20)
		store.close()
		assertEquals(Blocks.STONE.defaultState, stateAt(directory, T0))
	}

	@Test
	fun `a version nothing referenced is replaced and a referenced one is kept`() {
		val directory = Files.createTempDirectory("terrain-store")
		val store = TerrainStore(directory)
		store.record(SECTION, section(Blocks.STONE.defaultState), T0)
		store.record(SECTION, section(Blocks.DIRT.defaultState), T0 + 10)
		store.reference(T0 + 20)
		assertNull(stateAt(directory, T0), "the stone version was superseded before any dump")
		assertEquals(Blocks.DIRT.defaultState, stateAt(directory, T0 + 10))

		store.record(SECTION, section(Blocks.OAK_PLANKS.defaultState), T0 + 30)
		store.reference(T0 + 40)
		store.close()
		assertEquals(Blocks.DIRT.defaultState, stateAt(directory, T0 + 25), "a dump at T0 + 20 still replays dirt")
		assertEquals(Blocks.OAK_PLANKS.defaultState, stateAt(directory, T0 + 40))
	}

	@Test
	fun `a captured section costs a few hundred bytes`() {
		val directory = Files.createTempDirectory("terrain-store")
		val store = TerrainStore(directory)
		val container = section(Blocks.STONE.defaultState)
		for (x in 0..15) for (z in 0..15) {
			container.set(x, 8, z, if ((x + z) % 3 == 0) Blocks.GRASS_BLOCK.defaultState else Blocks.DIRT.defaultState)
			for (y in 9..15) container.set(x, y, z, Blocks.AIR.defaultState)
		}
		container.set(3, 9, 4, Blocks.OAK_LOG.defaultState)
		store.record(SECTION, container, T0)
		store.reference(T0)
		store.close()
		val bytes = Files.list(directory).use { files -> files.mapToLong(Files::size).sum() }
		assertTrue(bytes < 600, "one mixed section took $bytes bytes")
		val loaded = TerrainStore.load(directory, longArrayOf(SECTION), T0).getValue(SECTION)
		for (y in 0..15) for (z in 0..15) for (x in 0..15) assertEquals(container.get(x, y, z), loaded.get(x, y, z), "state at ($x, $y, $z)")
	}

	private fun stateAt(directory: Path, at: Long): BlockState? = TerrainStore.load(directory, longArrayOf(SECTION), at)[SECTION]?.get(0, 0, 0)

	private fun section(fill: BlockState): PalettedContainer<BlockState> {
		MinecraftBootstrap.ensure()
		return PalettedContainer(Blocks.AIR.defaultState, PaletteProvider.forBlockStates(Block.STATE_IDS)).apply {
			for (y in 0..15) for (z in 0..15) for (x in 0..15) set(x, y, z, fill)
		}
	}

	private companion object {
		val SECTION = ChunkSectionPos.asLong(12, 4, -7)
		val T0 = System.currentTimeMillis() + 60_000
	}
}
