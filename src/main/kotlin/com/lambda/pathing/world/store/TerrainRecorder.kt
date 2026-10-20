package com.lambda.pathing.world.store

import com.lambda.pathing.world.streaming.SectionRecorder
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.world.World
import net.minecraft.world.chunk.ChunkStatus
import java.util.concurrent.ConcurrentLinkedQueue

class TerrainRecorder(private val world: World, val store: TerrainStore) : SectionRecorder {
	private val queued = ConcurrentLinkedQueue<Long>()
	private val recorded = LongOpenHashSet()

	override fun record(sectionKey: Long) {
		queued.add(sectionKey)
	}

	override fun flush() {
		while (true) {
			val sectionKey = queued.poll() ?: return
			val index = world.sectionCoordToIndex(ChunkSectionPos.unpackY(sectionKey))
			if (index !in 0 until world.countVerticalSections()) continue
			val chunk = world.getChunk(ChunkSectionPos.unpackX(sectionKey), ChunkSectionPos.unpackZ(sectionKey), ChunkStatus.FULL, false) ?: continue
			store.record(sectionKey, chunk.getSection(index).blockStateContainer.copy(), System.currentTimeMillis())
			synchronized(recorded) { recorded.add(sectionKey) }
		}
	}

	fun recordedSections(): LongArray = synchronized(recorded) { recorded.toLongArray() }
}
