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

import com.lambda.Lambda.mc
import com.lambda.graphics.RenderMain.projModel
import com.lambda.graphics.mc.LambdaRenderPipelines
import com.lambda.graphics.texture.LambdaImageAtlas
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.ProjectionType
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import net.minecraft.client.gl.GpuSampler
import net.minecraft.client.render.ProjectionMatrix2
import net.minecraft.util.Identifier
import net.minecraft.util.Util.getMeasuringTimeMs
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.sqrt

@Suppress("unused")
object RendererUtils {
	private val screenProjectionMatrix = ProjectionMatrix2("lambda_screen", -1000f, 1000f, false)

	val worldFacesPipeline: RenderPipeline get() = LambdaRenderPipelines.WorldQuads
	val worldLinesPipeline: RenderPipeline get() = LambdaRenderPipelines.WorldLines
	val worldTextPipeline: RenderPipeline get() = LambdaRenderPipelines.WorldSdfText
	val worldImagePipeline: RenderPipeline get() = LambdaRenderPipelines.WorldImages
	val worldModelPipeline: RenderPipeline get() = LambdaRenderPipelines.WorldModels

	val outlineFacesPipeline: RenderPipeline get() = LambdaRenderPipelines.OutlineQuads
	val outlineEdgesPipeline: RenderPipeline get() = LambdaRenderPipelines.OutlineLines
	val outlineTextPipeline: RenderPipeline get() = LambdaRenderPipelines.OutlineSdfText
	val outlineImagePipeline: RenderPipeline get() = LambdaRenderPipelines.OutlineImages
	val outlineModelPipeline: RenderPipeline get() = LambdaRenderPipelines.WorldModels

	val screenFacesPipeline: RenderPipeline get() = LambdaRenderPipelines.ScreenQuads
	val screenLinesPipeline: RenderPipeline get() = LambdaRenderPipelines.ScreenLines
	val screenTextPipeline: RenderPipeline get() = LambdaRenderPipelines.ScreenText
	val screenImagePipeline: RenderPipeline get() = LambdaRenderPipelines.ScreenImages

	private var glintTextureView: GpuTextureView? = null
	private var glintSampler: GpuSampler? = null
	private var glintTextureLoaded = false

	private var xrayDepthTexture: GpuTexture? = null
	private var xrayDepthView: GpuTextureView? = null
	private var xrayDepthWidth = 0
	private var xrayDepthHeight = 0

	private var screenDepthTexture: GpuTexture? = null
	private var screenDepthView: GpuTextureView? = null
	private var screenDepthWidth = 0
	private var screenDepthHeight = 0

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

	fun createGlintTransform(scale: Float): Matrix4f {
		val glintSpeed = mc.options?.glintSpeed?.value ?: 0.5
		val time = (getMeasuringTimeMs() * glintSpeed * 8.0).toLong()

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
		val time = (getMeasuringTimeMs() * glintSpeed * 8.0).toLong()

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

	fun ensureGlintTextureLoaded() {
		LambdaImageAtlas.processPendingLoads()
		
		if (!glintTextureLoaded) {
			val textureManager = mc.textureManager
			val glintId = Identifier.ofVanilla("textures/misc/enchanted_glint_item.png")
			val texture = textureManager.getTexture(glintId)
			glintTextureView = texture?.glTextureView
			glintTextureLoaded = true
		}

		glintSampler = RenderSystem.getSamplerCache().getRepeated(FilterMode.LINEAR)
	}

	fun bindGlintTexture(pass: com.mojang.blaze3d.systems.RenderPass, samplerName: String) {
		val view = glintTextureView ?: return
		val sampler = glintSampler ?: return
		pass.bindTexture(samplerName, view, sampler)
	}

	fun bindOverlayTexture(pass: com.mojang.blaze3d.systems.RenderPass, samplerName: String) {
		val overlay = mc.gameRenderer.overlayTexture ?: return
		val view = overlay.textureView ?: return
		val sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR)
		pass.bindTexture(samplerName, view, sampler)
	}

	fun bindLightmapTexture(pass: com.mojang.blaze3d.systems.RenderPass, samplerName: String) {
		val lightmap = mc.gameRenderer.lightmapTextureManager ?: return
		val view = lightmap.glTextureView ?: return
		val sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR)
		pass.bindTexture(samplerName, view, sampler)
	}

	fun getXrayDepthView(): GpuTextureView? {
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
				TextureFormat.DEPTH32,
				width,
				height,
				1,
				1
			)
			xrayDepthView = gpuDevice.createTextureView(xrayDepthTexture)
			xrayDepthWidth = width
			xrayDepthHeight = height

			RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(
					{ "Lambda Clear Xray Depth" },
					framebuffer.colorAttachmentView,
					java.util.OptionalInt.empty(),
					xrayDepthView,
					java.util.OptionalDouble.of(1.0)
				)?.close()
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

	fun getScreenDepthView(): GpuTextureView? {
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
				TextureFormat.DEPTH32,
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

	fun worldToScreenNormalized(worldPos: Vec3d): Vector2f? {
		val camera = mc.gameRenderer?.camera ?: return null
		val cameraPos = camera.pos

		val relX = (worldPos.x - cameraPos.x).toFloat()
		val relY = (worldPos.y - cameraPos.y).toFloat()
		val relZ = (worldPos.z - cameraPos.z).toFloat()

		val vec = Vector4f(relX, relY, relZ, 1f)
		projModel.transform(vec)

		val isBehind = vec.w < 0
		val w = if (abs(vec.w) < 0.001f) 0.001f else abs(vec.w)

		var ndcX = vec.x / w
		var ndcY = vec.y / w

		if (isBehind) {
			val len = sqrt(ndcX * ndcX + ndcY * ndcY)
			if (len > 0.0001f) {
				ndcX = (ndcX / len) * 3f
				ndcY = (ndcY / len) * 3f
			} else ndcY = -3f
		}

		val normalizedX = (ndcX + 1f) * 0.5f
		val normalizedY = (ndcY + 1f) * 0.5f

		return Vector2f(normalizedX, normalizedY)
	}

	fun isOnScreen(worldPos: Vec3d): Boolean {
		val camera = mc.gameRenderer?.camera ?: return false
		val cameraPos = camera.pos

		val relX = (worldPos.x - cameraPos.x).toFloat()
		val relY = (worldPos.y - cameraPos.y).toFloat()
		val relZ = (worldPos.z - cameraPos.z).toFloat()
		val vec = Vector4f(relX, relY, relZ, 1f)
		projModel.transform(vec)
		if (vec.w <= 0) return false

		val pos = worldToScreenNormalized(worldPos) ?: return false
		return pos.x in 0f..1f && pos.y in 0f..1f
	}
}

fun GpuBuffer.upload(data: ByteBuffer) =
	RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.slice(), data)
