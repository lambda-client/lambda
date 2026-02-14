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
