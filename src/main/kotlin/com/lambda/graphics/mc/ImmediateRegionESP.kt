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
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil

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
	private var renderBuilder: RenderBuilder? = null

	/**
	 * Get the current camera position for building camera-relative shapes.
	 * Returns null if camera is not available.
	 */
	private fun getCameraPos(): Vec3d? = mc.gameRenderer?.camera?.pos

	/** Get or create a ShapeScope for drawing with camera-relative coordinates. */
	fun shapes(block: RenderBuilder.() -> Unit) {
		val s = renderBuilder ?: RenderBuilder(getCameraPos() ?: return).also { renderBuilder = it }
		s.apply(block)
	}

	/** Clear all geometry data. */
	fun clear() {
		renderBuilder = null
	}

	/** Called each tick to reset for next frame. */
	fun tick() {
		renderBuilder = null
	}

	/** Upload collected geometry to GPU. Must be called on main thread. */
	fun upload() {
		renderBuilder?.let { s ->
			renderer.upload(s.collector)
			// Track font atlas for text rendering
			currentFontAtlas = s.fontAtlas
		} ?: run {
			renderer.clearData()
			currentFontAtlas = null
		}
	}

	// Font atlas used for current text rendering
	private var currentFontAtlas: com.lambda.graphics.text.SDFFontAtlas? = null

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
						RegionRenderer.createRenderPass("$name Text", depthTest)?.use { pass ->
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

	/**
	 * Create SDF params uniform buffer with default values.
	 */
	private fun createSDFParamsBuffer(): com.mojang.blaze3d.buffers.GpuBuffer? {
		val device = RenderSystem.getDevice()
		val buffer = MemoryUtil.memAlloc(16)
		return try {
			buffer.putFloat(0.5f)   // SDFThreshold
			buffer.putFloat(0.1f)   // OutlineWidth
			buffer.putFloat(0.2f)   // GlowRadius
			buffer.putFloat(0.15f)  // ShadowSoftness
			buffer.flip()
			device.createBuffer({ "SDFParams" }, com.mojang.blaze3d.buffers.GpuBuffer.USAGE_UNIFORM, buffer)
		} catch (_: Exception) {
			null
		} finally {
			MemoryUtil.memFree(buffer)
		}
	}
}
