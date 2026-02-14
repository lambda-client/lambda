

package com.lambda.graphics.mc.renderer

import com.lambda.Lambda.mc
import com.lambda.graphics.RenderMain
import com.lambda.graphics.mc.LambdaRenderPipelines
import com.lambda.graphics.texture.LambdaImageAtlas
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.ProjectionType
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.render.ProjectionMatrix2
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f

object RendererUtils {
	private val screenProjectionMatrix = ProjectionMatrix2("lambda_screen", -1000f, 1000f, false)

	fun createScreenDynamicTransform(): GpuBufferSlice {
		val identityMatrix = Matrix4f()
		return RenderSystem.getDynamicUniforms()
			.write(
				identityMatrix,
				Vector4f(1f, 1f, 1f, 1f),
				Vector3f(0f, 0f, 0f),
				identityMatrix
			)
	}

	fun createScreenDynamicTransformWithGlint(): GpuBufferSlice {
		val identityMatrix = Matrix4f()
		return RenderSystem.getDynamicUniforms()
			.write(
				identityMatrix,
				Vector4f(1f, 1f, 1f, 1f),
				Vector3f(0f, 0f, 0f),
				createGlintTransform(0.125f)
			)
	}

	fun createScreenModelDynamicTransformWithGlint(): GpuBufferSlice {
		val identityMatrix = Matrix4f()
		return RenderSystem.getDynamicUniforms()
			.write(
				identityMatrix,
				Vector4f(1f, 1f, 1f, 1f),
				Vector3f(0f, 0f, 0f),
				createGlintTransform(8.0f)
			)
	}

	fun createGlintTransform(scale: Float): Matrix4f {
		val glintSpeed = mc.options?.glintSpeed?.value ?: 0.5
		val time = (net.minecraft.util.Util.getMeasuringTimeMs() * glintSpeed * 8.0).toLong()

		val scrollX = (time % 110000L) / 110000.0f
		val scrollY = (time % 30000L) / 30000.0f

		val matrix = Matrix4f()
		matrix.translation(-scrollX, scrollY, 0f)
		matrix.rotateZ((Math.PI / 18.0).toFloat())
		matrix.scale(scale)
		
		return matrix
	}

	fun createGlintUniform(scale: Float): GpuBufferSlice {
		val glintSpeed = mc.options?.glintSpeed?.value ?: 0.5
		val time = (net.minecraft.util.Util.getMeasuringTimeMs() * glintSpeed * 8.0).toLong()

		val scroll1X = (time % 110000L) / 110000.0f
		val scroll1Y = (time % 30000L) / 30000.0f
		val mat1 = Matrix4f().translation(-scroll1X, scroll1Y, 0f)
			.rotateZ((Math.PI / 18.0).toFloat())
			.scale(scale)

		return RenderSystem.getDynamicUniforms()
			.write(
				Matrix4f(),
				Vector4f(1f, 1f, 1f, 1f),
				Vector3f(0f, 0f, 0f),
				mat1
			)
	}

	fun createWorldDynamicTransformWithGlint(): GpuBufferSlice {
		val modelViewMatrix = RenderMain.modelViewMatrix
		val modelView = Matrix4f(modelViewMatrix).m30(0f).m31(0f).m32(0f)
		
		return RenderSystem.getDynamicUniforms()
			.write(
				modelView,
				Vector4f(1f, 1f, 1f, 1f),
				Vector3f(0f, 0f, 0f),
				createGlintTransform(8.0f)
			)
	}

	fun createChunkTransformWithGlint(chunkOffset: Vector3f): GpuBufferSlice {
		return RenderSystem.getDynamicUniforms()
			.write(
				RenderSystem.getModelViewMatrix(),
				Vector4f(1f, 1f, 1f, 1f),
				chunkOffset,
				createGlintTransform(8.0f)
			)
	}

	fun withScreenContext(block: () -> Unit) {
		val window = mc.window ?: return
		val width = window.scaledWidth.toFloat()
		val height = window.scaledHeight.toFloat()

		RenderSystem.backupProjectionMatrix()

		screenProjectionMatrix.set(width, height).let { slice ->
			RenderSystem.setProjectionMatrix(slice, ProjectionType.ORTHOGRAPHIC)
		}

		RenderSystem.getModelViewStack().pushMatrix().identity()

		try {
			block()
		} finally {
			RenderSystem.getModelViewStack().popMatrix()
			RenderSystem.restoreProjectionMatrix()
		}
	}


	fun getFacesPipeline(depthTest: Boolean): RenderPipeline = 
		if (depthTest) LambdaRenderPipelines.ESP_QUADS else LambdaRenderPipelines.ESP_QUADS_THROUGH

	fun getEdgesPipeline(depthTest: Boolean): RenderPipeline = 
		if (depthTest) LambdaRenderPipelines.ESP_LINES else LambdaRenderPipelines.ESP_LINES_THROUGH

	fun getTextPipeline(depthTest: Boolean): RenderPipeline = 
		if (depthTest) LambdaRenderPipelines.SDF_TEXT else LambdaRenderPipelines.SDF_TEXT_THROUGH

	fun getWorldImagePipeline(depthTest: Boolean): RenderPipeline = 
		if (depthTest) LambdaRenderPipelines.WORLD_IMAGE else LambdaRenderPipelines.WORLD_IMAGE_THROUGH

	fun getModelPipeline(depthTest: Boolean): RenderPipeline = 
		if (depthTest) LambdaRenderPipelines.WORLD_MODEL else LambdaRenderPipelines.WORLD_MODEL_THROUGH

	fun getScreenFacesPipeline(): RenderPipeline = LambdaRenderPipelines.SCREEN_FACES
	fun getScreenEdgesPipeline(): RenderPipeline = LambdaRenderPipelines.SCREEN_LINES
	fun getScreenTextPipeline(): RenderPipeline = LambdaRenderPipelines.SCREEN_TEXT
	fun getScreenImagePipeline(): RenderPipeline = LambdaRenderPipelines.SCREEN_IMAGE

	private var glintTextureView: com.mojang.blaze3d.textures.GpuTextureView? = null
	private var glintSampler: net.minecraft.client.gl.GpuSampler? = null
	private var glintTextureLoaded = false

	fun ensureGlintTextureLoaded() {
		LambdaImageAtlas.processPendingLoads()
		
		if (!glintTextureLoaded) {
			val textureManager = mc.textureManager
			val glintId = net.minecraft.util.Identifier.ofVanilla("textures/misc/enchanted_glint_item.png")
			val texture = textureManager.getTexture(glintId)
			glintTextureView = texture?.glTextureView
			glintTextureLoaded = true
		}

		glintSampler = RenderSystem.getSamplerCache().getRepeated(com.mojang.blaze3d.textures.FilterMode.LINEAR)
	}

	fun bindGlintTexture(pass: com.mojang.blaze3d.systems.RenderPass, samplerName: String) {
		val view = glintTextureView ?: return
		val sampler = glintSampler ?: return
		pass.bindTexture(samplerName, view, sampler)
	}

	fun bindOverlayTexture(pass: com.mojang.blaze3d.systems.RenderPass, samplerName: String) {
		val overlay = mc.gameRenderer.overlayTexture ?: return
		val view = overlay.textureView ?: return
		val sampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.LINEAR)
		pass.bindTexture(samplerName, view, sampler)
	}

	fun bindLightmapTexture(pass: com.mojang.blaze3d.systems.RenderPass, samplerName: String) {
		val lightmap = mc.gameRenderer.lightmapTextureManager ?: return
		val view = lightmap.glTextureView ?: return
		val sampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.LINEAR)
		pass.bindTexture(samplerName, view, sampler)
	}


	private var xrayDepthTexture: com.mojang.blaze3d.textures.GpuTexture? = null
	private var xrayDepthView: com.mojang.blaze3d.textures.GpuTextureView? = null
	private var xrayDepthWidth = 0
	private var xrayDepthHeight = 0

	fun getXrayDepthView(): com.mojang.blaze3d.textures.GpuTextureView? {
		val framebuffer = mc.framebuffer ?: return null
		val width = framebuffer.textureWidth
		val height = framebuffer.textureHeight

		if (xrayDepthTexture == null || xrayDepthWidth != width || xrayDepthHeight != height) {
			xrayDepthView?.close()
			xrayDepthTexture?.close()

			val gpuDevice = RenderSystem.getDevice()
			xrayDepthTexture = gpuDevice.createTexture(
				{ "Lambda Xray Depth Buffer" },
				15,
				com.mojang.blaze3d.textures.TextureFormat.DEPTH32,
				width,
				height,
				1,
				1
			)
			xrayDepthView = gpuDevice.createTextureView(xrayDepthTexture)
			xrayDepthWidth = width
			xrayDepthHeight = height
		}

		return xrayDepthView
	}

	fun clearXrayDepthBuffer() {
		val depthView = getXrayDepthView() ?: return
		val framebuffer = mc.framebuffer ?: return

		RenderSystem.getDevice()
			.createCommandEncoder()
			.createRenderPass(
				{ "Lambda Clear Xray Depth" },
				framebuffer.colorAttachmentView,
				java.util.OptionalInt.empty(),
				depthView,
				java.util.OptionalDouble.of(1.0)
			)?.close()
	}


	private var screenDepthTexture: com.mojang.blaze3d.textures.GpuTexture? = null
	private var screenDepthView: com.mojang.blaze3d.textures.GpuTextureView? = null
	private var screenDepthWidth = 0
	private var screenDepthHeight = 0

	fun getScreenDepthView(): com.mojang.blaze3d.textures.GpuTextureView? {
		val framebuffer = mc.framebuffer ?: return null
		val width = framebuffer.textureWidth
		val height = framebuffer.textureHeight

		if (screenDepthTexture == null || screenDepthWidth != width || screenDepthHeight != height) {
			screenDepthView?.close()
			screenDepthTexture?.close()

			val gpuDevice = RenderSystem.getDevice()
			screenDepthTexture = gpuDevice.createTexture(
				{ "Lambda Screen Depth Buffer" },
				15,
				com.mojang.blaze3d.textures.TextureFormat.DEPTH32,
				width,
				height,
				1,
				1
			)
			screenDepthView = gpuDevice.createTextureView(screenDepthTexture)
			screenDepthWidth = width
			screenDepthHeight = height
		}

		return screenDepthView
	}

	fun clearScreenDepthBuffer() {
		val depthView = getScreenDepthView() ?: return
		val framebuffer = mc.framebuffer ?: return

		RenderSystem.getDevice()
			.createCommandEncoder()
			.createRenderPass(
				{ "Lambda Clear Screen Depth" },
				framebuffer.colorAttachmentView,
				java.util.OptionalInt.empty(),
				depthView,
				java.util.OptionalDouble.of(1.0)
			)?.close()
	}
}


fun com.mojang.blaze3d.buffers.GpuBuffer.upload(data: java.nio.ByteBuffer) =
	RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.slice(), data)
