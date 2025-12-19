/*
 * Copyright 2025 Lambda
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

package com.lambda.graphics.mc

import net.minecraft.util.math.Vec3d
import org.joml.Vector3f

/**
 * A render region represents a chunk-sized area in the world where vertices are stored relative to
 * the region's origin. This solves floating-point precision issues at large world coordinates.
 *
 * @param originX The X coordinate of the region's origin (typically chunk corner)
 * @param originY The Y coordinate of the region's origin
 * @param originZ The Z coordinate of the region's origin
 */
class RenderRegion(val originX: Int, val originY: Int, val originZ: Int) {
    /**
     * Compute the camera-relative offset for this region. This is done in double precision to
     * maintain accuracy at large coordinates.
     *
     * @param cameraPos The camera's world position (double precision)
     * @return The offset from camera to region origin (small float, high precision)
     */
    fun computeCameraRelativeOffset(cameraPos: Vec3d): Vector3f {
        val offsetX = originX.toDouble() - cameraPos.x
        val offsetY = originY.toDouble() - cameraPos.y
        val offsetZ = originZ.toDouble() - cameraPos.z
        return Vector3f(offsetX.toFloat(), offsetY.toFloat(), offsetZ.toFloat())
    }

    companion object {
        /** Standard size of a render region (matches Minecraft chunk size). */
        const val REGION_SIZE = 16

        /**
         * Create a region for a chunk position.
         *
         * @param chunkX Chunk X coordinate
         * @param chunkZ Chunk Z coordinate
         * @param bottomY World bottom Y coordinate (typically -64)
         */
        fun forChunk(chunkX: Int, chunkZ: Int, bottomY: Int) =
            RenderRegion(chunkX * 16, bottomY, chunkZ * 16)
    }
}
