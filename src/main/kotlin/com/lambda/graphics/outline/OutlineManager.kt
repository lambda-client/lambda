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

package com.lambda.graphics.outline

import net.minecraft.util.math.BlockPos

/**
 * Central registry for outline effects.
 * 
 * Modules register their outline targets each frame by calling the set methods.
 * The manager tracks entity IDs and block positions that should be outlined,
 * along with their style configuration.
 * 
 * The outline system integrates with Minecraft's existing entity outline pipeline
 * for entities (no re-render needed), and uses custom rendering for blocks.
 * 
 * Usage:
 * ```kotlin
 * // In your module's onRenderWorld:
 * OutlineManager.clear()
 * 
 * world.entities.filter { it is HostileEntity }.forEach { entity ->
 *     OutlineManager.setEntityOutline(entity.id, OutlineStyle.HOSTILE)
 * }
 * ```
 */
object OutlineManager {
    // Entity outlines by entity ID
    private val entityOutlines = mutableMapOf<Int, OutlineStyle>()
    
    // Block positions to outline (Phase 3)
    private val blockOutlines = mutableMapOf<BlockPos, OutlineStyle>()
    
    /**
     * Set an outline for an entity by its ID.
     * Pass null to remove the outline.
     */
    fun setEntityOutline(entityId: Int, style: OutlineStyle?) {
        if (style != null) {
            entityOutlines[entityId] = style
        } else {
            entityOutlines.remove(entityId)
        }
    }
    
    /**
     * Get the outline style for an entity, or null if not outlined.
     */
    fun getEntityOutline(entityId: Int): OutlineStyle? = entityOutlines[entityId]
    
    /**
     * Check if any entity outlines are registered.
     */
    fun hasEntityOutlines(): Boolean = entityOutlines.isNotEmpty()
    
    /**
     * Check if an entity's vertices should be captured.
     * Called from EntityRenderManagerMixin during MC's render pass.
     */
    @JvmStatic
    fun shouldCapture(entityId: Int): Boolean = entityOutlines.containsKey(entityId)
    
    /**
     * Get all entity outlines (for iteration during rendering).
     */
    fun getEntityOutlines(): Map<Int, OutlineStyle> = entityOutlines
    
    /**
     * Set an outline for a block position.
     * Pass null to remove the outline.
     */
    fun setBlockOutline(pos: BlockPos, style: OutlineStyle?) {
        if (style != null) {
            blockOutlines[pos] = style
        } else {
            blockOutlines.remove(pos)
        }
    }
    
    /**
     * Get the outline style for a block position, or null if not outlined.
     */
    fun getBlockOutline(pos: BlockPos): OutlineStyle? = blockOutlines[pos]
    
    /**
     * Check if any block outlines are registered.
     */
    fun hasBlockOutlines(): Boolean = blockOutlines.isNotEmpty()
    
    /**
     * Get all block outlines (for iteration during rendering).
     */
    fun getBlockOutlines(): Map<BlockPos, OutlineStyle> = blockOutlines
    
    /**
     * Clear all outline registrations.
     * Should be called at the start of each frame before modules register their outlines.
     */
    fun clear() {
        entityOutlines.clear()
        blockOutlines.clear()
    }
    
    /**
     * Clear only entity outlines (useful for partial updates).
     */
    fun clearEntities() {
        entityOutlines.clear()
    }
    
    /**
     * Clear only block outlines (useful for partial updates).
     */
    fun clearBlocks() {
        blockOutlines.clear()
    }
}
