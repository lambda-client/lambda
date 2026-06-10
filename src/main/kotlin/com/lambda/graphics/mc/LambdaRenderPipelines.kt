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
	override val priority get() = 100

	private val LambdaEspSnippet = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET).buildSnippet()

	val WorldLines: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LambdaEspSnippet, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/world_lines"))
				.withVertexShader(Identifier.of("lambda", "core/world_lines"))
				.withFragmentShader(Identifier.of("lambda", "core/world_lines"))
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.PositionColorNormalLineWidthDash,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val WorldQuads: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LambdaEspSnippet, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/world_quads"))
				.withVertexShader(Identifier.of("lambda", "core/world_faces"))
				.withFragmentShader(Identifier.of("lambda", "core/world_faces"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
				.build()
		)

	val WorldSdfText: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LambdaEspSnippet, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/sdf_text"))
				.withVertexShader(Identifier.of("lambda", "core/world_sdf_text"))
				.withFragmentShader(Identifier.of("lambda", "core/world_sdf_text"))
				.withSampler("Sampler0")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(LambdaVertexFormats.PositionTextureColorAnchorSdf, VertexFormat.DrawMode.QUADS)
				.build()
		)

	val WorldImages: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LambdaEspSnippet)
				.withLocation(Identifier.of("lambda", "pipeline/world_image"))
				.withVertexShader(Identifier.of("lambda", "core/world_image"))
				.withFragmentShader(Identifier.of("lambda", "core/world_image"))
				.withUniform("Fog", UniformType.UNIFORM_BUFFER)
				.withSampler("Sampler0")
				.withSampler("Sampler1")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.WorldImageFormat,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val WorldModels: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LambdaEspSnippet)
				.withLocation(Identifier.of("lambda", "pipeline/world_model"))
				.withVertexShader(Identifier.of("lambda", "core/world_model"))
				.withFragmentShader(Identifier.of("lambda", "core/world_model"))
				.withSampler("Sampler0")
				.withSampler("Sampler1")
				.withSampler("Sampler2")
				.withSampler("Sampler3")
				.withUniform("GlintTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Fog", UniformType.UNIFORM_BUFFER)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.WorldModelFormat,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val OutlineLines: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LambdaEspSnippet, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/outline_lines"))
				.withVertexShader(Identifier.of("lambda", "core/world_lines"))
				.withFragmentShader(Identifier.of("lambda", "core/world_lines"))
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.PositionColorNormalLineWidthDash,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val OutlineQuads: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LambdaEspSnippet, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/outline_quads"))
				.withVertexShader(Identifier.of("lambda", "core/world_faces"))
				.withFragmentShader(Identifier.of("lambda", "core/world_faces"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
				.build()
		)

	val OutlineSdfText: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LambdaEspSnippet, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/outline_sdf_text"))
				.withVertexShader(Identifier.of("lambda", "core/world_sdf_text"))
				.withFragmentShader(Identifier.of("lambda", "core/world_sdf_text"))
				.withSampler("Sampler0")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(LambdaVertexFormats.PositionTextureColorAnchorSdf, VertexFormat.DrawMode.QUADS)
				.build()
		)

	val OutlineImages: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LambdaEspSnippet)
				.withLocation(Identifier.of("lambda", "pipeline/outline_world_image"))
				.withVertexShader(Identifier.of("lambda", "core/world_image"))
				.withFragmentShader(Identifier.of("lambda", "core/world_image"))
				.withUniform("Fog", UniformType.UNIFORM_BUFFER)
				.withSampler("Sampler0")
				.withSampler("Sampler1")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.WorldImageFormat,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val ScreenLines: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LambdaEspSnippet, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/screen_lines"))
				.withVertexShader(Identifier.of("lambda", "core/screen_lines"))
				.withFragmentShader(Identifier.of("lambda", "core/screen_lines"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.ScreenLineFormat,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val ScreenQuads: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LambdaEspSnippet)
				.withLocation(Identifier.of("lambda", "pipeline/screen_faces"))
				.withVertexShader(Identifier.of("lambda", "core/screen_faces"))
				.withFragmentShader(Identifier.of("lambda", "core/screen_faces"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.ScreenFaceFormat,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val ScreenText: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LambdaEspSnippet)
				.withLocation(Identifier.of("lambda", "pipeline/screen_text"))
				.withVertexShader(Identifier.of("lambda", "core/screen_sdf_text"))
				.withFragmentShader(Identifier.of("lambda", "core/screen_sdf_text"))
				.withSampler("Sampler0")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.ScreenTextSdfFormat,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val ScreenImages: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LambdaEspSnippet)
				.withLocation(Identifier.of("lambda", "pipeline/screen_image"))
				.withVertexShader(Identifier.of("lambda", "core/screen_image"))
				.withFragmentShader(Identifier.of("lambda", "core/screen_image"))
				.withSampler("Sampler0")
				.withSampler("Sampler1")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.ScreenImageFormat,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val OutlineId: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LambdaEspSnippet, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/outline_id"))
				.withVertexShader(Identifier.of("lambda", "core/outline_id"))
				.withFragmentShader(Identifier.of("lambda", "core/outline_id"))
				.withSampler("Sampler0")
				.withoutBlend()
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.OutlineIdFormat,
					VertexFormat.DrawMode.TRIANGLES
				)
				.build()
		)

	val OutlineSobel: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LambdaEspSnippet)
				.withLocation(Identifier.of("lambda", "pipeline/outline_sobel"))
				.withVertexShader(Identifier.of("lambda", "core/outline_sobel"))
				.withFragmentShader(Identifier.of("lambda", "core/outline_sobel"))
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withSampler("Sampler0")
				.withSampler("Sampler1")
				.withSampler("Sampler2")
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
}
