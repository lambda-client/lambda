package com.lambda.pathing.world.streaming

import com.lambda.Lambda
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.world.snapshot.BlockPhysicsTable
import com.lambda.pathing.world.snapshot.HazardRules
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.world.chunk.ChunkSection
import org.apache.logging.log4j.LogManager
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.abs

private val LOG = LogManager.getLogger(Lambda.SYMBOL)

class PathingWorld internal constructor(
	val bounds: SimulationSnapshotBounds,
	private val source: TerrainSource,
	hazards: HazardRules,
	private val recorder: SectionRecorder? = null,
) {
	private val revisions = RevisionLog(ReentrantLock())
	private val sections = LiveSections(source, BlockPhysicsTable.of(hazards), bounds) { key -> recorder?.record(key) }
	private val minSectionY = bounds.minY shr 4
	private val maxSectionY = bounds.maxY shr 4

	private val reloads = AtomicLong()
	private val changedReloads = AtomicLong()
	private val forgotten = AtomicLong()
	private val cellChanges = AtomicLong()
	private val misses = AtomicLong()

	val snapshot: SnapshotSimulationEnvironment = SnapshotSimulationEnvironment.live(bounds, sections) { misses.incrementAndGet() }

	val terrainSnapshot: SnapshotSimulationEnvironment = snapshot.ignoringEntities()

	val revision: Long get() = revisions.revision

	val sectionsDerived: Int get() = sections.size

	val chunkReloads: Long get() = reloads.get()

	val reloadsChanged: Long get() = changedReloads.get()

	val chunksForgotten: Long get() = forgotten.get()

	val blockChanges: Long get() = cellChanges.get()

	val exactMisses: Long get() = misses.get()

	fun terrainLedger(): String = "%d sections derived in %d ms (%d failed reads)".format(sections.size, sections.deriveMillis, sections.failures)

	internal fun isDerived(sectionKey: Long): Boolean = sections.isDerived(sectionKey)

	fun readable(chunkX: Int, chunkZ: Int): Boolean = source.isReadable(chunkX, chunkZ)

	fun loadable(chunkX: Int, chunkZ: Int): Boolean = source.isReadable(chunkX, chunkZ) || source.mayLoad(chunkX, chunkZ)

	@Volatile
	var obstacleCells: LongArray = TerrainSource.NO_CELLS
		private set

	fun advance() {
		check(source.isOnClientThread()) { "PathingWorld must advance on the client thread" }
		source.admissions(::onChunkLoaded)
		val cells = source.trackObstacles()
		if (cells !== obstacleCells) {
			obstacleCells = cells
			snapshot.entityCells(cells)
		}
		recorder?.flush()
	}

	fun onBlockChanged(pos: BlockPos) {
		changed(pos.x, pos.y, pos.z, waterOnly = false)
		changed(pos.x, pos.y - 1, pos.z, waterOnly = true)
		for (dy in -1..1) {
			changed(pos.x + 1, pos.y + dy, pos.z, waterOnly = true)
			changed(pos.x - 1, pos.y + dy, pos.z, waterOnly = true)
			changed(pos.x, pos.y + dy, pos.z + 1, waterOnly = true)
			changed(pos.x, pos.y + dy, pos.z - 1, waterOnly = true)
		}
	}

	private fun changed(x: Int, y: Int, z: Int, waterOnly: Boolean) {
		if (!bounds.contains(x, y, z) || !sections.cellChanged(x, y, z, waterOnly)) return
		cellChanges.incrementAndGet()
		revisions.recordMutation(VoxelPos(x, y, z))
	}

	fun onChunkLoaded(chunkX: Int, chunkZ: Int) {
		if (!source.admits(chunkX, chunkZ)) return
		val known = source.attach(chunkX, chunkZ)
		val reload = sections.chunkLoaded(chunkX, chunkZ, known)
		if (known) reloads.incrementAndGet()
		when {
			reload.whole -> {
				changedReloads.incrementAndGet()
				logReload(chunkX, chunkZ, "more than a section's worth of cells")
				revisions.recordChunkChanged(PathingChunk(chunkX, chunkZ))
			}
			reload.cells.isNotEmpty() -> {
				changedReloads.incrementAndGet()
				logReload(chunkX, chunkZ, "${reload.cells.size} cells")
				for (cell in reload.cells) revisions.recordMutation(cell)
			}
		}
		if (known) revisions.signal() else revisions.recordArrived((minSectionY..maxSectionY).map { PathingSection(chunkX, it, chunkZ) })
	}

	fun onChunkUnloaded(chunkX: Int, chunkZ: Int, chunkSections: Array<ChunkSection>?) {
		for (evicted in source.detach(chunkX, chunkZ, chunkSections)) {
			sections.chunkForgotten(evicted.toInt(), (evicted ushr 32).toInt())
			forgotten.incrementAndGet()
		}
	}

	fun mutationsSince(snapshotRevision: Long): List<WorldMutation> = revisions.mutationsSince(snapshotRevision)

	fun close() = revisions.close()

	fun subscribe(): WorldEvents = WorldEvents(revisions.subscribe())

	class WorldEvents internal constructor(private val subscription: RevisionLog.Subscription) : AutoCloseable {
		fun drain(): WorldEventBatch = subscription.drain()

		override fun close() = subscription.close()
	}

	fun awaitEvents(sinceRevision: Long, timeoutMillis: Long): Boolean =
		revisions.awaitEvents(sinceRevision, timeoutMillis)

	private var reloadLogWindow = 0L
	private var reloadsLogged = 0

	private fun logReload(chunkX: Int, chunkZ: Int, what: String) {
		val now = System.currentTimeMillis()
		if (now - reloadLogWindow > RELOAD_LOG_WINDOW_MILLIS) {
			reloadLogWindow = now
			reloadsLogged = 0
		}
		if (reloadsLogged++ >= RELOAD_LOGS_PER_WINDOW) return
		val dx = chunkX - source.playerChunkX
		val dz = chunkZ - source.playerChunkZ
		LOG.info(
			"Chunk ({}, {}) reloaded {} chunks from the player (dx {}, dz {}, view distance {}) and changed {} the planner had read",
			chunkX, chunkZ, maxOf(abs(dx), abs(dz)), dx, dz, source.viewDistance, what,
		)
	}

	private companion object {
		const val RELOAD_LOG_WINDOW_MILLIS = 10_000L
		const val RELOAD_LOGS_PER_WINDOW = 8
	}
}
