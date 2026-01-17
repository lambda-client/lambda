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

package com.lambda.graphics.mc.renderer

import com.lambda.Lambda.mc
import com.lambda.graphics.RenderMain
import com.lambda.graphics.mc.LambdaRenderPipelines
import com.lambda.graphics.mc.RegionRenderer
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.text.SDFFontAtlas
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil

/**
 * Modern replacement for the legacy Treed system. Handles geometry that is cleared and rebuilt
 * every tick.
 * 
 * Geometry is stored relative to the camera position at tick time. At render time, we compute
 * the delta between tick-camera and current-camera to ensure smooth motion without jitter.
 */
class TickedRenderer(val name: String, var depthTest: Boolean = false) {
	private val renderer = RegionRenderer()
	private var renderBuilder: RenderBuilder? = null
	
	// Camera position captured at tick time (when shapes are built)
	private var tickCameraPos: Vec3d? = null

	/** Get the current shape scope for drawing. Geometry stored relative to tick camera. */
	fun shapes(block: RenderBuilder.() -> Unit) {
		val cameraPos = mc.gameRenderer?.camera?.pos ?: return
		if (renderBuilder == null) {
			tickCameraPos = cameraPos
			renderBuilder = RenderBuilder(cameraPos)
		}
		renderBuilder?.apply(block)
	}

	/** Clear all current builders. Call this at the end of every tick. */
	fun clear() {
		renderBuilder = null
		tickCameraPos = null
	}

	/** Upload collected geometry to GPU. Must be called on main thread. */
	fun upload() {
		renderBuilder?.let { s ->
			renderer.upload(s.collector)
			currentFontAtlas = s.fontAtlas
		} ?: run {
			renderer.clearData()
			currentFontAtlas = null
		}
	}

	// Font atlas used for current text rendering
	private var currentFontAtlas: SDFFontAtlas? = null

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

		val modelViewMatrix = RenderMain.modelViewMatrix

		// Compute the camera movement since tick time in double precision
		// Geometry is stored relative to tickCamera, so we translate by (tickCamera - currentCamera)
		val deltaX = (tickCamera.x - currentCameraPos.x).toFloat()
		val deltaY = (tickCamera.y - currentCameraPos.y).toFloat()
		val deltaZ = (tickCamera.z - currentCameraPos.z).toFloat()

		val modelView = Matrix4f(modelViewMatrix).translate(deltaX, deltaY, deltaZ)
		val dynamicTransform = RenderSystem.getDynamicUniforms()
			.write(modelView, Vector4f(1f, 1f, 1f, 1f), Vector3f(0f, 0f, 0f), Matrix4f())

		// Render Faces
		RegionRenderer.Companion.createRenderPass("$name Faces", depthTest)?.use { pass ->
			val pipeline =
				if (depthTest) LambdaRenderPipelines.ESP_QUADS
				else LambdaRenderPipelines.ESP_QUADS_THROUGH
			pass.setPipeline(pipeline)
			RenderSystem.bindDefaultUniforms(pass)
			pass.setUniform("DynamicTransforms", dynamicTransform)
			renderer.renderFaces(pass)
		}

		// Render Edges
		RegionRenderer.Companion.createRenderPass("$name Edges", depthTest)?.use { pass ->
			val pipeline =
				if (depthTest) LambdaRenderPipelines.ESP_LINES
				else LambdaRenderPipelines.ESP_LINES_THROUGH
			pass.setPipeline(pipeline)
			RenderSystem.bindDefaultUniforms(pass)
			pass.setUniform("DynamicTransforms", dynamicTransform)
			renderer.renderEdges(pass)
		}

		// Render Text
		if (renderer.hasTextData()) {
			val atlas = currentFontAtlas
			if (atlas != null) {
				if (!atlas.isUploaded) atlas.upload()
				val textureView = atlas.textureView
				val sampler = atlas.sampler
				if (textureView != null && sampler != null) {
					val sdfParams = createSDFParamsBuffer()
					if (sdfParams != null) {
						RegionRenderer.Companion.createRenderPass("$name Text", depthTest)?.use { pass ->
							val pipeline =
								if (depthTest) LambdaRenderPipelines.SDF_TEXT
								else LambdaRenderPipelines.SDF_TEXT_THROUGH
							pass.setPipeline(pipeline)
							RenderSystem.bindDefaultUniforms(pass)
							pass.setUniform("DynamicTransforms", dynamicTransform)
							pass.setUniform("SDFParams", sdfParams)
							pass.bindTexture("Sampler0", textureView, sampler)
							renderer.renderText(pass)
						}
						sdfParams.close()
					}
				}
			}
		}
	}

	private fun createSDFParamsBuffer(): GpuBuffer? {
		val device = RenderSystem.getDevice()
		val buffer = MemoryUtil.memAlloc(16)
		return try {
			buffer.putFloat(0.5f)
			buffer.putFloat(0.1f)
			buffer.putFloat(0.2f)
			buffer.putFloat(0.15f)
			buffer.flip()
			device.createBuffer({ "SDFParams" }, GpuBuffer.USAGE_UNIFORM, buffer)
		} catch (e: Exception) {
			null
		} finally {
			MemoryUtil.memFree(buffer)
		}
	}
}
