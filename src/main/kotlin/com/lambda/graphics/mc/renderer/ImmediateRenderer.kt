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
import com.lambda.graphics.RenderMain
import com.lambda.graphics.mc.RegionRenderer
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.text.SDFFontAtlas
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.util.math.Vec3d
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
class ImmediateRenderer(name: String, depthTest: Boolean = false) : AbstractRenderer(name, depthTest) {
	private val renderer = RegionRenderer()

	// Current frame builder (being populated this frame)
	private var renderBuilder: RenderBuilder? = null

	// Font atlas used for current text rendering
	private var _currentFontAtlas: SDFFontAtlas? = null
	override val currentFontAtlas: SDFFontAtlas? get() = _currentFontAtlas
	
	override val deferredItems: List<RenderBuilder.ScreenItemRender>?
		get() = renderBuilder?.deferredItems

	/**
	 * Get the current camera position for building camera-relative shapes.
	 * Returns null if camera is not available.
	 */
	private fun getCameraPos(): Vec3d? = mc.gameRenderer?.camera?.pos

	/** Get or create a ShapeScope for drawing with camera-relative coordinates. */
	fun shapes(block: RenderBuilder.() -> Unit) {
		val s = renderBuilder ?: RenderBuilder(getCameraPos() ?: return).also { renderBuilder = it }
		s.apply(block)
	}

	/** Clear all geometry data. */
	fun clear() {
		renderBuilder = null
	}

	/** Called each tick to reset for next frame. */
	fun tick() {
		renderBuilder = null
	}

	/** Upload collected geometry to GPU. Must be called on main thread. */
	fun upload() {
		renderBuilder?.let { s ->
			renderer.upload(s.collector)
			_currentFontAtlas = s.fontAtlas
		} ?: run {
			renderer.clearData()
			_currentFontAtlas = null
		}
	}

	/** Close and release all GPU resources. */
	fun close() {
		renderer.close()
		clear()
	}

	/**
	 * Get renderer/transform pairs for world-space rendering.
	 * Returns single renderer with identity-based transform (camera-relative coords).
	 */
	override fun getRendererTransforms(): List<Pair<RegionRenderer, GpuBufferSlice>> {
		if (!renderer.hasData()) return emptyList()
		
		val modelViewMatrix = RenderMain.modelViewMatrix
		val dynamicTransform = RenderSystem.getDynamicUniforms()
			.write(
				modelViewMatrix,
				Vector4f(1f, 1f, 1f, 1f),
				Vector3f(0f, 0f, 0f),
				Matrix4f()
			)
		
		return listOf(renderer to dynamicTransform)
	}

	/**
	 * Get renderers for screen-space rendering.
	 */
	override fun getScreenRenderers(): List<RegionRenderer> {
		return if (renderer.hasScreenData()) listOf(renderer) else emptyList()
	}
}
