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

package com.lambda.graphics.mc

import com.lambda.Lambda.mc
import com.lambda.graphics.esp.ShapeScope
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * Interpolated ESP system for smooth entity rendering.
 *
 * This system rebuilds and uploads vertices every frame with camera-relative coordinates.
 * Callers are responsible for providing interpolated positions (e.g., using entity.prevX/x 
 * with tickDelta). The tick() method clears builders to allow smooth transitions between frames.
 */
class ImmediateRegionESP(val name: String, var depthTest: Boolean = false) {
	private val renderer = RegionRenderer()

	// Current frame builder (being populated this frame)
	private var currScope: ShapeScope? = null

	/**
	 * Get the current camera position for building camera-relative shapes.
	 * Returns null if camera is not available.
	 */
	private fun getCameraPos(): Vec3d? = mc.gameRenderer?.camera?.pos

	/** Get or create a ShapeScope for drawing with camera-relative coordinates. */
	fun shapes(block: ShapeScope.() -> Unit) {
		val s = currScope ?: ShapeScope(getCameraPos() ?: return).also { currScope = it }
		s.apply(block)
	}

	/** Clear all geometry data. */
	fun clear() {
		currScope = null
	}

	/** Called each tick to reset for next frame. */
	fun tick() {
		currScope = null
	}

	/** Upload collected geometry to GPU. Must be called on main thread. */
	fun upload() {
		currScope?.let { s ->
			renderer.upload(s.builder.collector)
		} ?: renderer.clearData()
	}

	/** Close and release all GPU resources. */
	fun close() {
		renderer.close()
		clear()
	}

	/**
	 * Render all geometry. Since coordinates are already camera-relative,
	 * we just use the base modelView matrix without additional translation.
	 */
	fun render() {
		if (!renderer.hasData()) return

		val modelViewMatrix = com.lambda.graphics.RenderMain.modelViewMatrix

		val dynamicTransform = RenderSystem.getDynamicUniforms()
			.write(
				modelViewMatrix,
				Vector4f(1f, 1f, 1f, 1f),
				Vector3f(0f, 0f, 0f),
				Matrix4f()
			)

		// Render Faces
		RegionRenderer.createRenderPass("$name Faces", depthTest)?.use { pass ->
			val pipeline =
				if (depthTest) LambdaRenderPipelines.ESP_QUADS
				else LambdaRenderPipelines.ESP_QUADS_THROUGH
			pass.setPipeline(pipeline)
			RenderSystem.bindDefaultUniforms(pass)
			pass.setUniform("DynamicTransforms", dynamicTransform)
			renderer.renderFaces(pass)
		}

		// Render Edges
		RegionRenderer.createRenderPass("$name Edges", depthTest)?.use { pass ->
			val pipeline =
				if (depthTest) LambdaRenderPipelines.ESP_LINES
				else LambdaRenderPipelines.ESP_LINES_THROUGH
			pass.setPipeline(pipeline)
			RenderSystem.bindDefaultUniforms(pass)
			pass.setUniform("DynamicTransforms", dynamicTransform)
			renderer.renderEdges(pass)
		}
	}
}
