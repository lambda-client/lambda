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

import com.lambda.core.Loadable
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gl.UniformType
import net.minecraft.client.render.VertexFormats
import net.minecraft.util.Identifier

object LambdaRenderPipelines : Loadable {
	/**
	 * Simple pipeline for blitting one FBO to another with full-screen quad.
	 * Used for layered composition.
	 */
	val COLOR_BLIT: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/color_blit"))
				.withVertexShader(Identifier.ofVanilla("core/position_tex"))
				.withFragmentShader(Identifier.ofVanilla("core/position_tex"))
				.withSampler("Sampler0")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.QUADS)
				.build()
		)

	override val priority: Int
		get() = 100 // High priority to ensure pipelines are ready early

	/**
	 * Base snippet for Lambda ESP rendering. Includes transforms, projection, and a custom
	 * per-region uniform.
	 */
	private val LAMBDA_ESP_SNIPPET =
		RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET).buildSnippet()

	/**
	 * Pipeline for ESP lines/outlines.
	 * - Uses MC's line rendering with per-vertex line width
	 * - No depth write for overlapping
	 * - No culling
	 */
	val ESP_LINES: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/esp_lines"))
				.withVertexShader(Identifier.of("lambda", "core/world_lines"))
				.withFragmentShader(Identifier.of("lambda", "core/world_lines"))
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Fog", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH_DASH,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	/** Pipeline for ESP lines that render through walls. */
	val ESP_LINES_THROUGH: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/esp_lines_through"))
				.withVertexShader(Identifier.of("lambda", "core/world_lines"))
				.withFragmentShader(Identifier.of("lambda", "core/world_lines"))
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Fog", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH_DASH,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	/** Pipeline for text that renders through walls. */
	/**
	 * Pipeline for quad-based ESP (compatible with existing shape building). Uses QUADS draw mode
	 * which MC converts to triangles internally.
	 */
	val ESP_QUADS: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/esp_quads"))
				.withVertexShader(Identifier.of("lambda", "core/world_faces"))
				.withFragmentShader(Identifier.of("lambda", "core/world_faces"))
				.withUniform("Fog", UniformType.UNIFORM_BUFFER)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
				.build()
		)

	/** Pipeline for quad-based ESP that renders through walls. */
	val ESP_QUADS_THROUGH: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/esp_quads_through"))
				.withVertexShader(Identifier.of("lambda", "core/world_faces"))
				.withFragmentShader(Identifier.of("lambda", "core/world_faces"))
				.withUniform("Fog", UniformType.UNIFORM_BUFFER)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
				.build()
		)

	/**
	 * Pipeline for SDF text rendering with proper smoothstep anti-aliasing.
	 * Uses lambda:core/sdf_text shaders with per-vertex style parameters.
	 */
	val SDF_TEXT: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/sdf_text"))
				.withVertexShader(Identifier.of("lambda", "core/world_sdf_text"))
				.withFragmentShader(Identifier.of("lambda", "core/world_sdf_text"))
				.withUniform("Fog", UniformType.UNIFORM_BUFFER)
				.withSampler("Sampler0")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(LambdaVertexFormats.POSITION_TEXTURE_COLOR_ANCHOR_SDF, VertexFormat.DrawMode.QUADS)
				.build()
		)

	/** SDF text pipeline that renders through walls. */
	val SDF_TEXT_THROUGH: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/sdf_text_through"))
				.withVertexShader(Identifier.of("lambda", "core/world_sdf_text"))
				.withFragmentShader(Identifier.of("lambda", "core/world_sdf_text"))
				.withUniform("Fog", UniformType.UNIFORM_BUFFER)
				.withSampler("Sampler0")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(LambdaVertexFormats.POSITION_TEXTURE_COLOR_ANCHOR_SDF, VertexFormat.DrawMode.QUADS)
				.build()
		)

	// ============================================================================
	// Screen-Space Pipelines (with layer-based depth for draw order)
	// ============================================================================

	/**
	 * Pipeline for screen-space faces/quads.
	 * Uses custom shader with layer support for draw order preservation.
	 */
	val SCREEN_FACES: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/screen_faces"))
				.withVertexShader(Identifier.of("lambda", "core/screen_faces"))
				.withFragmentShader(Identifier.of("lambda", "core/screen_faces"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)  // Enable depth write for layer ordering
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)  // Enable depth test
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.SCREEN_FACE_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	/**
	 * Pipeline for screen-space lines.
	 * Uses a custom vertex format with 2D direction for perpendicular offset calculation.
	 * Includes layer support for draw order preservation.
	 */
	val SCREEN_LINES: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/screen_lines"))
				.withVertexShader(Identifier.of("lambda", "core/screen_lines"))
				.withFragmentShader(Identifier.of("lambda", "core/screen_lines"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)  // Enable depth write for layer ordering
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)  // Depth test for layer ordering
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.SCREEN_LINE_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	/**
	 * Pipeline for screen-space SDF text rendering.
	 * Uses custom SDF shader with per-vertex style parameters for anti-aliased text with effects.
	 * Includes layer support for draw order preservation.
	 */
	val SCREEN_TEXT: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/screen_text"))
				.withVertexShader(Identifier.of("lambda", "core/screen_sdf_text"))
				.withFragmentShader(Identifier.of("lambda", "core/screen_sdf_text"))
				.withSampler("Sampler0")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)  // Enable depth write for layer ordering
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)  // Enable depth test
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.SCREEN_TEXT_SDF_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	// ============================================================================
	// Image Rendering Pipelines (with glint overlay support)
	// ============================================================================

	/**
	 * Pipeline for screen-space image rendering with overlay support.
	 * Uses two samplers: Sampler0 for main texture, Sampler1 for overlay (glint).
	 */
	val SCREEN_IMAGE: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/screen_image"))
				.withVertexShader(Identifier.of("lambda", "core/screen_image"))
				.withFragmentShader(Identifier.of("lambda", "core/screen_image"))
				.withSampler("Sampler0")
				.withSampler("Sampler1")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)  // Enable depth write for layer ordering
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.SCREEN_IMAGE_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	/**
	 * Pipeline for world-space billboard image rendering with overlay support.
	 * Uses anchor-based positioning with optional billboarding.
	 */
	val WORLD_IMAGE: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/world_image"))
				.withVertexShader(Identifier.of("lambda", "core/world_image"))
				.withFragmentShader(Identifier.of("lambda", "core/world_image"))
				.withUniform("Fog", UniformType.UNIFORM_BUFFER)
				.withSampler("Sampler0")
				.withSampler("Sampler1")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false) // No depth write for proper transparency blending
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.WORLD_IMAGE_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	/**
	 * Pipeline for world-space billboard image rendering that renders through walls.
	 */
	val WORLD_IMAGE_THROUGH: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/world_image_through"))
				.withVertexShader(Identifier.of("lambda", "core/world_image"))
				.withFragmentShader(Identifier.of("lambda", "core/world_image"))
				.withUniform("Fog", UniformType.UNIFORM_BUFFER)
				.withSampler("Sampler0")
				.withSampler("Sampler1")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.WORLD_IMAGE_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	/**
	 * Pipeline for world-space 3D model rendering.
	 * Supports Position, Color, UV0 (Atlas), OverlayUV (Overlay), UV2 (Lightmap), Normal.
	 */
	val WORLD_MODEL: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/world_model"))
				.withVertexShader(Identifier.of("lambda", "core/world_model"))
				.withFragmentShader(Identifier.of("lambda", "core/world_model"))
				.withSampler("Sampler0") // Atlas
				.withSampler("Sampler1") // Overlay
				.withSampler("Sampler2") // Lightmap
				.withSampler("Sampler3") // Glint
				.withUniform("GlintTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Fog", UniformType.UNIFORM_BUFFER)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.WORLD_MODEL_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	/**
	 * Pipeline for world-space 3D model rendering that renders through walls.
	 */
	val WORLD_MODEL_THROUGH: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/world_model_through"))
				.withVertexShader(Identifier.of("lambda", "core/world_model"))
				.withFragmentShader(Identifier.of("lambda", "core/world_model"))
				.withSampler("Sampler0")
				.withSampler("Sampler1")
				.withSampler("Sampler2")
				.withSampler("Sampler3") // Glint
				.withUniform("GlintTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Fog", UniformType.UNIFORM_BUFFER)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.WORLD_MODEL_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	// ============================================================================
	// Outline Rendering Pipelines (FBO-based silhouette + edge detection)
	// ============================================================================

	/**
	 * Pipeline for rendering entity silhouettes to the outline FBO.
	 * Uses flat color output for edge detection.
	 */
	val OUTLINE_SILHOUETTE: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/outline_silhouette"))
				.withVertexShader(Identifier.of("lambda", "core/outline_silhouette"))
				.withFragmentShader(Identifier.of("lambda", "core/outline_silhouette"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false) // Don't need depth for silhouette
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST) // No self-occlusion
				.withCull(false)
				.withVertexFormat(
					VertexFormats.POSITION_COLOR,
					VertexFormat.DrawMode.TRIANGLES
				)
				.build()
		)

	/**
	 * Pipeline for Sobel edge detection and blending back to main framebuffer.
	 * Renders fullscreen quad with edge detection shader.
	 */
	val OUTLINE_SOBEL: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/outline_sobel"))
				.withVertexShader(Identifier.of("lambda", "core/outline_sobel"))
				.withFragmentShader(Identifier.of("lambda", "core/outline_sobel"))
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withSampler("Sampler0") // Silhouette/Group texture
				.withSampler("Sampler1") // Silhouette Depth Buffer
				.withSampler("Sampler2") // MC Depth Buffer
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					VertexFormats.POSITION_TEXTURE,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	/**
	 * Pipeline for rendering entity IDs and ESP colors to the ID buffer.
	 * Uses POSITION_TEXTURE_COLOR format to separate UVs (for alpha testing)
	 * from the actual displayed ESP color.
	 */
	val OUTLINE_ID: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/outline_id"))
				.withVertexShader(Identifier.of("lambda", "core/outline_id"))
				.withFragmentShader(Identifier.of("lambda", "core/outline_id"))
				.withSampler("Sampler0")
				.withoutBlend() // No blending - exact ID values
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.OUTLINE_ID_FORMAT,
					VertexFormat.DrawMode.TRIANGLES
				)
				.build()
		)

	/**
	 * Pipeline for rendering entity IDs through walls.
	 */
	val OUTLINE_ID_THROUGH: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/outline_id_through"))
				.withVertexShader(Identifier.of("lambda", "core/outline_id"))
				.withFragmentShader(Identifier.of("lambda", "core/outline_id"))
				.withSampler("Sampler0")
				.withoutBlend()
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.OUTLINE_ID_FORMAT,
					VertexFormat.DrawMode.TRIANGLES
				)
				.build()
		)
}
