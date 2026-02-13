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
import com.lambda.graphics.mc.LambdaRenderPipelines
import com.lambda.graphics.mc.RegionRenderer
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.outline.OutlineManager
import com.lambda.graphics.outline.OutlineStyle
import com.lambda.graphics.text.SDFFontAtlas
import com.lambda.graphics.texture.TextureOwner.upload
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
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
	 * 
	 * All world-space renderers share the same xray depth buffer (cleared once per frame in RenderMain).
	 * - depthTest = true: Uses MC's depth buffer (respects world geometry)
	 * - depthTest = false: Uses Lambda's xray depth buffer (ignores world, self-ordering only)
	 */
	fun SafeContext.render() {
		val chunks = getRendererTransforms()
		if (chunks.isEmpty()) return

		val depth = depthTest()

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

			// ============================================================================
			// Isolated Outline Groups Pass (Iterative Actual + Silhouette + Sobel)
			// ============================================================================
			val outlinedIds = chunks.flatMap { it.first.getOutlineIds() }.toSet()
			if (outlinedIds.isNotEmpty()) {
				val framebuffer = mc.framebuffer ?: return
				val nearestSampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.NEAREST)

				// Pre-load glint texture once before iterative passes to avoid IllegalStateException
				com.lambda.graphics.mc.renderer.RendererUtils.ensureGlintTextureLoaded()
				val glintUniformL = com.lambda.graphics.mc.renderer.RendererUtils.createGlintUniform(8.0f)

				outlinedIds.forEach { id ->
					val style = OutlineManager.getOutlineStyle(id) ?: OutlineStyle.DEFAULT
					val depthView = if (depthTest()) framebuffer.depthAttachmentView else RendererUtils.getXrayDepthView()
					if (depthView == null) return@forEach

					// 1. Prepare isolated Group FBO
					com.lambda.graphics.outline.OutlineRenderer.beginGroupPass("$name Group $id", depthView)
					val groupTarget = com.lambda.graphics.outline.OutlineRenderer.getGroupView() ?: return@forEach

					// 2. Render ACTUAL geometry using REGULAR pipelines into the group FBO
					com.mojang.blaze3d.systems.RenderSystem.getDevice()
						.createCommandEncoder()
						.createRenderPass(
							{ "$name Outline Group $id - Draw" },
							groupTarget,
							java.util.OptionalInt.empty(), // Already cleared in beginGroupPass
							depthView,
							java.util.OptionalDouble.empty()
						)?.use { pass ->
							// Faces
							pass.setPipeline(RendererUtils.getFacesPipeline(depthTest()))
							com.mojang.blaze3d.systems.RenderSystem.bindDefaultUniforms(pass)
							chunks.forEach { (renderer, transform) ->
								if (renderer.hasOutlinedData(id)) {
									pass.setUniform("DynamicTransforms", transform)
									renderer.renderOutlinedFaces(pass, id)
								}
							}

							// Edges
							pass.setPipeline(RendererUtils.getEdgesPipeline(depthTest()))
							com.mojang.blaze3d.systems.RenderSystem.bindDefaultUniforms(pass)
							chunks.forEach { (renderer, transform) ->
								if (renderer.hasOutlinedData(id)) {
									pass.setUniform("DynamicTransforms", transform)
									renderer.renderOutlinedEdges(pass, id)
								}
							}

							// Text
							val atlasT = currentFontAtlas
							if (atlasT != null && atlasT.textureView != null) {
								pass.setPipeline(RendererUtils.getTextPipeline(depthTest()))
								com.mojang.blaze3d.systems.RenderSystem.bindDefaultUniforms(pass)
								pass.bindTexture("Sampler0", atlasT.textureView!!, atlasT.sampler ?: nearestSampler)
								chunks.forEach { (renderer, transform) ->
									if (renderer.hasOutlinedData(id)) {
										pass.setUniform("DynamicTransforms", transform)
										renderer.renderOutlinedText(pass, id)
									}
								}
							}

							pass.setPipeline(RendererUtils.getWorldImagePipeline(depthTest()))
							com.mojang.blaze3d.systems.RenderSystem.bindDefaultUniforms(pass)
							RendererUtils.bindGlintTexture(pass, "Sampler1")
							chunks.forEach { (renderer, transform) ->
								if (renderer.hasOutlinedData(id)) {
									pass.setUniform("DynamicTransforms", transform)
									renderer.renderOutlinedImages(pass, id)
								}
							}

							pass.setPipeline(RendererUtils.getModelPipeline(depthTest()))
							com.mojang.blaze3d.systems.RenderSystem.bindDefaultUniforms(pass)
							RendererUtils.bindOverlayTexture(pass, "Sampler1")
							RendererUtils.bindLightmapTexture(pass, "Sampler2")
							RendererUtils.bindGlintTexture(pass, "Sampler3")
							pass.setUniform("GlintTransforms", glintUniformL)
							chunks.forEach { (renderer, transform) ->
								if (renderer.hasOutlinedData(id)) {
									pass.setUniform("DynamicTransforms", transform)
									renderer.renderOutlinedModels(pass, id)
								}
							}
						}

					// 3. Apply Sobel and composite back to main framebuffer
					com.lambda.graphics.outline.OutlineRenderer.endGroupPass(style)
				}
			}

		// Entity outlines are now consolidated and triggered from RenderMain after all modules have registered.
	}

	/**
	 * Render screen-space geometry. Uses orthographic projection for 2D rendering.
	 * Uses a SEPARATE screen depth buffer for complete isolation from world-space.
	 * This should be called after world-space render() for proper layering.
	 * 
	 * Each renderer clears the screen depth buffer at the start to ensure
	 * complete isolation from other renderers.
	 */
	fun renderScreen() {
		val renderers = getScreenRenderers()

		RendererUtils.withScreenContext {
			val dynamicTransform = RendererUtils.createScreenDynamicTransform()
			
			// Clear the SCREEN depth buffer (separate from world xray depth)
			// This ensures complete isolation between renderers
			RendererUtils.clearScreenDepthBuffer()
			
			// Helper to get the right render pass (depth already cleared at start)
			fun getScreenPass(label: String): RenderPass? {
				return RegionRenderer.createScreenRenderPassWithDepth(label, clearDepth = false)
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
