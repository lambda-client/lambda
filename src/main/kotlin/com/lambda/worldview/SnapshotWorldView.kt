/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.worldview

import net.minecraft.block.BlockState
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.world.chunk.ChunkStatus

/**
 * Copy-on-read snapshot of the observed world, one planning session's base
 * view. Sections are copied to flat int arrays the first time the planner
 * touches them; observed block changes are written through by the owner so
 * the snapshot tracks the world exactly as long as every [WorldEvent] is
 * forwarded.
 *
 * Client thread only — reads and writes both. What this buys even before
 * overlays: the planner's world reads are decoupled from Minecraft chunk
 * internals, in state-id form, and ready to move off-thread once graph
 * generation does.
 */
class SnapshotWorldView(private val world: ClientWorld) : WorldView {
    private val sections = HashMap<Long, IntArray?>()

    override fun stateId(x: Int, y: Int, z: Int): Int {
        if (world.isOutOfHeightLimit(y)) return BlockTraitRegistry.AIR_ID
        val key = ChunkSectionPos.asLong(x shr 4, y shr 4, z shr 4)
        val section = if (key in sections) sections[key] else copySection(x shr 4, y shr 4, z shr 4).also { sections[key] = it }
        if (section == null) return BlockTraitRegistry.AIR_ID
        return section[localIndex(x, y, z)]
    }

    /**
     * Write-through for an observed block change. Sections the planner has
     * never read stay uncopied — they will be copied fresh when first read.
     */
    fun applyObserved(pos: BlockPos, newState: BlockState) {
        if (world.isOutOfHeightLimit(pos.y)) return
        val key = ChunkSectionPos.asLong(pos.x shr 4, pos.y shr 4, pos.z shr 4)
        val section = sections[key] ?: run {
            // An all-air section (null sentinel) that gains a block must
            // materialize; a merely-uncached section can stay lazy.
            if (key in sections) IntArray(SECTION_VOLUME) { BlockTraitRegistry.AIR_ID }.also { sections[key] = it } else return
        }
        section[localIndex(pos.x, pos.y, pos.z)] = BlockTraitRegistry.idOf(newState)
    }

    /**
     * Drops cached sections of one chunk so the next read re-copies. Called
     * on chunk load (previously-unloaded area read as air must refresh) and
     * unload (parity with live reads, which turn to air).
     */
    fun evictChunk(chunkPos: ChunkPos) {
        val bottom = world.bottomSectionCoord
        for (sy in bottom until bottom + world.countVerticalSections()) {
            sections.remove(ChunkSectionPos.asLong(chunkPos.x, sy, chunkPos.z))
        }
    }

    val cachedSectionCount: Int get() = sections.size

    private fun copySection(sx: Int, sy: Int, sz: Int): IntArray? {
        val chunk = world.getChunk(sx, sz, ChunkStatus.FULL, false) ?: return null
        val section = chunk.getSection(chunk.sectionCoordToIndex(sy))
        if (section.isEmpty) return null

        val result = IntArray(SECTION_VOLUME)
        var i = 0
        for (y in 0..15) {
            for (z in 0..15) {
                for (x in 0..15) {
                    result[i++] = BlockTraitRegistry.idOf(section.getBlockState(x, y, z))
                }
            }
        }
        return result
    }

    private fun localIndex(x: Int, y: Int, z: Int): Int =
        ((y and 15) shl 8) or ((z and 15) shl 4) or (x and 15)

    companion object {
        private const val SECTION_VOLUME = 16 * 16 * 16
    }
}
