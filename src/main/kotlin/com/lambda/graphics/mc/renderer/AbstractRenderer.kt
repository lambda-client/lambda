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
import com.lambda.graphics.outline.OutlineManager
import com.lambda.graphics.outline.OutlineRenderer
import com.lambda.graphics.outline.OutlineStyle
import com.lambda.graphics.text.SDFFontAtlas
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import kotlin.collections.isNotEmpty

abstract class AbstractRenderer(val name: String, var depthTest: SafeContext.() -> Boolean) {
	protected abstract fun getRendererTransforms(): List<Pair<RegionRenderer, GpuBufferSlice>>
	
	protected abstract fun getScreenRenderers(): List<RegionRenderer>

	protected abstract val currentFontAtlas: SDFFontAtlas?

	fun SafeContext.render() {
		val chunks = getRendererTransforms()
		if (chunks.isEmpty()) return

		val depth = depthTest()

		RegionRenderer.createRenderPass("$name Faces", depth)?.use { pass ->
			pass.setPipeline(RendererUtils.facesPipeline)
			RenderSystem.bindDefaultUniforms(pass)
			chunks.forEach { (renderer, transform) ->
				pass.setUniform("DynamicTransforms", transform)
				renderer.renderFaces(pass)
			}
		}

		RegionRenderer.createRenderPass("$name Edges", depth)?.use { pass ->
			pass.setPipeline(RendererUtils.edgesPipeline)
			RenderSystem.bindDefaultUniforms(pass)
			chunks.forEach { (renderer, transform) ->
				pass.setUniform("DynamicTransforms", transform)
				renderer.renderEdges(pass)
			}
		}

		val textChunks = chunks.filter { (renderer, _) -> renderer.hasTextData() }
		val atlas = currentFontAtlas
		if (atlas != null && textChunks.isNotEmpty()) {
			if (!atlas.isUploaded) atlas.upload()
			val textureView = atlas.textureView
			val sampler = atlas.sampler
			if (textureView != null && sampler != null) {
				RegionRenderer.createRenderPass("$name Text", depth)?.use { pass ->
					pass.setPipeline(RendererUtils.textPipeline)
					RenderSystem.bindDefaultUniforms(pass)
					pass.bindTexture("Sampler0", textureView, sampler)
					textChunks.forEach { (renderer, transform) ->
						pass.setUniform("DynamicTransforms", transform)
						renderer.renderText(pass)
					}
				}
			}
		}

		val imageChunks = chunks.filter { (renderer, _) -> renderer.hasWorldImageData() }
		if (imageChunks.isNotEmpty()) {
			RendererUtils.ensureGlintTextureLoaded()
			
			RegionRenderer.createRenderPass("$name World Images", depth)?.use { pass ->
				pass.setPipeline(RendererUtils.worldImagePipeline)
				RenderSystem.bindDefaultUniforms(pass)

				RendererUtils.bindGlintTexture(pass, "Sampler1")
				
				imageChunks.forEach { (renderer, transform) ->
					pass.setUniform("DynamicTransforms", transform)
					renderer.renderWorldImages(pass)
				}
			}
		}

		val modelChunks = chunks.filter { (renderer, _) -> renderer.hasModelData() }
		if (modelChunks.isNotEmpty()) {
			RendererUtils.ensureGlintTextureLoaded()
			
			val glintUniform = RendererUtils.createGlintUniform(8.0f)
			
			RegionRenderer.createRenderPass("$name World Models", depth)?.use { pass ->
				pass.setPipeline(RendererUtils.modelPipeline)
				RenderSystem.bindDefaultUniforms(pass)

				RendererUtils.bindOverlayTexture(pass, "Sampler1")
				RendererUtils.bindLightmapTexture(pass, "Sampler2")
				RendererUtils.bindGlintTexture(pass, "Sampler3")

				pass.setUniform("GlintTransforms", glintUniform)
				
				modelChunks.forEach { (renderer, transform) ->
					pass.setUniform("DynamicTransforms", transform)
					renderer.renderModels(pass)
				}
			}
		}

		val outlinedIds = chunks.flatMap { it.first.getOutlineIds() }.toSet()
		if (outlinedIds.isNotEmpty()) {
			val nearestSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.NEAREST)

			RendererUtils.ensureGlintTextureLoaded()
			val glintUniformL = RendererUtils.createGlintUniform(8.0f)

			val depth = depthTest()
			outlinedIds.forEach { id ->
				val style = OutlineManager.getOutlineStyle(id) ?: OutlineStyle.DEFAULT

				RegionRenderer.createRenderPass("$name Outlined Draw $id", depth)?.use { pass ->
					pass.setPipeline(RendererUtils.facesPipeline)
					RenderSystem.bindDefaultUniforms(pass)
					chunks.forEach { (renderer, transform) ->
						if (renderer.hasOutlinedData(id)) {
							pass.setUniform("DynamicTransforms", transform)
							renderer.renderOutlinedFaces(pass, id)
						}
					}

					pass.setPipeline(RendererUtils.edgesPipeline)
					RenderSystem.bindDefaultUniforms(pass)
					chunks.forEach { (renderer, transform) ->
						if (renderer.hasOutlinedData(id)) {
							pass.setUniform("DynamicTransforms", transform)
							renderer.renderOutlinedEdges(pass, id)
						}
					}

					val atlasD = currentFontAtlas
					if (atlasD != null && atlasD.textureView != null) {
						pass.setPipeline(RendererUtils.textPipeline)
						RenderSystem.bindDefaultUniforms(pass)
						pass.bindTexture("Sampler0", atlasD.textureView!!, atlasD.sampler ?: nearestSampler)
						chunks.forEach { (renderer, transform) ->
							if (renderer.hasOutlinedData(id)) {
								pass.setUniform("DynamicTransforms", transform)
								renderer.renderOutlinedText(pass, id)
							}
						}
					}

					pass.setPipeline(RendererUtils.worldImagePipeline)
					RenderSystem.bindDefaultUniforms(pass)
					RendererUtils.bindGlintTexture(pass, "Sampler1")
					chunks.forEach { (renderer, transform) ->
						if (renderer.hasOutlinedData(id)) {
							pass.setUniform("DynamicTransforms", transform)
							renderer.renderOutlinedImages(pass, id)
						}
					}

					pass.setPipeline(RendererUtils.modelPipeline)
					RenderSystem.bindDefaultUniforms(pass)
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

				OutlineRenderer.beginGroupPass("$name Group $id")
				val groupTarget = OutlineRenderer.getGroupView() ?: return@forEach
				val groupDepthView = OutlineRenderer.getGroupDepthView() ?: return@forEach

				RenderSystem.getDevice()
					.createCommandEncoder()
					.createRenderPass(
						{ "$name Outline Group $id - Draw" },
						groupTarget,
						java.util.OptionalInt.empty(),
						groupDepthView,
						java.util.OptionalDouble.empty()
					)?.use { pass ->
						pass.setPipeline(RendererUtils.facesPipeline)
						RenderSystem.bindDefaultUniforms(pass)
						chunks.forEach { (renderer, transform) ->
							if (renderer.hasOutlinedData(id)) {
								pass.setUniform("DynamicTransforms", transform)
								renderer.renderOutlinedFaces(pass, id)
							}
						}

						pass.setPipeline(RendererUtils.edgesPipeline)
						RenderSystem.bindDefaultUniforms(pass)
						chunks.forEach { (renderer, transform) ->
							if (renderer.hasOutlinedData(id)) {
								pass.setUniform("DynamicTransforms", transform)
								renderer.renderOutlinedEdges(pass, id)
							}
						}

						val atlasT = currentFontAtlas
						if (atlasT != null && atlasT.textureView != null) {
							pass.setPipeline(RendererUtils.textPipeline)
							RenderSystem.bindDefaultUniforms(pass)
							pass.bindTexture("Sampler0", atlasT.textureView!!, atlasT.sampler ?: nearestSampler)
							chunks.forEach { (renderer, transform) ->
								if (renderer.hasOutlinedData(id)) {
									pass.setUniform("DynamicTransforms", transform)
									renderer.renderOutlinedText(pass, id)
								}
							}
						}

						pass.setPipeline(RendererUtils.worldImagePipeline)
						RenderSystem.bindDefaultUniforms(pass)
						RendererUtils.bindGlintTexture(pass, "Sampler1")
						chunks.forEach { (renderer, transform) ->
							if (renderer.hasOutlinedData(id)) {
								pass.setUniform("DynamicTransforms", transform)
								renderer.renderOutlinedImages(pass, id)
							}
						}

						pass.setPipeline(RendererUtils.modelPipeline)
						RenderSystem.bindDefaultUniforms(pass)
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

					OutlineRenderer.endGroupPass(style, depth)
				}
			}
	}

	fun renderScreen() {
		val renderers = getScreenRenderers()

		RendererUtils.withScreenContext {
			val dynamicTransform = RendererUtils.createScreenDynamicTransform()
			
			RendererUtils.clearScreenDepthBuffer()

			fun getScreenPass(label: String): RenderPass? =
				RegionRenderer.createScreenRenderPassWithDepth(label, clearDepth = false)

			val modelRenderers = renderers.filter { it.hasScreenModelData() }
			if (modelRenderers.isNotEmpty()) {
				RendererUtils.ensureGlintTextureLoaded()
				
				val glintUniform = RendererUtils.createGlintUniform(8.0f)
				
				getScreenPass("$name Screen Models")?.use { pass ->
					pass.setPipeline(RendererUtils.modelPipeline)
					RenderSystem.bindDefaultUniforms(pass)

					pass.setUniform("DynamicTransforms", dynamicTransform)
					pass.setUniform("GlintTransforms", glintUniform)

					RendererUtils.bindOverlayTexture(pass, "Sampler1")
					RendererUtils.bindLightmapTexture(pass, "Sampler2")
					RendererUtils.bindGlintTexture(pass, "Sampler3")
					
					modelRenderers.forEach { it.renderScreenModels(pass) }
				}
			}

			getScreenPass("$name Screen Faces")?.use { pass ->
				pass.setPipeline(RendererUtils.screenFacesPipeline)
				RenderSystem.bindDefaultUniforms(pass)
				pass.setUniform("DynamicTransforms", dynamicTransform)
				renderers.forEach { it.renderScreenFaces(pass) }
			}

			getScreenPass("$name Screen Edges")?.use { pass ->
				pass.setPipeline(RendererUtils.screenEdgesPipeline)
				RenderSystem.bindDefaultUniforms(pass)
				pass.setUniform("DynamicTransforms", dynamicTransform)
				renderers.forEach { it.renderScreenEdges(pass) }
			}

			val textRenderers = renderers.filter { it.hasScreenTextData() }
			val atlas = currentFontAtlas
			if (atlas != null && textRenderers.isNotEmpty()) {
				if (!atlas.isUploaded) atlas.upload()
				val textureView = atlas.textureView
				val sampler = atlas.sampler
				if (textureView != null && sampler != null) {
					getScreenPass("$name Screen Text")?.use { pass ->
						pass.setPipeline(RendererUtils.screenTextPipeline)
						RenderSystem.bindDefaultUniforms(pass)
						pass.setUniform("DynamicTransforms", dynamicTransform)
						pass.bindTexture("Sampler0", textureView, sampler)
						textRenderers.forEach { it.renderScreenText(pass) }
					}
				}
			}

			val imageRenderers = renderers.filter { it.hasScreenImageData() }
			if (imageRenderers.isNotEmpty()) {
				RendererUtils.ensureGlintTextureLoaded()
				
				val glintTransform = RendererUtils.createScreenDynamicTransformWithGlint()
				
				getScreenPass("$name Screen Images")?.use { pass ->
					pass.setPipeline(RendererUtils.screenImagePipeline)
					RenderSystem.bindDefaultUniforms(pass)
					pass.setUniform("DynamicTransforms", glintTransform)

					RendererUtils.bindGlintTexture(pass, "Sampler1")
					
					imageRenderers.forEach { it.renderScreenImages(pass) }
				}
			}
		}
	}
}
