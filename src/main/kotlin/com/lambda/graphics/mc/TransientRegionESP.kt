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

import com.lambda.Lambda.mc
import com.lambda.graphics.esp.ShapeScope
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * Modern replacement for the legacy Treed system. Handles geometry that is cleared and rebuilt
 * every tick.
 * 
 * Geometry is stored relative to the camera position at tick time. At render time, we compute
 * the delta between tick-camera and current-camera to ensure smooth motion without jitter.
 */
class TransientRegionESP(val name: String, var depthTest: Boolean = false) {
	private val renderer = RegionRenderer()
	private var scope: ShapeScope? = null
	
	// Camera position captured at tick time (when shapes are built)
	private var tickCameraPos: Vec3d? = null

	/** Get the current shape scope for drawing. Geometry stored relative to tick camera. */
	fun shapes(block: ShapeScope.() -> Unit) {
		val cameraPos = mc.gameRenderer?.camera?.pos ?: return
		if (scope == null) {
			tickCameraPos = cameraPos
			scope = ShapeScope(cameraPos)
		}
		scope?.apply(block)
	}

	/** Clear all current builders. Call this at the end of every tick. */
	fun clear() {
		scope = null
		tickCameraPos = null
	}

	/** Upload collected geometry to GPU. Must be called on main thread. */
	fun upload() {
		scope?.let { s ->
			renderer.upload(s.builder.collector)
		} ?: renderer.clearData()
	}

	/** Close and release all GPU resources. */
	fun close() {
		renderer.close()
		clear()
	}

	/**
	 * Render with smooth camera interpolation.
	 * Computes delta between tick-camera and current-camera in double precision.
	 */
	fun render() {
		val currentCameraPos = mc.gameRenderer?.camera?.pos ?: return
		val tickCamera = tickCameraPos ?: return
		if (!renderer.hasData()) return

		val modelViewMatrix = com.lambda.graphics.RenderMain.modelViewMatrix

		// Compute the camera movement since tick time in double precision
		// Geometry is stored relative to tickCamera, so we translate by (tickCamera - currentCamera)
		val deltaX = (tickCamera.x - currentCameraPos.x).toFloat()
		val deltaY = (tickCamera.y - currentCameraPos.y).toFloat()
		val deltaZ = (tickCamera.z - currentCameraPos.z).toFloat()

		val modelView = Matrix4f(modelViewMatrix).translate(deltaX, deltaY, deltaZ)
		val dynamicTransform = RenderSystem.getDynamicUniforms()
			.write(modelView, Vector4f(1f, 1f, 1f, 1f), Vector3f(0f, 0f, 0f), Matrix4f())

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
