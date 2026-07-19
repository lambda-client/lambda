
package com.minato.graphics.mc

import com.minato.core.Loadable
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gl.UniformType
import net.minecraft.client.render.VertexFormats
import net.minecraft.util.Identifier

object MinatoRenderPipelines : Loadable {
	override val priority get() = 100

	private val MINATO_ESP_SNIPPET = RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET).buildSnippet()

	val WORLD_LINES: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(MINATO_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("minato", "pipeline/world_lines"))
				.withVertexShader(Identifier.of("minato", "core/world_lines"))
				.withFragmentShader(Identifier.of("minato", "core/world_lines"))
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					MinatoVertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH_DASH,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val WORLD_QUADS: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(MINATO_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("minato", "pipeline/world_quads"))
				.withVertexShader(Identifier.of("minato", "core/world_faces"))
				.withFragmentShader(Identifier.of("minato", "core/world_faces"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
				.build()
		)

	val WORLD_SDF_TEXT: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(MINATO_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("minato", "pipeline/sdf_text"))
				.withVertexShader(Identifier.of("minato", "core/world_sdf_text"))
				.withFragmentShader(Identifier.of("minato", "core/world_sdf_text"))
				.withSampler("Sampler0")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(MinatoVertexFormats.POSITION_TEXTURE_COLOR_ANCHOR_SDF, VertexFormat.DrawMode.QUADS)
				.build()
		)

	val WORLD_IMAGES: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(MINATO_ESP_SNIPPET)
				.withLocation(Identifier.of("minato", "pipeline/world_image"))
				.withVertexShader(Identifier.of("minato", "core/world_image"))
				.withFragmentShader(Identifier.of("minato", "core/world_image"))
				.withUniform("Fog", UniformType.UNIFORM_BUFFER)
				.withSampler("Sampler0")
				.withSampler("Sampler1")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(false)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					MinatoVertexFormats.WORLD_IMAGE_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val WORLD_MODELS: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(MINATO_ESP_SNIPPET)
				.withLocation(Identifier.of("minato", "pipeline/world_model"))
				.withVertexShader(Identifier.of("minato", "core/world_model"))
				.withFragmentShader(Identifier.of("minato", "core/world_model"))
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
					MinatoVertexFormats.WORLD_MODEL_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val OUTLINE_LINES: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(MINATO_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("minato", "pipeline/outline_lines"))
				.withVertexShader(Identifier.of("minato", "core/world_lines"))
				.withFragmentShader(Identifier.of("minato", "core/world_lines"))
				.withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
				.withUniform("Projection", UniformType.UNIFORM_BUFFER)
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					MinatoVertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH_DASH,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val OUTLINE_QUADS: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(MINATO_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("minato", "pipeline/outline_quads"))
				.withVertexShader(Identifier.of("minato", "core/world_faces"))
				.withFragmentShader(Identifier.of("minato", "core/world_faces"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
				.build()
		)

	val OUTLINE_SDF_TEXT: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(MINATO_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("minato", "pipeline/outline_sdf_text"))
				.withVertexShader(Identifier.of("minato", "core/world_sdf_text"))
				.withFragmentShader(Identifier.of("minato", "core/world_sdf_text"))
				.withSampler("Sampler0")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(MinatoVertexFormats.POSITION_TEXTURE_COLOR_ANCHOR_SDF, VertexFormat.DrawMode.QUADS)
				.build()
		)

	val OUTLINE_IMAGES: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(MINATO_ESP_SNIPPET)
				.withLocation(Identifier.of("minato", "pipeline/outline_world_image"))
				.withVertexShader(Identifier.of("minato", "core/world_image"))
				.withFragmentShader(Identifier.of("minato", "core/world_image"))
				.withUniform("Fog", UniformType.UNIFORM_BUFFER)
				.withSampler("Sampler0")
				.withSampler("Sampler1")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					MinatoVertexFormats.WORLD_IMAGE_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val SCREEN_LINES: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(MINATO_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("minato", "pipeline/screen_lines"))
				.withVertexShader(Identifier.of("minato", "core/screen_lines"))
				.withFragmentShader(Identifier.of("minato", "core/screen_lines"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					MinatoVertexFormats.SCREEN_LINE_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val SCREEN_QUADS: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(MINATO_ESP_SNIPPET)
				.withLocation(Identifier.of("minato", "pipeline/screen_faces"))
				.withVertexShader(Identifier.of("minato", "core/screen_faces"))
				.withFragmentShader(Identifier.of("minato", "core/screen_faces"))
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					MinatoVertexFormats.SCREEN_FACE_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val SCREEN_TEXT: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(MINATO_ESP_SNIPPET)
				.withLocation(Identifier.of("minato", "pipeline/screen_text"))
				.withVertexShader(Identifier.of("minato", "core/screen_sdf_text"))
				.withFragmentShader(Identifier.of("minato", "core/screen_sdf_text"))
				.withSampler("Sampler0")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					MinatoVertexFormats.SCREEN_TEXT_SDF_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val SCREEN_IMAGES: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(MINATO_ESP_SNIPPET)
				.withLocation(Identifier.of("minato", "pipeline/screen_image"))
				.withVertexShader(Identifier.of("minato", "core/screen_image"))
				.withFragmentShader(Identifier.of("minato", "core/screen_image"))
				.withSampler("Sampler0")
				.withSampler("Sampler1")
				.withBlend(BlendFunction.TRANSLUCENT)
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					MinatoVertexFormats.SCREEN_IMAGE_FORMAT,
					VertexFormat.DrawMode.QUADS
				)
				.build()
		)

	val OUTLINE_ID: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(MINATO_ESP_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
				.withLocation(Identifier.of("minato", "pipeline/outline_id"))
				.withVertexShader(Identifier.of("minato", "core/outline_id"))
				.withFragmentShader(Identifier.of("minato", "core/outline_id"))
				.withSampler("Sampler0")
				.withoutBlend()
				.withDepthWrite(true)
				.withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
				.withCull(false)
				.withVertexFormat(
					MinatoVertexFormats.OUTLINE_ID_FORMAT,
					VertexFormat.DrawMode.TRIANGLES
				)
				.build()
		)

	val OUTLINE_SOBEL: RenderPipeline =
		RenderPipelines.register(
			RenderPipeline.builder(MINATO_ESP_SNIPPET)
				.withLocation(Identifier.of("minato", "pipeline/outline_sobel"))
				.withVertexShader(Identifier.of("minato", "core/outline_sobel"))
				.withFragmentShader(Identifier.of("minato", "core/outline_sobel"))
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
