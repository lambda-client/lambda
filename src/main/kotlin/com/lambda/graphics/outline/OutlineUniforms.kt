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

import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import net.minecraft.client.gl.DynamicUniformStorage
import org.lwjgl.glfw.GLFW.glfwGetTime
import java.nio.ByteBuffer
import kotlin.math.max

private const val TIME_WRAP_SECONDS = 256.0

object OutlinePostUniforms {
    private val uniformSize = Std140SizeCalculator()
        .putVec2()
        .putFloat()
        .get()

    private val storage = DynamicUniformStorage<PostData>("Lambda - Outline Post UBO", uniformSize, 16)

    fun write(width: Float, height: Float): GpuBufferSlice =
        storage.write(PostData(width, height, (glfwGetTime() % TIME_WRAP_SECONDS).toFloat()))

    fun clear() {
        storage.clear()
    }

    private data class PostData(
        val sizeX: Float,
        val sizeY: Float,
        val time: Float
    ) : DynamicUniformStorage.Uploadable {
        override fun write(buffer: ByteBuffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec2(sizeX, sizeY)
                .putFloat(time)
        }
    }
}

object OutlineGlowUniforms {
    private val uniformSize = Std140SizeCalculator()
        .putVec2()
        .putFloat()
        .putInt()
        .get()

    private val storage = DynamicUniformStorage<GlowData>("Lambda - Outline Glow UBO", uniformSize, 16)

    fun write(width: Int, height: Int, offset: Float, depthTest: Boolean): GpuBufferSlice =
        storage.write(
            GlowData(
                0.5f / max(width, 1),
                0.5f / max(height, 1),
                offset,
                if (depthTest) 1 else 0
            )
        )

    fun clear() {
        storage.clear()
    }

    private data class GlowData(
        val halfTexelX: Float,
        val halfTexelY: Float,
        val offset: Float,
        val depthTest: Int
    ) : DynamicUniformStorage.Uploadable {
        override fun write(buffer: ByteBuffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec2(halfTexelX, halfTexelY)
                .putFloat(offset)
                .putInt(depthTest)
        }
    }
}

object OutlineCompositeUniforms {
    private val uniformSize = Std140SizeCalculator()
        .putFloat()
        .putFloat()
        .putFloat()
        .putFloat()
        .putInt()
        .putInt()
        .putInt()
        .get()

    private val storage = DynamicUniformStorage<CompositeData>("Lambda - Outline Composite UBO", uniformSize, 16)

    fun write(style: OutlineStyle, depthTest: Boolean): GpuBufferSlice =
        storage.write(
            CompositeData(
                style.fillOpacity,
                style.glowMultiplier,
                style.lineWidth,
                style.lineIntensity,
                style.outlineMode.ordinal,
                style.glowPosition.ordinal,
                if (depthTest) 1 else 0,
            )
        )

    fun clear() {
        storage.clear()
    }

    private data class CompositeData(
        val fillOpacity: Float,
        val glowMultiplier: Float,
        val lineWidth: Float,
        val lineIntensity: Float,
        val outlineStyle: Int,
        val glowPosition: Int,
        val depthTest: Int,
    ) : DynamicUniformStorage.Uploadable {
        override fun write(buffer: ByteBuffer) {
            Std140Builder.intoBuffer(buffer)
                .putFloat(fillOpacity)
                .putFloat(glowMultiplier)
                .putFloat(lineWidth)
                .putFloat(lineIntensity)
                .putInt(outlineStyle)
                .putInt(glowPosition)
                .putInt(depthTest)
        }
    }
}
