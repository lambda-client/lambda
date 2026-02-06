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

import com.lambda.context.SafeContext
import com.lambda.graphics.mc.RegionRenderer
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.text.SDFFontAtlas
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import kotlin.collections.isNotEmpty

/**
 * Abstract base class for ESP renderers.
 * 
 * Provides shared world-space and screen-space rendering logic while allowing
 * subclasses to define their own lifecycle (upload frequency, geometry building, etc.)
 * 
 * Subclasses implement [getRendererTransforms] to return their renderer/transform pairs:
 * - ImmediateRenderer/TickedRenderer: returns a single pair (one renderer, one transform)
 * - ChunkedRenderer: returns multiple pairs (one per active chunk with per-chunk transforms)
 *
 * @param name Debug name for render passes
 * @param depthTest Whether to use depth testing (true = through walls disabled)
 */
abstract class AbstractRenderer(val name: String, var depthTest: SafeContext.() -> Boolean) {
	/**
	 * Get all renderer/transform pairs to render.
	 * Each pair contains a RegionRenderer and its associated dynamic transform.
	 * 
	 * @return List of (RegionRenderer, GpuBufferSlice) pairs, or empty list if nothing to render
	 */
	protected abstract fun getRendererTransforms(): List<Pair<RegionRenderer, GpuBufferSlice>>
	
	/**
	 * Get all screen-space renderers.
	 * Returns renderers that have screen-space data to render.
	 */
	protected abstract fun getScreenRenderers(): List<RegionRenderer>
	
	/** Current font atlas for text rendering (may be null if no text) */
	protected abstract val currentFontAtlas: SDFFontAtlas?

	/**
	 * Render world-space geometry (faces, edges, text).
	 * Iterates over all renderer/transform pairs from getRendererTransforms().
	 */
	fun SafeContext.render() {
		val chunks = getRendererTransforms()
		if (chunks.isEmpty()) return

		// When using xray mode (depthTest=false), clear our custom depth buffer
		// This gives us correct self-ordering while showing through MC's world
		val depth = depthTest()

		if (!depth) {
			RendererUtils.clearXrayDepthBuffer()
		}

		// Render Faces
		RegionRenderer.createRenderPass("$name Faces", depth)?.use { pass ->
			pass.setPipeline(RendererUtils.getFacesPipeline(depth))
			RenderSystem.bindDefaultUniforms(pass)
			chunks.forEach { (renderer, transform) ->
				pass.setUniform("DynamicTransforms", transform)
				renderer.renderFaces(pass)
			}
		}

		// Render Edges
		RegionRenderer.createRenderPass("$name Edges", depth)?.use { pass ->
			pass.setPipeline(RendererUtils.getEdgesPipeline(depth))
			RenderSystem.bindDefaultUniforms(pass)
			chunks.forEach { (renderer, transform) ->
				pass.setUniform("DynamicTransforms", transform)
				renderer.renderEdges(pass)
			}
		}

		// Render Text - style params are now embedded in vertex attributes
		val textChunks = chunks.filter { (renderer, _) -> renderer.hasTextData() }
		val atlas = currentFontAtlas
		if (atlas != null && textChunks.isNotEmpty()) {
			if (!atlas.isUploaded) atlas.upload()
			val textureView = atlas.textureView
			val sampler = atlas.sampler
			if (textureView != null && sampler != null) {
				RegionRenderer.createRenderPass("$name Text", depth)?.use { pass ->
					pass.setPipeline(RendererUtils.getTextPipeline(depth))
					RenderSystem.bindDefaultUniforms(pass)
					pass.bindTexture("Sampler0", textureView, sampler)
					textChunks.forEach { (renderer, transform) ->
						pass.setUniform("DynamicTransforms", transform)
						renderer.renderText(pass)
					}
				}
			}
		}

		// Render World Images
		val imageChunks = chunks.filter { (renderer, _) -> renderer.hasWorldImageData() }
		if (imageChunks.isNotEmpty()) {
			// Pre-load glint texture before creating render pass
			RendererUtils.ensureGlintTextureLoaded()
			
			RegionRenderer.createRenderPass("$name World Images", depth)?.use { pass ->
				pass.setPipeline(RendererUtils.getWorldImagePipeline(depth))
				RenderSystem.bindDefaultUniforms(pass)
				
				// Bind enchantment glint texture for overlay support
				RendererUtils.bindGlintTexture(pass, "Sampler1")
				
				// Use per-chunk transforms for correct positioning
				// Glint animation is calculated in shader using GameTime with corrected speed
				imageChunks.forEach { (renderer, transform) ->
					pass.setUniform("DynamicTransforms", transform)
					renderer.renderWorldImages(pass)
				}
			}
		}

		// Render World Models
		val modelChunks = chunks.filter { (renderer, _) -> renderer.hasModelData() }
		if (modelChunks.isNotEmpty()) {
			RendererUtils.ensureGlintTextureLoaded()
			
			// Create dedicated glint uniform slice (scale 8.0 for vanilla atlas parity)
			val glintUniform = RendererUtils.createGlintUniform(8.0f)
			
			RegionRenderer.createRenderPass("$name World Models", depth)?.use { pass ->
				pass.setPipeline(RendererUtils.getModelPipeline(depth))
				RenderSystem.bindDefaultUniforms(pass)
				
				// Bind overlay, lightmap, and glint textures
				RendererUtils.bindOverlayTexture(pass, "Sampler1")
				RendererUtils.bindLightmapTexture(pass, "Sampler2")
				RendererUtils.bindGlintTexture(pass, "Sampler3")
				
				// Set the global glint animation matrix for this pass
				pass.setUniform("GlintTransforms", glintUniform)
				
				// Use original chunk transforms for correct geometry position
				modelChunks.forEach { (renderer, transform) ->
					pass.setUniform("DynamicTransforms", transform)
					renderer.renderModels(pass)
				}
			}
		}
	}

	/**
	 * Render screen-space geometry. Uses orthographic projection for 2D rendering.
	 * Uses depth testing with xray depth buffer for unified call-order layering.
	 * This should be called after world-space render() for proper layering.
	 */
	fun renderScreen() {
		val renderers = getScreenRenderers()

		RendererUtils.withScreenContext {
			val dynamicTransform = RendererUtils.createScreenDynamicTransform()
			
			// Track if we've cleared the depth buffer yet
			var depthCleared = false
			
			// Helper to get the right render pass (clear depth on first call)
			fun getScreenPass(label: String): RenderPass? {
				val pass = RegionRenderer.createScreenRenderPassWithDepth(label, clearDepth = !depthCleared)
				depthCleared = true
				return pass
			}

			// Render Screen Models
			val modelRenderers = renderers.filter { it.hasScreenModelData() }
			if (modelRenderers.isNotEmpty()) {
				RendererUtils.ensureGlintTextureLoaded()
				
				// Create dedicated glint uniform slice (scale 8.0 for vanilla GUI parity)
				val glintUniform = RendererUtils.createGlintUniform(8.0f)
				
				getScreenPass("$name Screen Models")?.use { pass ->
					pass.setPipeline(RendererUtils.getModelPipeline(depthTest = true))
					RenderSystem.bindDefaultUniforms(pass)
					
					// Use global screen dynamic transform for geometry position
					pass.setUniform("DynamicTransforms", dynamicTransform)
					// Use dedicated glint uniform for animation
					pass.setUniform("GlintTransforms", glintUniform)
					
					// Bind overlay and lightmap textures for item effects
					RendererUtils.bindOverlayTexture(pass, "Sampler1")
					RendererUtils.bindLightmapTexture(pass, "Sampler2")
					// Bind glint texture for enchantment shimmer
					RendererUtils.bindGlintTexture(pass, "Sampler3")
					
					modelRenderers.forEach { it.renderScreenModels(pass) }
				}
			}

			// Render Screen Faces
			getScreenPass("$name Screen Faces")?.use { pass ->
				pass.setPipeline(RendererUtils.getScreenFacesPipeline(depthTest = true))
				RenderSystem.bindDefaultUniforms(pass)
				pass.setUniform("DynamicTransforms", dynamicTransform)
				renderers.forEach { it.renderScreenFaces(pass) }
			}

			// Render Screen Edges
			getScreenPass("$name Screen Edges")?.use { pass ->
				pass.setPipeline(RendererUtils.getScreenEdgesPipeline(depthTest = true))
				RenderSystem.bindDefaultUniforms(pass)
				pass.setUniform("DynamicTransforms", dynamicTransform)
				renderers.forEach { it.renderScreenEdges(pass) }
			}

			// Render Screen Text - style params are now embedded in vertex attributes
			val textRenderers = renderers.filter { it.hasScreenTextData() }
			val atlas = currentFontAtlas
			if (atlas != null && textRenderers.isNotEmpty()) {
				if (!atlas.isUploaded) atlas.upload()
				val textureView = atlas.textureView
				val sampler = atlas.sampler
				if (textureView != null && sampler != null) {
					getScreenPass("$name Screen Text")?.use { pass ->
						pass.setPipeline(RendererUtils.getScreenTextPipeline(depthTest = true))
						RenderSystem.bindDefaultUniforms(pass)
						pass.setUniform("DynamicTransforms", dynamicTransform)
						pass.bindTexture("Sampler0", textureView, sampler)
						textRenderers.forEach { it.renderScreenText(pass) }
					}
				}
			}

			// Render Screen Images - needs separate transform with glint matrix for animation
			val imageRenderers = renderers.filter { it.hasScreenImageData() }
			if (imageRenderers.isNotEmpty()) {
				// Pre-load glint texture BEFORE creating render pass to avoid command conflicts
				RendererUtils.ensureGlintTextureLoaded()
				
				// Create a fresh dynamic transform with glint matrix calculated NOW (not at build time)
				val glintTransform = RendererUtils.createScreenDynamicTransformWithGlint()
				
				getScreenPass("$name Screen Images")?.use { pass ->
					pass.setPipeline(RendererUtils.getScreenImagePipeline(depthTest = true))
					RenderSystem.bindDefaultUniforms(pass)
					pass.setUniform("DynamicTransforms", glintTransform)
					
					// Bind enchantment glint texture for overlay support
					RendererUtils.bindGlintTexture(pass, "Sampler1")
					
					// Each renderer handles its own texture batches
					imageRenderers.forEach { it.renderScreenImages(pass) }
				}
			}
		}
	}
}
