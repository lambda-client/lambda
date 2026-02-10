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

/**
 * Per-frame vertex capture storage.
 * 
 * During Minecraft's entity render pass, vertices are captured and mapped to entity IDs.
 * During Lambda's render pass, these vertices can be retrieved and replayed to an outline FBO.
 * 
 * Call [clear] at the start of each frame before capturing.
 * 
 * Usage:
 * ```kotlin
 * // During MC render (in mixin)
 * VertexCapture.beginCapture(entity.id)
 * // ... vertices are captured via wrapped VertexConsumer
 * VertexCapture.endCapture()
 * 
 * // During Lambda render
 * val geometry = VertexCapture.getEntityGeometry(entityId)
 * // ... render geometry to outline FBO
 * ```
 */
import com.mojang.blaze3d.textures.GpuTextureView

object VertexCapture {
    
    // Entity ID -> list of captured geometries (cleared each frame)
    private val entityGeometries = mutableMapOf<Int, MutableList<CapturedGeometry>>()
    
    // Currently capturing entity ID (or null if not capturing)
    private var capturingEntityId: Int? = null
    
    // Current texture view being captured for the current entity
    private var currentTextureView: GpuTextureView? = null
    
    // Geometry being built for current texture
    private var currentGeometry: CapturedGeometry? = null
    
    /**
     * Begin capturing vertices for an entity.
     * Call before the entity's render method.
     */
    fun beginCapture(entityId: Int) {
        capturingEntityId = entityId
        entityGeometries.getOrPut(entityId) { mutableListOf() }.clear()
        currentTextureView = null
        currentGeometry = null
    }

    /**
     * Set the current texture being used for rendering.
     * This switches the target geometry for vertex capture.
     */
    fun setActiveTexture(textureView: GpuTextureView?) {
        val entityId = capturingEntityId ?: return
        
        // Find existing geometry for this texture or create new one
        val list = entityGeometries[entityId] ?: return
        
        if (textureView != currentTextureView || currentGeometry == null) {
            currentTextureView = textureView
            currentGeometry = list.find { it.textureView == textureView }
            
            if (currentGeometry == null) {
                val newGeom = CapturedGeometry(textureView)
                list.add(newGeom)
                currentGeometry = newGeom
            }
        }
    }
    
    /**
     * End capturing vertices for the current entity.
     */
    fun endCapture() {
        // We actually clear empty ones here to save memory
        val entityId = capturingEntityId ?: return
        entityGeometries[entityId]?.removeAll { it.isEmpty() }
        if (entityGeometries[entityId]?.isEmpty() == true) {
            entityGeometries.remove(entityId)
        }

        capturingEntityId = null
        currentTextureView = null
        currentGeometry = null
    }
    
    fun isCapturing(): Boolean = capturingEntityId != null
    fun isCapturing(entityId: Int): Boolean = capturingEntityId == entityId
    
    /**
     * Add a vertex to the current capture.
     */
    fun captureVertex(x: Float, y: Float, z: Float, w: Float, nx: Float, ny: Float, nz: Float, u: Float = 0f, v: Float = 0f) {
        currentGeometry?.addVertex(x, y, z, w, nx, ny, nz, u, v)
    }
    
    /**
     * Get all captured geometries for an entity.
     */
    fun getEntityGeometries(entityId: Int): List<CapturedGeometry> = entityGeometries[entityId] ?: emptyList()
    
    fun hasEntityGeometry(): Boolean = entityGeometries.isNotEmpty()
    fun getCapturedEntityIds(): Set<Int> = entityGeometries.keys
    
    fun clear() {
        entityGeometries.clear()
        capturingEntityId = null
        currentTextureView = null
        currentGeometry = null
    }
}
