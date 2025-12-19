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
	 * Pipeline for static ESP faces (filled quads).
	 * - Translucent blending for see-through effect
	 * - No depth write to allow overlapping
	 * - No culling to see all faces
	 * - Uses TRIANGLES mode for maximum flexibility
	 */
	val ESP_FACES: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/esp_faces"))
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

	/**
	 * Pipeline for static ESP faces that render through walls.
	 * - Same as ESP_FACES but with no depth test
	 */
	val ESP_FACES_THROUGH: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/esp_faces_through"))
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
	 * Pipeline for ESP lines/outlines.
	 * - Uses MC's line rendering with per-vertex line width
	 * - No depth write for overlapping
	 * - No culling
	 */
	val ESP_LINES: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/esp_lines"))
				.withVertexShader(Identifier.ofVanilla("core/rendertype_lines"))
				.withFragmentShader(Identifier.ofVanilla("core/rendertype_lines"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH,
					VertexFormat.DrawMode.LINES
				)
				.build()
		)

	/** Pipeline for ESP lines that render through walls. */
	val ESP_LINES_THROUGH: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/esp_lines_through"))
				.withVertexShader(Identifier.ofVanilla("core/rendertype_lines"))
				.withFragmentShader(Identifier.ofVanilla("core/rendertype_lines"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH,
					VertexFormat.DrawMode.LINES
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
}
