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

package com.lambda.graphics.esp

import com.lambda.Lambda.mc
import com.lambda.graphics.mc.LambdaRenderPipelines
import com.lambda.graphics.mc.RegionRenderer
import com.lambda.graphics.mc.RenderRegion
import com.lambda.util.extension.tickDelta
import com.mojang.blaze3d.systems.RenderSystem
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * Base class for region-based ESP systems. Provides unified rendering logic and region management.
 */
abstract class RegionESP(val name: String, val depthTest: Boolean) {
    protected val renderers = ConcurrentHashMap<Long, RegionRenderer>()

    /** Get or create a ShapeScope for a specific world position. */
    open fun shapes(x: Double, y: Double, z: Double, block: ShapeScope.() -> Unit) {}

    /** Upload collected geometry to GPU. Must be called on main thread. */
    open fun upload() {}

    /** Clear all geometry data. */
    abstract fun clear()

    /** Close and release all GPU resources. */
    open fun close() {
        renderers.values.forEach { it.close() }
        renderers.clear()
        clear()
    }

    /**
     * Render all active regions.
     * @param tickDelta Progress within current tick (used for interpolation)
     */
    open fun render(tickDelta: Float = mc.tickDelta) {
        val camera = mc.gameRenderer?.camera ?: return
        val cameraPos = camera.pos

        val activeRenderers = renderers.values.filter { it.hasData() }
        if (activeRenderers.isEmpty()) return

        val modelViewMatrix = com.lambda.graphics.RenderMain.modelViewMatrix
        val transforms = activeRenderers.map { renderer ->
            val offset = renderer.region.computeCameraRelativeOffset(cameraPos)
            val modelView = Matrix4f(modelViewMatrix).translate(offset)

            val dynamicTransform = RenderSystem.getDynamicUniforms()
                .write(
                    modelView,
                    Vector4f(1f, 1f, 1f, 1f),
                    Vector3f(0f, 0f, 0f),
                    Matrix4f()
                )
            renderer to dynamicTransform
        }

        // Render Faces
        RegionRenderer.createRenderPass("$name Faces")?.use { pass ->
            val pipeline =
                    if (depthTest) LambdaRenderPipelines.ESP_QUADS
                    else LambdaRenderPipelines.ESP_QUADS_THROUGH
            pass.setPipeline(pipeline)
            RenderSystem.bindDefaultUniforms(pass)
            transforms.forEach { (renderer, transform) ->
                pass.setUniform("DynamicTransforms", transform)
                renderer.renderFaces(pass)
            }
        }

        // Render Edges
        RegionRenderer.createRenderPass("$name Edges")?.use { pass ->
            val pipeline =
                    if (depthTest) LambdaRenderPipelines.ESP_LINES
                    else LambdaRenderPipelines.ESP_LINES_THROUGH
            pass.setPipeline(pipeline)
            RenderSystem.bindDefaultUniforms(pass)
            transforms.forEach { (renderer, transform) ->
                pass.setUniform("DynamicTransforms", transform)
                renderer.renderEdges(pass)
            }
        }
    }

    /**
     * Compute a unique key for a region based on its coordinates. Prevents collisions between
     * regions at different Y levels.
     */
    protected fun getRegionKey(x: Double, y: Double, z: Double): Long {
        val rx = (RenderRegion.REGION_SIZE * floor(x / RenderRegion.REGION_SIZE)).toInt()
        val ry = (RenderRegion.REGION_SIZE * floor(y / RenderRegion.REGION_SIZE)).toInt()
        val rz = (RenderRegion.REGION_SIZE * floor(z / RenderRegion.REGION_SIZE)).toInt()

        return getRegionKey(rx, ry, rz)
    }

    protected fun getRegionKey(rx: Int, ry: Int, rz: Int): Long {
        // 20 bits for X, 20 bits for Z, 24 bits for Y (total 64)
        // This supports +- 500k blocks in X/Z and full Y range
        return (rx.toLong() and 0xFFFFF) or
                ((rz.toLong() and 0xFFFFF) shl 20) or
                ((ry.toLong() and 0xFFFFFF) shl 40)
    }
}
