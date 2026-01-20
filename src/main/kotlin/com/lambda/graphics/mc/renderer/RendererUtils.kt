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
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.ProjectionType
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.render.ProjectionMatrix2
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.system.MemoryUtil

/**
 * Shared utilities for ESP renderers.
 * Contains common rendering setup code used by ImmediateRenderer, TickedRenderer, and ChunkedRenderer.
 */
object RendererUtils {
	// Shared projection matrix for screen-space rendering
	private val screenProjectionMatrix = ProjectionMatrix2("lambda_screen", -1000f, 1000f, true)

	/**
	 * Create SDF params uniform buffer with specified or default values.
	 * Used for SDF text rendering.
	 *
	 * @param outlineWidth Width of text outline in SDF units (0 = no outline)
	 * @param glowRadius Radius of glow effect in SDF units (0 = no glow)
	 * @param shadowSoftness Softness of shadow effect (0 = no shadow)
	 */
	fun createSDFParamsBuffer(
		outlineWidth: Float = 0f,
		glowRadius: Float = 0.2f,
		shadowSoftness: Float = 0.15f
	): GpuBuffer? {
		val device = RenderSystem.getDevice()
		val buffer = MemoryUtil.memAlloc(16)
		return try {
			buffer.putFloat(0.5f)           // SDFThreshold
			buffer.putFloat(outlineWidth)   // OutlineWidth
			buffer.putFloat(glowRadius)     // GlowRadius
			buffer.putFloat(shadowSoftness) // ShadowSoftness
			buffer.flip()
			device.createBuffer({ "SDFParams" }, GpuBuffer.USAGE_UNIFORM, buffer)
		} catch (_: Exception) {
			null
		} finally {
			MemoryUtil.memFree(buffer)
		}
	}

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

	/** Get the face/quad pipeline based on depth test setting. */
	fun getFacesPipeline(depthTest: Boolean): RenderPipeline =
		if (depthTest) LambdaRenderPipelines.ESP_QUADS
		else LambdaRenderPipelines.ESP_QUADS_THROUGH

	/** Get the edge/line pipeline based on depth test setting. */
	fun getEdgesPipeline(depthTest: Boolean): RenderPipeline =
		if (depthTest) LambdaRenderPipelines.ESP_LINES
		else LambdaRenderPipelines.ESP_LINES_THROUGH

	/** Get the SDF text pipeline based on depth test setting. */
	fun getTextPipeline(depthTest: Boolean): RenderPipeline =
		if (depthTest) LambdaRenderPipelines.SDF_TEXT
		else LambdaRenderPipelines.SDF_TEXT_THROUGH

	/** Screen-space faces pipeline (always no depth test). */
	val screenFacesPipeline: RenderPipeline get() = LambdaRenderPipelines.ESP_QUADS_THROUGH

	/** Screen-space edges pipeline. */
	val screenEdgesPipeline: RenderPipeline get() = LambdaRenderPipelines.SCREEN_LINES

	/** Screen-space text pipeline. */
	val screenTextPipeline: RenderPipeline get() = LambdaRenderPipelines.SCREEN_TEXT

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
	fun renderPendingItems(context: net.minecraft.client.gui.DrawContext) {
		if (pendingItems.isEmpty()) return
		
		val window = mc.window ?: return
		val textRenderer = mc.textRenderer ?: return
		
		val scaledWidth = window.scaledWidth
		val scaledHeight = window.scaledHeight
		
		// Standard Minecraft item size is 16x16 pixels
		val standardItemSize = 16f
		
		pendingItems.forEach { item ->
			val pixelX = (item.x * scaledWidth).toInt()
			val pixelY = (item.y * scaledHeight).toInt()
			
			// Calculate scale based on normalized size using average of dimensions (matches toPixelSize)
			// Size of 0.05 means ~5% of screen, so pixelSize = size * (width + height) / 2
			val targetPixelSize = item.size * (scaledWidth + scaledHeight) / 2f
			val scale = targetPixelSize / standardItemSize
			
			if (scale != 1f) {
				// For scaled items, we need to translate and scale the matrix
				// Matrix3x2fStack uses JOML methods directly
				context.matrices.pushMatrix()
				context.matrices.translate(pixelX.toFloat(), pixelY.toFloat())
				context.matrices.scale(scale, scale)
				context.drawItem(item.stack, 0, 0)
				context.drawStackOverlay(textRenderer, item.stack, 0, 0)
				context.matrices.popMatrix()
			} else {
				context.drawItem(item.stack, pixelX, pixelY)
				context.drawStackOverlay(textRenderer, item.stack, pixelX, pixelY)
			}
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

