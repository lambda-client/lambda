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
import com.lambda.graphics.mc.RegionVertexCollector
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.text.SDFFontAtlas
import com.mojang.blaze3d.buffers.GpuBuffer
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

	// Font atlas used for current text rendering
	private var currentFontAtlas: SDFFontAtlas? = null
	
	// Styled text buffers: maps SDFStyle to (buffer, indexCount)
	private val styledTextBuffers = mutableMapOf<RenderBuilder.SDFStyle, Pair<GpuBuffer, Int>>()
	private val styledScreenTextBuffers = mutableMapOf<RenderBuilder.SDFStyle, Pair<GpuBuffer, Int>>()

	/** Upload collected geometry to GPU. Must be called on main thread. */
	fun upload() {
		// Clean up previous styled buffers
		styledTextBuffers.values.forEach { (buffer, _) -> buffer.close() }
		styledTextBuffers.clear()
		styledScreenTextBuffers.values.forEach { (buffer, _) -> buffer.close() }
		styledScreenTextBuffers.clear()
		
		renderBuilder?.let { s ->
			renderer.upload(s.collector)
			currentFontAtlas = s.fontAtlas
			
			// Upload styled text groups
			s.textStyleGroups.forEach { (style, vertices) ->
				val result = RegionVertexCollector.uploadTextVertices(vertices)
				if (result.buffer != null) {
					styledTextBuffers[style] = result.buffer to result.indexCount
				}
			}
			
			// Upload styled screen text groups
			s.screenTextStyleGroups.forEach { (style, vertices) ->
				val result = RegionVertexCollector.uploadScreenTextVertices(vertices)
				if (result.buffer != null) {
					styledScreenTextBuffers[style] = result.buffer to result.indexCount
				}
			}
		} ?: run {
			renderer.clearData()
			currentFontAtlas = null
		}
	}

	/** Close and release all GPU resources. */
	fun close() {
		renderer.close()
		styledTextBuffers.values.forEach { (buffer, _) -> buffer.close() }
		styledTextBuffers.clear()
		styledScreenTextBuffers.values.forEach { (buffer, _) -> buffer.close() }
		styledScreenTextBuffers.clear()
		clear()
	}

	/**
	 * Render with smooth camera interpolation.
	 * Computes delta between tick-camera and current-camera in double precision.
	 */
	fun render() {
		val currentCameraPos = mc.gameRenderer?.camera?.pos ?: return
		val tickCamera = tickCameraPos ?: return
		if (!renderer.hasData() && styledTextBuffers.isEmpty()) return

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
		RegionRenderer.createRenderPass("$name Faces", depthTest)?.use { pass ->
			pass.setPipeline(RendererUtils.getFacesPipeline(depthTest))
			RenderSystem.bindDefaultUniforms(pass)
			pass.setUniform("DynamicTransforms", dynamicTransform)
			renderer.renderFaces(pass)
		}

		// Render Edges
		RegionRenderer.createRenderPass("$name Edges", depthTest)?.use { pass ->
			pass.setPipeline(RendererUtils.getEdgesPipeline(depthTest))
			RenderSystem.bindDefaultUniforms(pass)
			pass.setUniform("DynamicTransforms", dynamicTransform)
			renderer.renderEdges(pass)
		}

		// Render Styled Text - each style gets its own SDF params
		if (styledTextBuffers.isNotEmpty()) {
			val atlas = currentFontAtlas
			if (atlas != null) {
				if (!atlas.isUploaded) atlas.upload()
				val textureView = atlas.textureView
				val sampler = atlas.sampler
				if (textureView != null && sampler != null) {
					styledTextBuffers.forEach { (style, bufferInfo) ->
						val (buffer, indexCount) = bufferInfo
						val outlineWidth = style.outline?.width ?: 0f
						val glowRadius = style.glow?.radius ?: 0.2f
						val shadowSoftness = style.shadow?.softness ?: 0.15f
						
						val sdfParams = RendererUtils.createSDFParamsBuffer(outlineWidth, glowRadius, shadowSoftness)
						if (sdfParams != null) {
							RegionRenderer.createRenderPass("$name Text", depthTest)?.use { pass ->
								pass.setPipeline(RendererUtils.getTextPipeline(depthTest))
								RenderSystem.bindDefaultUniforms(pass)
								pass.setUniform("DynamicTransforms", dynamicTransform)
								pass.setUniform("SDFParams", sdfParams)
								pass.bindTexture("Sampler0", textureView, sampler)
								RegionRenderer.renderQuadBuffer(pass, buffer, indexCount)
							}
							sdfParams.close()
						}
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
		if (!renderer.hasScreenData() && styledScreenTextBuffers.isEmpty()) return

		RendererUtils.withScreenContext {
			val dynamicTransform = RendererUtils.createScreenDynamicTransform()

			// Render Screen Faces
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

			// Render Styled Screen Text - each style gets its own SDF params
			if (styledScreenTextBuffers.isNotEmpty()) {
				val atlas = currentFontAtlas
				if (atlas != null) {
					if (!atlas.isUploaded) atlas.upload()
					val textureView = atlas.textureView
					val sampler = atlas.sampler
					if (textureView != null && sampler != null) {
						styledScreenTextBuffers.forEach { (style, bufferInfo) ->
							val (buffer, indexCount) = bufferInfo
							val outlineWidth = style.outline?.width ?: 0f
							val glowRadius = style.glow?.radius ?: 0.2f
							val shadowSoftness = style.shadow?.softness ?: 0.15f
							
							val sdfParams = RendererUtils.createSDFParamsBuffer(outlineWidth, glowRadius, shadowSoftness)
							if (sdfParams != null) {
								RegionRenderer.Companion.createRenderPass("$name Screen Text", false)?.use { pass ->
									pass.setPipeline(RendererUtils.screenTextPipeline)
									RenderSystem.bindDefaultUniforms(pass)
									pass.setUniform("DynamicTransforms", dynamicTransform)
									pass.setUniform("SDFParams", sdfParams)
									pass.bindTexture("Sampler0", textureView, sampler)
									RegionRenderer.renderQuadBuffer(pass, buffer, indexCount)
								}
								sdfParams.close()
							}
						}
					}
				}
			}
		}
	}
}
