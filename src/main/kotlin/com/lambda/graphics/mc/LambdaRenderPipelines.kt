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
import com.lambda.Lambda
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.shaders.ShaderType
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gl.UniformType
import net.minecraft.client.render.VertexFormats
import net.minecraft.util.Identifier
import java.io.IOException
import java.nio.charset.StandardCharsets

object LambdaRenderPipelines : Loadable {
	override val priority get() = 100

	private val LAMBDA_ESP_SNIPPET = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET).buildSnippet()

	val WORLD_LINES: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
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
					LambdaVertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH_DASH,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val WORLD_QUADS: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
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

	val WORLD_SDF_TEXT: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/sdf_text"))
				.withVertexShader(Identifier.of("lambda", "core/world_sdf_text"))
				.withFragmentShader(Identifier.of("lambda", "core/world_sdf_text"))
				.withSampler("Sampler0")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(LambdaVertexFormats.POSITION_TEXTURE_COLOR_ANCHOR_SDF, VertexFormat.DrawMode.QUADS)
				.build()
		)

	val WORLD_IMAGES: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
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
					LambdaVertexFormats.WORLD_IMAGE_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val WORLD_MODELS: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
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
					LambdaVertexFormats.WORLD_MODEL_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val OUTLINE_LINES: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
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
					LambdaVertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH_DASH,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val OUTLINE_QUADS: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
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

	val OUTLINE_SDF_TEXT: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/outline_sdf_text"))
				.withVertexShader(Identifier.of("lambda", "core/world_sdf_text"))
				.withFragmentShader(Identifier.of("lambda", "core/world_sdf_text"))
				.withSampler("Sampler0")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(LambdaVertexFormats.POSITION_TEXTURE_COLOR_ANCHOR_SDF, VertexFormat.DrawMode.QUADS)
				.build()
		)

	val OUTLINE_IMAGES: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
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
					LambdaVertexFormats.WORLD_IMAGE_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val SCREEN_LINES: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/screen_lines"))
				.withVertexShader(Identifier.of("lambda", "core/screen_lines"))
				.withFragmentShader(Identifier.of("lambda", "core/screen_lines"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.SCREEN_LINE_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val SCREEN_QUADS: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/screen_faces"))
				.withVertexShader(Identifier.of("lambda", "core/screen_faces"))
				.withFragmentShader(Identifier.of("lambda", "core/screen_faces"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.SCREEN_FACE_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val SCREEN_TEXT: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/screen_text"))
				.withVertexShader(Identifier.of("lambda", "core/screen_sdf_text"))
				.withFragmentShader(Identifier.of("lambda", "core/screen_sdf_text"))
				.withSampler("Sampler0")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.SCREEN_TEXT_SDF_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val SCREEN_IMAGES: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
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
					LambdaVertexFormats.SCREEN_IMAGE_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val OUTLINE_ID: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder()
				.withLocation(Identifier.of("lambda", "pipeline/outline_id"))
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

	private fun buildOutlinePostPipeline(
		location: String,
		fragmentShader: Identifier,
		uniforms: List<String> = emptyList(),
		samplers: List<String>,
		blend: Boolean = false
	): RenderPipeline {
		val builder = RenderPipeline.builder()
			.withLocation(Identifier.of("lambda", location))
			.withVertexShader(Identifier.of("lambda", "core/outline_sobel"))
			.withFragmentShader(fragmentShader)
			.withDepthWrite(false)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withCull(false)
			.withVertexFormat(VertexFormats.POSITION_TEXTURE, VertexFormat.DrawMode.QUADS)

		uniforms.forEach { builder.withUniform(it, UniformType.UNIFORM_BUFFER) }
		samplers.forEach(builder::withSampler)
		if (blend) builder.withBlend(BlendFunction.TRANSLUCENT) else builder.withoutBlend()
		return builder.build()
	}

	private fun outlinePostPipeline(
		location: String,
		fragmentShader: Identifier,
		uniforms: List<String> = emptyList(),
		samplers: List<String>,
		blend: Boolean = false
	): RenderPipeline = RenderPipelines.register(
		buildOutlinePostPipeline(location, fragmentShader, uniforms, samplers, blend)
	)

	val OUTLINE_GLOW: RenderPipeline = outlinePostPipeline(
		location = "pipeline/outline_glow",
		fragmentShader = Identifier.of("lambda", "core/outline_glow"),
		uniforms = listOf("GlowData"),
		samplers = listOf("Sampler0", "Sampler1", "Sampler2")
	)

	val OUTLINE_COMPOSITE: RenderPipeline = outlinePostPipeline(
		location = "pipeline/outline_composite",
		fragmentShader = Identifier.of("lambda", "core/outline_composite"),
		uniforms = listOf("PostData", "OutlineData"),
		samplers = listOf("Sampler0", "Sampler1", "Sampler2", "Sampler3"),
		blend = true
	)

	fun createCustomOutlineCompositePipeline(encodedName: String, versionToken: String, shaderIdentifier: Identifier): RenderPipeline =
		buildOutlinePostPipeline(
			location = "pipeline/outline_composite/$encodedName/$versionToken",
			fragmentShader = shaderIdentifier,
			uniforms = listOf("PostData", "OutlineData"),
			samplers = listOf("Sampler0", "Sampler1", "Sampler2", "Sampler3"),
			blend = true
		)

	@Throws(IOException::class)
	fun getShaderSource(identifier: Identifier): String {
		val resource = Lambda.mc.resourceManager.getResource(identifier).orElseThrow()
		resource.inputStream.use { input ->
			return input.readBytes().toString(StandardCharsets.UTF_8)
		}
	}

	@Throws(IOException::class)
	fun getShaderSource(identifier: Identifier, shaderType: ShaderType): String =
		getShaderSource(shaderType.idConverter().toResourcePath(identifier))
}
