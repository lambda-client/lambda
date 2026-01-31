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
	 * Create a dynamic transform with glint texture matrix for animated enchantment effect.
	 * Used for screen image rendering with overlay support.
	 * Note: We use a smaller scale (0.25) compared to vanilla (8.0) because our UVs are 
	 * normalized 0-1, not model-based coordinates.
	 */
	fun createScreenDynamicTransformWithGlint(): GpuBufferSlice {
		val identityMatrix = Matrix4f()
		return RenderSystem.getDynamicUniforms()
			.write(
				identityMatrix,
				Vector4f(1f, 1f, 1f, 1f),
				Vector3f(0f, 0f, 0f),
				createGlintTransform(0.25f)  // Smaller scale for screen-space (larger pattern)
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
		val glintSpeed = mc.options?.glintSpeed?.value ?: 0.5  // Default to 0.5 if options not available
		val time = (net.minecraft.util.Util.getMeasuringTimeMs() * glintSpeed * 8.0).toLong()
		
		// Calculate scroll offsets (0-1 range)
		val scrollX = (time % 110000L) / 110000.0f  // X cycle: 110 seconds
		val scrollY = (time % 30000L) / 30000.0f    // Y cycle: 30 seconds
		
		// Build matrix: translation(-f, g, 0) -> rotateZ(π/18) -> scale
		val matrix = Matrix4f()
		matrix.translation(-scrollX, scrollY, 0f)
		matrix.rotateZ((Math.PI / 18.0).toFloat())  // 10 degrees
		matrix.scale(scale)
		
		return matrix
	}

	/**
	 * Create a dynamic transform with glint texture matrix for world-space image rendering.
	 * Uses the current model-view matrix from RenderSystem for proper camera alignment.
	 * Note: We use a smaller scale (0.25) compared to vanilla because our UVs are 
	 * normalized 0-1, not model-based coordinates.
	 */
	fun createWorldDynamicTransformWithGlint(): GpuBufferSlice {
		return RenderSystem.getDynamicUniforms()
			.write(
				RenderSystem.getModelViewMatrix(),
				Vector4f(1f, 1f, 1f, 1f),
				Vector3f(0f, 0f, 0f),
				createGlintTransform(0.25f)  // Smaller scale for normalized UVs (larger pattern)
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

	/** Screen-space faces pipeline (with layer-based depth for draw order). */
	val screenFacesPipeline: RenderPipeline get() = LambdaRenderPipelines.SCREEN_FACES

	/** Screen-space edges pipeline. */
	val screenEdgesPipeline: RenderPipeline get() = LambdaRenderPipelines.SCREEN_LINES

	/** Screen-space text pipeline. */
	val screenTextPipeline: RenderPipeline get() = LambdaRenderPipelines.SCREEN_TEXT

	/** Get the screen-space image pipeline. */
	fun getScreenImagePipeline(): RenderPipeline = LambdaRenderPipelines.SCREEN_IMAGE

	/**
	 * Get the world-space image pipeline.
	 * Always uses depth testing for proper self-ordering.
	 */
	fun getWorldImagePipeline(depthTest: Boolean): RenderPipeline = LambdaRenderPipelines.WORLD_IMAGE

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
			glintSampler = RenderSystem.getSamplerCache().get(com.mojang.blaze3d.textures.FilterMode.LINEAR)
			glintTextureLoaded = true
		}
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

	// ============================================================================
	// Deferred Item Rendering
	// ============================================================================

	/**
	 * Global queue of items to render during the next HUD render pass.
	 * Items are added via renderDeferredItems() and rendered when HudRenderEvent fires.
	 */
	private val pendingItems = mutableListOf<com.lambda.graphics.mc.RenderBuilder.ScreenItemRender>()

	/**
	 * Queue deferred ItemStack renders to be drawn during the HUD render pass.
	 * Items are rendered when Minecraft's HUD rendering occurs via HudRenderEvent.
	 *
	 * @param items List of ScreenItemRender to draw
	 */
	fun renderDeferredItems(items: List<com.lambda.graphics.mc.RenderBuilder.ScreenItemRender>) {
		if (items.isEmpty()) return
		pendingItems.addAll(items)
	}

	/**
	 * Render pending items using the provided DrawContext.
	 * Called by HudRenderEvent listener when Minecraft's HUD is being rendered.
	 *
	 * @param context The DrawContext from Minecraft's HUD rendering
	 */
	fun renderPendingItems(context: DrawContext) {
		if (pendingItems.isEmpty()) return
		
		val window = mc.window ?: return
		val textRenderer = mc.textRenderer ?: return
		
		val scaledWidth = window.scaledWidth
		val scaledHeight = window.scaledHeight
		
		// Standard Minecraft item size is 16x16 pixels
		val standardItemSize = 16f
		
		pendingItems.forEach { item ->
			// Use floating point for smooth sub-pixel positioning (prevents jitter from integer truncation)
			val pixelX = item.x * scaledWidth
			
			// Calculate scale based on normalized size using height-only (matches toPixelSize)
			// Size of 0.05 means 5% of screen height, so pixelSize = size * height
			val targetPixelSize = item.size * scaledHeight
			val scale = targetPixelSize / standardItemSize
			
			// Flip Y: our normalized coords use Y=0 at bottom, but DrawContext uses Y=0 at top
			// Also offset by item height so items grow UPWARD from the specified position
			// (DrawContext draws from top-left extending down, we want bottom-left extending up)
			val itemHeight = standardItemSize * scale
			val pixelY = (1f - item.y) * scaledHeight - itemHeight
			
			// Always use matrix translation for smooth sub-pixel positioning
			context.matrices.pushMatrix()
			context.matrices.translate(pixelX, pixelY)
			if (scale != 1f) {
				context.matrices.scale(scale, scale)
			}
			context.drawItem(item.stack, 0, 0)
			context.drawStackOverlay(textRenderer, item.stack, 0, 0)
			context.matrices.popMatrix()
		}
		
		// Clear the queue after rendering
		pendingItems.clear()
	}

	// Initialize HudRenderEvent listener
	init {
		listenUnsafe<HudRenderEvent> { event ->
			renderPendingItems(event.context)
		}
	}
}

