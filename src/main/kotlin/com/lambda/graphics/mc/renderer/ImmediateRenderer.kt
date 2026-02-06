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

import com.lambda.context.SafeContext
import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.RenderMain
import com.lambda.graphics.mc.RegionRenderer
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.text.SDFFontAtlas
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderSystem
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * Interpolated ESP system for smooth entity rendering.
 *
 * This system rebuilds and uploads vertices every frame with camera-relative coordinates.
 * Callers are responsible for providing interpolated positions (e.g., using entity.prevX/x 
 * with tickDelta). The tick() method clears builders to allow smooth transitions between frames.
 */
class ImmediateRenderer(
	owner: Any,
	name: String,
	depthTest: SafeContext.() -> Boolean,
	update: RenderBuilder.(SafeContext) -> Unit
) : AbstractRenderer(name, depthTest) {
	private val renderer = RegionRenderer()

	// Font atlas used for current text rendering
	private var _currentFontAtlas: SDFFontAtlas? = null
	override val currentFontAtlas: SDFFontAtlas? get() = _currentFontAtlas

	init {
		owner.listen<RenderEvent.Render> {
			renderer.clearData()
			val renderBuilder = RenderBuilder(mc.gameRenderer.camera.pos).also { it.update(SafeContext.create() ?: return@listen) }
			upload(renderBuilder)
			render()
			renderScreen()
		}
	}

	/** Upload collected geometry to GPU. Must be called on main thread. */
	fun upload(renderBuilder: RenderBuilder) {
		renderer.upload(renderBuilder.collector)
		_currentFontAtlas = renderBuilder.fontAtlas
	}

	/**
	 * Get renderer/transform pairs for world-space rendering.
	 * Returns single renderer with camera-relative transform and fresh glint TextureMat.
	 */
	override fun getRendererTransforms(): List<Pair<RegionRenderer, GpuBufferSlice>> {
		if (!renderer.hasData()) return emptyList()
		
		val modelViewMatrix = RenderMain.modelViewMatrix
		val modelView = Matrix4f(modelViewMatrix).m30(0f).m31(0f).m32(0f)
		val dynamicTransform = RenderSystem.getDynamicUniforms()
			.write(
				modelView,
				Vector4f(1f, 1f, 1f, 1f),
				Vector3f(0f, 0f, 0f),
				RendererUtils.createGlintTransform(0.125f)  // Calibrated glint scale for world images
			)
		
		return listOf(renderer to dynamicTransform)
	}

	/**
	 * Get renderers for screen-space rendering.
	 */
	override fun getScreenRenderers() = if (renderer.hasScreenData()) listOf(renderer) else emptyList()

	companion object {
		fun Any.immediateRenderer(
			name: String,
			depthTest: SafeContext.() -> Boolean = { false },
			update: RenderBuilder.(SafeContext) -> Unit
		) = ImmediateRenderer(this, name, depthTest, update)
	}
}
