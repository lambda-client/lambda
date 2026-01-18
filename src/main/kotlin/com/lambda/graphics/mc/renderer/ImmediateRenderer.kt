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
import com.lambda.graphics.mc.RegionRenderer
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.text.SDFFontAtlas
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
class ImmediateRenderer(val name: String, var depthTest: Boolean = false) {
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
	private var currentFontAtlas: SDFFontAtlas? = null

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

		val modelViewMatrix = RenderMain.modelViewMatrix

		val dynamicTransform = RenderSystem.getDynamicUniforms()
			.write(
				modelViewMatrix,
				Vector4f(1f, 1f, 1f, 1f),
				Vector3f(0f, 0f, 0f),
				Matrix4f()
			)

		// Render Faces
		RegionRenderer.Companion.createRenderPass("$name Faces", depthTest)?.use { pass ->
			pass.setPipeline(RendererUtils.getFacesPipeline(depthTest))
			RenderSystem.bindDefaultUniforms(pass)
			pass.setUniform("DynamicTransforms", dynamicTransform)
			renderer.renderFaces(pass)
		}

		// Render Edges
		RegionRenderer.Companion.createRenderPass("$name Edges", depthTest)?.use { pass ->
			pass.setPipeline(RendererUtils.getEdgesPipeline(depthTest))
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
					val sdfParams = RendererUtils.createSDFParamsBuffer()
					if (sdfParams != null) {
						RegionRenderer.Companion.createRenderPass("$name Text", depthTest)?.use { pass ->
							pass.setPipeline(RendererUtils.getTextPipeline(depthTest))
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
	 * Render screen-space geometry. Uses orthographic projection for 2D rendering.
	 * This should be called after world-space render() for proper layering.
	 */
	fun renderScreen() {
		if (!renderer.hasScreenData()) return

		RendererUtils.withScreenContext {
			val dynamicTransform = RendererUtils.createScreenDynamicTransform()

			// Render Screen Faces (no depth test for 2D)
			RegionRenderer.createRenderPass("$name Screen Faces", false)?.use { pass ->
				pass.setPipeline(RendererUtils.screenFacesPipeline)
				RenderSystem.bindDefaultUniforms(pass)
				pass.setUniform("DynamicTransforms", dynamicTransform)
				renderer.renderScreenFaces(pass)
			}

			// Render Screen Edges
			RegionRenderer.createRenderPass("$name Screen Edges", false)?.use { pass ->
				pass.setPipeline(RendererUtils.screenEdgesPipeline)
				RenderSystem.bindDefaultUniforms(pass)
				pass.setUniform("DynamicTransforms", dynamicTransform)
				renderer.renderScreenEdges(pass)
			}

			// Render Screen Text
			if (renderer.hasScreenTextData()) {
				val atlas = currentFontAtlas
				if (atlas != null) {
					if (!atlas.isUploaded) atlas.upload()
					val textureView = atlas.textureView
					val sampler = atlas.sampler
					if (textureView != null && sampler != null) {
						val sdfParams = RendererUtils.createSDFParamsBuffer()
						if (sdfParams != null) {
							RegionRenderer.createRenderPass("$name Screen Text", false)?.use { pass ->
								pass.setPipeline(RendererUtils.screenTextPipeline)
								RenderSystem.bindDefaultUniforms(pass)
								pass.setUniform("DynamicTransforms", dynamicTransform)
								pass.setUniform("SDFParams", sdfParams)
								pass.bindTexture("Sampler0", textureView, sampler)
								renderer.renderScreenText(pass)
							}
							sdfParams.close()
						}
					}
				}
			}
		}
	}
}
