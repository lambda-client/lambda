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
				.withVertexShader(Identifier.of("lambda", "core/advanced_lines"))
				.withFragmentShader(Identifier.of("lambda", "core/advanced_lines"))
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
				.withVertexShader(Identifier.of("lambda", "core/advanced_lines"))
				.withFragmentShader(Identifier.of("lambda", "core/advanced_lines"))
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

	/**
	 * Pipeline for quad-based ESP (compatible with existing shape building). Uses QUADS draw mode
	 * which MC converts to triangles internally.
	 */
	val ESP_QUADS: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/esp_quads"))
				.withVertexShader(Identifier.ofVanilla("core/position_color"))
				.withFragmentShader(Identifier.ofVanilla("core/position_color"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					VertexFormats.POSITION_COLOR,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	/** Pipeline for quad-based ESP that renders through walls. */
	val ESP_QUADS_THROUGH: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/esp_quads_through"))
				.withVertexShader(Identifier.ofVanilla("core/position_color"))
				.withFragmentShader(Identifier.ofVanilla("core/position_color"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					VertexFormats.POSITION_COLOR,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	/**
	 * Pipeline for textured text rendering with alpha blending.
	 * Uses position_tex_color shader with Sampler0 for font atlas texture.
	 */
	val TEXT_QUADS: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/text_quads"))
				.withVertexShader(Identifier.ofVanilla("core/position_tex_color"))
				.withFragmentShader(Identifier.ofVanilla("core/position_tex_color"))
				.withSampler("Sampler0")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					VertexFormats.POSITION_TEXTURE_COLOR,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	/** Pipeline for text that renders through walls. */
	val TEXT_QUADS_THROUGH: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/text_quads_through"))
				.withVertexShader(Identifier.ofVanilla("core/position_tex_color"))
				.withFragmentShader(Identifier.ofVanilla("core/position_tex_color"))
				.withSampler("Sampler0")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					VertexFormats.POSITION_TEXTURE_COLOR,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	/**
	 * Pipeline for SDF text rendering with proper smoothstep anti-aliasing.
	 * Uses lambda:core/sdf_text shaders with per-vertex style parameters.
	 */
	val SDF_TEXT: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/sdf_text"))
				.withVertexShader(Identifier.of("lambda", "core/sdf_text"))
				.withFragmentShader(Identifier.of("lambda", "core/sdf_text"))
				.withSampler("Sampler0")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.POSITION_TEXTURE_COLOR_ANCHOR_SDF,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	/** SDF text pipeline that renders through walls. */
	val SDF_TEXT_THROUGH: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/sdf_text_through"))
				.withVertexShader(Identifier.of("lambda", "core/sdf_text"))
				.withFragmentShader(Identifier.of("lambda", "core/sdf_text"))
				.withSampler("Sampler0")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.POSITION_TEXTURE_COLOR_ANCHOR_SDF,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	// ============================================================================
	// Screen-Space Pipelines
	// ============================================================================

	/**
	 * Pipeline for screen-space lines.
	 * Uses a custom vertex format with 2D direction for perpendicular offset calculation.
	 */
	val SCREEN_LINES: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/screen_lines"))
				.withVertexShader(Identifier.of("lambda", "core/screen_lines"))
				.withFragmentShader(Identifier.of("lambda", "core/screen_lines"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
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
	 */
	val SCREEN_TEXT: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/screen_text"))
				.withVertexShader(Identifier.of("lambda", "core/screen_sdf_text"))
				.withFragmentShader(Identifier.of("lambda", "core/screen_sdf_text"))
				.withSampler("Sampler0")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.SCREEN_TEXT_SDF_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)
}

