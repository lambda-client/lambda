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
import com.lambda.event.events.HudRenderEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.graphics.RenderMain
import com.lambda.graphics.mc.LambdaRenderPipelines
import com.lambda.graphics.mc.RegionRenderer
import com.lambda.graphics.text.SDFFontAtlas
import com.lambda.graphics.texture.LambdaImageAtlas
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.ProjectionType
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.ProjectionMatrix2
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * Shared utilities for ESP renderers.
 * Contains common rendering setup code used by ImmediateRenderer, TickedRenderer, and ChunkedRenderer.
 */
object RendererUtils {
	// Shared projection matrix for screen-space rendering
	// invertY=false means Y=0 at bottom, Y=height at top (OpenGL/math convention)
	private val screenProjectionMatrix = ProjectionMatrix2("lambda_screen", -1000f, 1000f, false)
	
	// Vanilla inventory lighting vectors (Studio Lighting)
	// These are from net.minecraft.client.render.DiffuseLighting and flipped to be "towards light"
	val INVENTORY_LIGHT_0 = Vector3f(-0.2f, 1.0f, -1.0f).normalize()
	val INVENTORY_LIGHT_1 = Vector3f(0.2f, 1.0f, 0.0f).normalize()



	/**
	 * Create a dynamic transform uniform with identity matrices for screen-space rendering.
	 */
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

	/**
	 * Create a dynamic transform with glint texture matrix for screen-space IMAGE rendering.
	 * Note: We use a smaller scale (0.125) compared to vanilla (8.0) because images use 
	 * normalized 0-1 UVs. 0.125 results in the same ~1/8 visual density.
	 */
	fun createScreenDynamicTransformWithGlint(): GpuBufferSlice {
		val identityMatrix = Matrix4f()
		return RenderSystem.getDynamicUniforms()
			.write(
				identityMatrix,
				Vector4f(1f, 1f, 1f, 1f),
				Vector3f(0f, 0f, 0f),
				createGlintTransform(0.125f)  // Normalized scale for [0, 1] UV parity
			)
	}

	/**
	 * Create a dynamic transform with glint texture matrix for screen-space MODEL rendering.
	 * Uses vanilla's 8.0 scale since model UVs are texture-based, not normalized.
	 */
	fun createScreenModelDynamicTransformWithGlint(): GpuBufferSlice {
		val identityMatrix = Matrix4f()
		return RenderSystem.getDynamicUniforms()
			.write(
				identityMatrix,
				Vector4f(1f, 1f, 1f, 1f),
				Vector3f(0f, 0f, 0f),
				createGlintTransform(8.0f)  // Vanilla glint scale parity
			)
	}

	/**
	 * Create a glint transformation matrix exactly like Minecraft's TextureTransform.getGlintTransformation().
	 * This is called every frame to get smooth animation.
	 * 
	 * @param scale The UV scale factor (8.0 for items, 0.5 for entities, 0.16 for armor)
	 */
	fun createGlintTransform(scale: Float): Matrix4f {
		// Exactly replicate Minecraft's TextureTransform.getGlintTransformation()
		val glintSpeed = mc.options?.glintSpeed?.value ?: 0.5
		val time = (net.minecraft.util.Util.getMeasuringTimeMs() * glintSpeed * 8.0).toLong()
		
		// Calculate scroll offsets (0-1 range)
		val scrollX = (time % 110000L) / 110000.0f    // 110s
		val scrollY = (time % 30000L) / 30000.0f     // 30s
		
		// Build matrix: Translation -> Rotation -> Scale (T * R * S)
		// This order ensures the translation/scrolling is NOT multiplied by the scale
		val matrix = Matrix4f()
		matrix.translation(-scrollX, scrollY, 0f)
		matrix.rotateZ((Math.PI / 18.0).toFloat()) // 10 degrees (Vanilla)
		matrix.scale(scale)
		
		return matrix
	}

	/**
	 * Create a dynamic uniform slice containing two glint transformation matrices.
	 * Pack Mat2 into the ModelView slot and Mat1 into the GlintMat slot of the GlintTransforms block.
	 */
	fun createGlintUniform(scale: Float): GpuBufferSlice {
		val glintSpeed = mc.options?.glintSpeed?.value ?: 0.5
		val time = (net.minecraft.util.Util.getMeasuringTimeMs() * glintSpeed * 8.0).toLong()
		
		// Up-Left (Vanilla parity in Y-up space)
		val scroll1X = (time % 110000L) / 110000.0f
		val scroll1Y = (time % 30000L) / 30000.0f
		val mat1 = Matrix4f().translation(-scroll1X, scroll1Y, 0f)
			.rotateZ((Math.PI / 18.0).toFloat()) // Exactly 10 deg (Vanilla)
			.scale(scale)

		return RenderSystem.getDynamicUniforms()
			.write(
				Matrix4f(),              // ModelView slot (Identity)
				Vector4f(1f, 1f, 1f, 1f), // Color
				Vector3f(0f, 0f, 0f),      // Offset
				mat1                     // GlintMat slot
			)
	}

	/**
	 * Create a dynamic transform with glint texture matrix for world-space MODEL rendering.
	 * Uses RenderMain.modelViewMatrix with translation zeroed for correct camera-relative positioning.
	 * Uses vanilla's 8.0 scale since model UVs are texture-based, not normalized.
	 */
	fun createWorldDynamicTransformWithGlint(): GpuBufferSlice {
		// Use modelViewMatrix with translation zeroed out (same pattern as ImmediateRenderer)
		val modelViewMatrix = RenderMain.modelViewMatrix
		val modelView = Matrix4f(modelViewMatrix).m30(0f).m31(0f).m32(0f)
		
		return RenderSystem.getDynamicUniforms()
			.write(
				modelView,
				Vector4f(1f, 1f, 1f, 1f),
				Vector3f(0f, 0f, 0f),
				createGlintTransform(8.0f)  // Vanilla glint scale parity
			)
	}

	/**
	 * Create a dynamic transform for a specific chunk position with glint texture matrix.
	 * Used for world-space model rendering where items need enchantment glint animation.
	 * Uses vanilla's 8.0 scale since model UVs are texture-based, not normalized.
	 *
	 * @param chunkOffset The chunk's position offset (usually cameraRelative)
	 */
	fun createChunkTransformWithGlint(chunkOffset: Vector3f): GpuBufferSlice {
		return RenderSystem.getDynamicUniforms()
			.write(
				RenderSystem.getModelViewMatrix(),
				Vector4f(1f, 1f, 1f, 1f),
				chunkOffset,
				createGlintTransform(8.0f)  // Vanilla glint scale parity
			)
	}

	/**
	 * Execute a block with screen-space rendering context.
	 * Sets up orthographic projection and identity model-view, then restores state after.
	 *
	 * @param block The rendering code to execute in screen-space context
	 */
	fun withScreenContext(block: () -> Unit) {
		val window = mc.window ?: return
		val width = window.scaledWidth.toFloat()
		val height = window.scaledHeight.toFloat()

		// Backup current projection matrix
		RenderSystem.backupProjectionMatrix()

		// Use orthographic projection matrix
		screenProjectionMatrix.set(width, height).let { slice ->
			RenderSystem.setProjectionMatrix(slice, ProjectionType.ORTHOGRAPHIC)
		}

		// Identity model-view for screen-space
		RenderSystem.getModelViewStack().pushMatrix().identity()

		try {
			block()
		} finally {
			// Restore matrices
			RenderSystem.getModelViewStack().popMatrix()
			RenderSystem.restoreProjectionMatrix()
		}
	}

	// ============================================================================
	// Pipeline Helpers
	// ============================================================================

	/**
	 * Get the face/quad pipeline.
	 * Always uses depth testing. Xray effect is achieved by using Lambda's
	 * custom depth buffer (which doesn't contain MC world geometry).
	 */
	fun getFacesPipeline(depthTest: Boolean): RenderPipeline = LambdaRenderPipelines.ESP_QUADS

	/**
	 * Get the edge/line pipeline.
	 * Always uses depth testing for proper self-ordering.
	 */
	fun getEdgesPipeline(depthTest: Boolean): RenderPipeline = LambdaRenderPipelines.ESP_LINES

	/**
	 * Get the SDF text pipeline.
	 * Always uses depth testing for proper self-ordering.
	 */
	fun getTextPipeline(depthTest: Boolean): RenderPipeline = LambdaRenderPipelines.SDF_TEXT

	/** Get the screen-space faces pipeline. */
	fun getScreenFacesPipeline(depthTest: Boolean = true): RenderPipeline = LambdaRenderPipelines.SCREEN_FACES
	
	/** Screen-space faces pipeline (legacy, use getScreenFacesPipeline instead). */
	val screenFacesPipeline: RenderPipeline get() = LambdaRenderPipelines.SCREEN_FACES

	/** Get the screen-space edges pipeline. */
	fun getScreenEdgesPipeline(depthTest: Boolean = true): RenderPipeline = LambdaRenderPipelines.SCREEN_LINES
	
	/** Screen-space edges pipeline (legacy, use getScreenEdgesPipeline instead). */
	val screenEdgesPipeline: RenderPipeline get() = LambdaRenderPipelines.SCREEN_LINES

	/** Get the screen-space text pipeline. */
	fun getScreenTextPipeline(depthTest: Boolean = true): RenderPipeline = LambdaRenderPipelines.SCREEN_TEXT
	
	/** Screen-space text pipeline (legacy, use getScreenTextPipeline instead). */
	val screenTextPipeline: RenderPipeline get() = LambdaRenderPipelines.SCREEN_TEXT

	/** Get the screen-space image pipeline. */
	fun getScreenImagePipeline(depthTest: Boolean = true): RenderPipeline = LambdaRenderPipelines.SCREEN_IMAGE

	/**
	 * Get the world-space image pipeline.
	 * Always uses depth testing for proper self-ordering.
	 */
	fun getWorldImagePipeline(depthTest: Boolean): RenderPipeline = LambdaRenderPipelines.WORLD_IMAGE

	/**
	 * Get the world-space model pipeline.
	 * Always uses depth testing for proper self-ordering.
	 */
	fun getModelPipeline(depthTest: Boolean): RenderPipeline = 
		if (depthTest) LambdaRenderPipelines.WORLD_MODEL else LambdaRenderPipelines.SCREEN_MODEL

	// Cached glint texture view and sampler
	private var glintTextureView: com.mojang.blaze3d.textures.GpuTextureView? = null
	private var glintSampler: net.minecraft.client.gl.GpuSampler? = null
	private var glintTextureLoaded = false

	/**
	 * Pre-load the glint texture and process any pending texture loads.
	 * Must be called BEFORE creating a render pass.
	 * This avoids the "close the existing render pass" error.
	 */
	fun ensureGlintTextureLoaded() {
		// Process any pending texture loads from background threads
		LambdaImageAtlas.processPendingLoads()
		
		if (!glintTextureLoaded) {
			val textureManager = mc.textureManager
			val glintId = net.minecraft.util.Identifier.ofVanilla("textures/misc/enchanted_glint_item.png")
			val texture = textureManager.getTexture(glintId)
			glintTextureView = texture?.glTextureView
			glintTextureLoaded = true
		}
		
		// Always ensure sampler is using REPEAT mode
		glintSampler = RenderSystem.getSamplerCache().getRepeated(com.mojang.blaze3d.textures.FilterMode.LINEAR)
	}

	/**
	 * Bind the Minecraft enchanted item glint texture to a sampler slot.
	 * Must call ensureGlintTextureLoaded() BEFORE starting the render pass.
	 *
	 * @param pass The render pass to bind the texture to
	 * @param samplerName The sampler name to bind to (e.g., "Sampler1")
	 */
	fun bindGlintTexture(pass: com.mojang.blaze3d.systems.RenderPass, samplerName: String) {
		val view = glintTextureView ?: return
		val sampler = glintSampler ?: return
		pass.bindTexture(samplerName, view, sampler)
	}

	/**
	 * Bind the generic Overlay texture (white/hurt flash) to a sampler slot.
	 */
	fun bindOverlayTexture(pass: com.mojang.blaze3d.systems.RenderPass, samplerName: String) {
		val overlay = mc.gameRenderer.overlayTexture ?: return
		val view = overlay.textureView ?: return
		// Overlay texture usually uses LINEAR filtering
		val sampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.LINEAR)
		pass.bindTexture(samplerName, view, sampler)
	}

	/**
	 * Bind the Lightmap texture to a sampler slot.
	 */
	fun bindLightmapTexture(pass: com.mojang.blaze3d.systems.RenderPass, samplerName: String) {
		val lightmap = mc.gameRenderer.lightmapTextureManager ?: return
		val view = lightmap.glTextureView ?: return
		// Lightmap uses LINEAR filtering
		val sampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.LINEAR)
		pass.bindTexture(samplerName, view, sampler)
	}

	// ============================================================================
	// Custom Depth Buffer for Xray Rendering
	// ============================================================================

	// Custom depth buffer for Lambda's xray rendering.
	// This allows proper depth ordering among our own renders while ignoring MC's world.
	private var xrayDepthTexture: com.mojang.blaze3d.textures.GpuTexture? = null
	private var xrayDepthView: com.mojang.blaze3d.textures.GpuTextureView? = null
	private var xrayDepthWidth = 0
	private var xrayDepthHeight = 0

	/**
	 * Get the xray depth buffer view, creating/resizing if necessary.
	 * This depth buffer is separate from MC's main depth buffer, allowing
	 * our renders to show through MC's world while still having correct
	 * depth ordering among themselves.
	 *
	 * @return The depth buffer view, or null if framebuffer is not available
	 */
	fun getXrayDepthView(): com.mojang.blaze3d.textures.GpuTextureView? {
		val framebuffer = mc.framebuffer ?: return null
		val width = framebuffer.textureWidth
		val height = framebuffer.textureHeight

		// Recreate if size changed or doesn't exist
		if (xrayDepthTexture == null || xrayDepthWidth != width || xrayDepthHeight != height) {
			// Clean up old resources
			xrayDepthView?.close()
			xrayDepthTexture?.close()

			// Create new depth texture matching framebuffer size
			val gpuDevice = RenderSystem.getDevice()
			xrayDepthTexture = gpuDevice.createTexture(
				{ "Lambda Xray Depth Buffer" },
				15, // Usage flags (same as MC's depth buffers)
				com.mojang.blaze3d.textures.TextureFormat.DEPTH32,
				width,
				height,
				1, // Layers
				1  // Mip levels
			)
			xrayDepthView = gpuDevice.createTextureView(xrayDepthTexture)
			xrayDepthWidth = width
			xrayDepthHeight = height
		}

		return xrayDepthView
	}

	/**
	 * Clear the xray depth buffer to prepare for a new render sequence.
	 * Should be called once at the start of each frame's xray rendering.
	 */
	fun clearXrayDepthBuffer() {
		val depthView = getXrayDepthView() ?: return
		val framebuffer = mc.framebuffer ?: return

		// Create a render pass that just clears the depth buffer
		// We pass OptionalDouble.of(1.0) to clear depth to far plane (1.0)
		RenderSystem.getDevice()
			.createCommandEncoder()
			.createRenderPass(
				{ "Lambda Clear Xray Depth" },
				framebuffer.colorAttachmentView,
				java.util.OptionalInt.empty(), // Don't clear color
				depthView,
				java.util.OptionalDouble.of(1.0) // Clear depth to 1.0 (far)
			)?.close() // Immediately close to execute the clear
	}
}

