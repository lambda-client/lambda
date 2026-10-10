package com.lambda.pathing.world.store

import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.UnsupportedPhysics
import com.lambda.pathing.physics.UnsupportedPhysicsKind
import com.lambda.pathing.physics.WaterPhysics
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import net.minecraft.client.MinecraftClient
import net.minecraft.util.WorldSavePath
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.World
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream

internal class BaritoneCache(private val directory: Path, private val minY: Int, height: Int) {
	private val sectionCount = height shr 4
	private val chunkBytes = height * CHUNK_COLUMN_BYTES_PER_LAYER
	private val regions = object : LinkedHashMap<Long, Array<RememberedChunk?>?>(16, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Array<RememberedChunk?>?>) = size > CACHED_REGIONS
	}
	private val sections = object : LinkedHashMap<Long, ImmutableSnapshotSection>(256, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ImmutableSnapshotSection>) = size > CACHED_SECTIONS
	}

	fun sectionsExcept(endpoints: Collection<Stance>): (Long) -> ImmutableSnapshotSection? {
		val unknown = endpoints.flatMapTo(HashSet()) { stance ->
			(stance.y - 1..stance.y + 1).map { y -> ChunkSectionPos.asLong(stance.x shr 4, y shr 4, stance.z shr 4) }
		}
		return { key -> if (key in unknown) null else section(key) }
	}

	@Synchronized
	fun section(sectionKey: Long): ImmutableSnapshotSection? {
		val index = ChunkSectionPos.unpackY(sectionKey) - (minY shr 4)
		if (index !in 0 until sectionCount) return null
		val chunk = chunk(ChunkSectionPos.unpackX(sectionKey), ChunkSectionPos.unpackZ(sectionKey)) ?: return null
		val words = chunk.mixed[index] ?: return UNIFORM[chunk.uniform[index].toInt()]
		return sections.getOrPut(sectionKey) {
			val builder = ImmutableSnapshotSection.Builder()
			for (y in 0..15) for (z in 0..15) for (x in 0..15) {
				val bit = (x shl 1) or (z shl 5) or (y shl 9)
				builder.set(x, y, z, PHYSICS[((words[bit ushr 6] ushr (bit and 63)) and 3L).toInt()])
			}
			builder.build()
		}
	}

	private fun chunk(chunkX: Int, chunkZ: Int): RememberedChunk? {
		val regionKey = ChunkSectionPos.asLong(chunkX shr 5, 0, chunkZ shr 5)
		val region = if (regions.containsKey(regionKey)) regions[regionKey] else read(chunkX shr 5, chunkZ shr 5).also { regions[regionKey] = it }
		return region?.get(((chunkX and 31) shl 5) or (chunkZ and 31))
	}

	private fun read(regionX: Int, regionZ: Int): Array<RememberedChunk?>? {
		val path = directory.resolve("r.$regionX.$regionZ.bcr")
		if (!Files.isRegularFile(path)) return null
		return runCatching {
			DataInputStream(GZIPInputStream(Files.newInputStream(path), READ_BUFFER_BYTES)).use { input ->
				if (input.readInt() != MAGIC) return null
				val bytes = ByteArray(chunkBytes)
				Array(REGION_CHUNKS) {
					when (input.read()) {
						CHUNK_PRESENT -> {
							input.readFully(bytes)
							RememberedChunk.of(bytes, sectionCount)
						}
						CHUNK_ABSENT -> null
						else -> return null
					}
				}
			}
		}.getOrNull()
	}

	private class RememberedChunk(val uniform: ByteArray, val mixed: Array<LongArray?>) {
		companion object {
			fun of(bytes: ByteArray, sectionCount: Int): RememberedChunk {
				val longs = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asLongBuffer()
				val uniform = ByteArray(sectionCount)
				val mixed = arrayOfNulls<LongArray>(sectionCount)
				for (section in 0 until sectionCount) {
					val words = LongArray(SECTION_WORDS) { longs.get(section * SECTION_WORDS + it) }
					val type = words[0] and 3L
					if (words.all { it == type * UNIFORM_PATTERN }) uniform[section] = type.toByte() else mixed[section] = words
				}
				return RememberedChunk(uniform, mixed)
			}
		}
	}

	companion object {
		private const val MAGIC = 456022911
		private const val CHUNK_ABSENT = 0
		private const val CHUNK_PRESENT = 1
		private const val REGION_CHUNKS = 32 * 32
		private const val CHUNK_COLUMN_BYTES_PER_LAYER = 16 * 16 * 2 / 8
		private const val SECTION_WORDS = 16 * 16 * 16 * 2 / 64
		private const val UNIFORM_PATTERN = 0x5555555555555555L
		private const val READ_BUFFER_BYTES = 32768
		private const val CACHED_REGIONS = 8
		private const val CACHED_SECTIONS = 4096

		private val PHYSICS = arrayOf(
			SnapshotBlockPhysics.AIR,
			SnapshotBlockPhysics(VoxelShapes.empty(), unsupportedPhysics = UnsupportedPhysics(UnsupportedPhysicsKind.FLUID, "baritone:avoid")),
			SnapshotBlockPhysics(VoxelShapes.empty(), coarseVoxel = CoarseVoxel.WATER, water = WaterPhysics(SURFACE_WATER_HEIGHT, Vec3d.ZERO)),
			SnapshotBlockPhysics.FULL_CUBE,
		)

		private val UNIFORM = Array(PHYSICS.size) { type -> ImmutableSnapshotSection.Builder().apply { fill(PHYSICS[type]) }.build() }

		private const val SURFACE_WATER_HEIGHT = 8f / 9f

		fun of(world: World): BaritoneCache? {
			val client = MinecraftClient.getInstance()
			val root = client.server?.getSavePath(WorldSavePath.ROOT)?.normalize()?.resolve("baritone")
				?: client.currentServerEntry?.let { server -> client.runDirectory.toPath().resolve("baritone").resolve(if (server.isRealm) "realms" else server.address) }
				?: return null
			val dimension = world.registryKey.value
			val directory = root.resolve(dimension.namespace).resolve("${dimension.path}_${world.dimension.logicalHeight}").resolve("cache")
			if (!Files.isDirectory(directory)) return null
			return BaritoneCache(directory, world.dimension.minY, world.dimension.height)
		}
	}
}
