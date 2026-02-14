

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

	override val priority get() = 100

	private val LAMBDA_ESP_SNIPPET = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET).buildSnippet()

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

	val SCREEN_FACES: RenderPipeline =
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

	val SCREEN_IMAGE: RenderPipeline =
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
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					LambdaVertexFormats.WORLD_IMAGE_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

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

	val WORLD_MODEL: RenderPipeline =
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

	val WORLD_MODEL_THROUGH: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/world_model_through"))
				.withVertexShader(Identifier.of("lambda", "core/world_model"))
				.withFragmentShader(Identifier.of("lambda", "core/world_model"))
				.withSampler("Sampler0")
				.withSampler("Sampler1")
				.withSampler("Sampler2")
				.withSampler("Sampler3")
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

	val OUTLINE_SILHOUETTE: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
				.withLocation(Identifier.of("lambda", "pipeline/outline_silhouette"))
				.withVertexShader(Identifier.of("lambda", "core/outline_silhouette"))
				.withFragmentShader(Identifier.of("lambda", "core/outline_silhouette"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					VertexFormats.POSITION_COLOR,
					VertexFormat.DrawMode.TRIANGLES
				)
				.build()
		)

	val OUTLINE_SOBEL: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET)
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

	val OUTLINE_ID: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(LAMBDA_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
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
