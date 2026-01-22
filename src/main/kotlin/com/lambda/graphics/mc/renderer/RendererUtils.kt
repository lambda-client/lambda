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

	/** Screen-space faces pipeline (with layer-based depth for draw order). */
	val screenFacesPipeline: RenderPipeline get() = LambdaRenderPipelines.SCREEN_FACES

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

