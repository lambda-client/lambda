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
 * Modern replacement for the legacy Treed system. Handles geometry that is cleared and rebuilt
 * every tick.
 * 
 * Geometry is stored relative to the camera position at tick time. At render time, we compute
 * the delta between tick-camera and current-camera to ensure smooth motion without jitter.
 */
class TickedRenderer(name: String, depthTest: Boolean = false) : AbstractRenderer(name, depthTest) {
	private val renderer = RegionRenderer()
	private var renderBuilder: RenderBuilder? = null
	
	// Camera position captured at tick time (when shapes are built)
	private var tickCameraPos: Vec3d? = null

	// Font atlas used for current text rendering
	private var _currentFontAtlas: SDFFontAtlas? = null
	override val currentFontAtlas: SDFFontAtlas? get() = _currentFontAtlas
	
	override val deferredItems: List<RenderBuilder.ScreenItemRender>?
		get() = renderBuilder?.deferredItems

	/** Get the current shape scope for drawing. Geometry stored relative to tick camera. */
	fun shapes(block: RenderBuilder.() -> Unit) {
		val cameraPos = mc.gameRenderer?.camera?.pos ?: return
		if (renderBuilder == null) {
			tickCameraPos = cameraPos
			renderBuilder = RenderBuilder(cameraPos)
		}
		renderBuilder?.apply(block)
	}

	/** Clear all current builders. Call this at the end of every tick. */
	fun clear() {
		renderBuilder = null
		tickCameraPos = null
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
	 * Computes delta between tick-camera and current-camera for smooth interpolation.
	 * Includes fresh glint TextureMat for world image animation.
	 */
	override fun getRendererTransforms(): List<Pair<RegionRenderer, GpuBufferSlice>> {
		val currentCameraPos = mc.gameRenderer?.camera?.pos ?: return emptyList()
		val tickCamera = tickCameraPos ?: return emptyList()
		if (!renderer.hasData()) return emptyList()

		val modelViewMatrix = RenderMain.modelViewMatrix

		// Compute the camera movement since tick time in double precision
		// Geometry is stored relative to tickCamera, so we translate by (tickCamera - currentCamera)
		val deltaX = (tickCamera.x - currentCameraPos.x).toFloat()
		val deltaY = (tickCamera.y - currentCameraPos.y).toFloat()
		val deltaZ = (tickCamera.z - currentCameraPos.z).toFloat()

		val modelView = Matrix4f(modelViewMatrix).translate(deltaX, deltaY, deltaZ)
		val dynamicTransform = RenderSystem.getDynamicUniforms()
			.write(modelView, Vector4f(1f, 1f, 1f, 1f), Vector3f(0f, 0f, 0f), RendererUtils.createGlintTransform(0.25f))
		
		return listOf(renderer to dynamicTransform)
	}

	/**
	 * Get renderers for screen-space rendering.
	 */
	override fun getScreenRenderers(): List<RegionRenderer> {
		return if (renderer.hasScreenData()) listOf(renderer) else emptyList()
	}
}
