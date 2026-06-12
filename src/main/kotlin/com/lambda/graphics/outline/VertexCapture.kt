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

import com.mojang.blaze3d.textures.GpuTextureView

@Suppress("unused")
object VertexCapture {
    private val entityGeometries = mutableMapOf<Any, MutableList<CapturedGeometry>>()

    private var capturingEntityId: Any? = null

    private var currentTextureView: GpuTextureView? = null

    private var currentGeometry: CapturedGeometry? = null
    
    fun beginCapture(entityId: Any) {
        capturingEntityId = entityId
        entityGeometries.getOrPut(entityId) { mutableListOf() }.clear()
        currentTextureView = null
        currentGeometry = null
    }

    fun setActiveTexture(textureView: GpuTextureView?) {
        val entityId = capturingEntityId ?: return

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
    
    fun endCapture() {
        val entityId = capturingEntityId ?: return
        entityGeometries[entityId]?.removeAll { it.isEmpty() }
        if (entityGeometries[entityId]?.isEmpty() == true) entityGeometries.remove(entityId)

        capturingEntityId = null
        currentTextureView = null
        currentGeometry = null
    }
    
    fun isCapturing(): Boolean = capturingEntityId != null
    fun isCapturing(entityId: Any): Boolean = capturingEntityId == entityId
    
    fun captureVertex(x: Float, y: Float, z: Float, w: Float, nx: Float, ny: Float, nz: Float, u: Float = 0f, v: Float = 0f) {
        currentGeometry?.addVertex(x, y, z, w, nx, ny, nz, u, v)
    }
    
    fun getEntityGeometries(entityId: Any): List<CapturedGeometry> = entityGeometries[entityId] ?: emptyList()
    
    fun hasEntityGeometry(): Boolean = entityGeometries.isNotEmpty()
    fun getCapturedEntityIds(): Set<Any> = entityGeometries.keys
    
    fun clear() {
        entityGeometries.clear()
        capturingEntityId = null
        currentTextureView = null
        currentGeometry = null
    }
}

data class CapturedVertex(
    val x: Float,
    val y: Float,
    val z: Float,
    val w: Float = 1.0f,
    val nx: Float = 0f,
    val ny: Float = 0f,
    val nz: Float = 1f,
    val u: Float = 0f,
    val v: Float = 0f
)

class CapturedGeometry(val textureView: GpuTextureView?) {
    private val vertices = ArrayList<CapturedVertex>()

    fun addVertex(x: Float, y: Float, z: Float, w: Float, nx: Float, ny: Float, nz: Float, u: Float = 0f, v: Float = 0f) {
        vertices.add(CapturedVertex(x, y, z, w, nx, ny, nz, u, v))
    }

    fun getVertices(): List<CapturedVertex> = vertices

    fun isEmpty(): Boolean = vertices.isEmpty()

    fun size(): Int = vertices.size

    fun clear() {
        vertices.clear()
    }
}
