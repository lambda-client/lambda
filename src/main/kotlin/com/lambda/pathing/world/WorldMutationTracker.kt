/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.world

import net.minecraft.util.math.BlockPos

data class PathingSection(val x: Int, val y: Int, val z: Int) {
    companion object {
        fun containing(pos: VoxelPos) = PathingSection(pos.x shr 4, pos.y shr 4, pos.z shr 4)
        fun containing(pos: BlockPos) = PathingSection(pos.x shr 4, pos.y shr 4, pos.z shr 4)
    }
}

data class PathingChunk(val x: Int, val z: Int) {
    companion object {
        fun containing(pos: VoxelPos) = PathingChunk(pos.x shr 4, pos.z shr 4)
    }
}

sealed interface WorldMutation {
    val revision: Long

    data class Section(
        val section: PathingSection,
        override val revision: Long,
    ) : WorldMutation

    data class Chunk(
        val chunk: PathingChunk,
        override val revision: Long,
    ) : WorldMutation
}

/**
 * Monotonic, dependency-aware record of changes applied to the client world.
 *
 * Block changes are tracked at section granularity. This is deliberately conservative:
 * an unrelated block in a depended-on 16³ section may force a replan, but a relevant
 * change can never be missed because an exact per-block history was pruned.
 */
internal class WorldMutationTracker {
    private var revision = 0L
    private val sectionRevisions = HashMap<PathingSection, Long>()
    private val chunkRevisions = HashMap<PathingChunk, Long>()

    @Synchronized
    fun currentRevision(): Long = revision

    @Synchronized
    fun markBlock(pos: BlockPos): Long {
        val next = ++revision
        sectionRevisions[PathingSection.containing(pos)] = next
        return next
    }

    @Synchronized
    fun markChunk(chunkX: Int, chunkZ: Int): Long {
        val next = ++revision
        chunkRevisions[PathingChunk(chunkX, chunkZ)] = next
        return next
    }

    @Synchronized
    fun changeSince(
        snapshotRevision: Long,
        sections: Set<PathingSection>,
        chunks: Set<PathingChunk>,
    ): WorldMutation? {
        if (revision <= snapshotRevision) return null

        sections.forEach { section ->
            val changedAt = sectionRevisions[section] ?: return@forEach
            if (changedAt > snapshotRevision) return WorldMutation.Section(section, changedAt)
        }
        chunks.forEach { chunk ->
            val changedAt = chunkRevisions[chunk] ?: return@forEach
            if (changedAt > snapshotRevision) return WorldMutation.Chunk(chunk, changedAt)
        }
        return null
    }

    @Synchronized
    fun reset() {
        revision = 0L
        sectionRevisions.clear()
        chunkRevisions.clear()
    }
}
