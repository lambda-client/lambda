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
import com.lambda.context.SafeContext
import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.graphics.RenderMain
import com.lambda.graphics.mc.RegionRenderer
import com.lambda.graphics.mc.RenderBuilder
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderSystem
import org.joml.Vector3f
import org.joml.Vector4f

class ImmediateRenderer(
	owner: Any,
	name: String,
	depthTest: () -> Boolean,
	update: RenderBuilder.() -> Unit
) : AbstractRenderer(name, depthTest) {
	private val renderer = RegionRenderer()

	init {
		owner.listenUnsafe<RenderEvent.PreRenderWorld> {
			val depth = depthTest()
			val renderBuilder = RenderBuilder(mc.gameRenderer.camera.pos, depthTest = depth).also {
				it.update()
			}
			upload(renderBuilder)
		}

		owner.listenUnsafe<RenderEvent.RenderWorld> { render() }
		owner.listenUnsafe<RenderEvent.RenderScreen> { renderScreen() }
	}

	fun upload(renderBuilder: RenderBuilder) {
		renderer.upload(renderBuilder.collector)
	}

	override fun getRendererTransforms(): List<Pair<RegionRenderer, GpuBufferSlice>> {
		if (!renderer.hasData()) return emptyList()
		
		val dynamicTransform = RenderSystem.getDynamicUniforms()
			.write(
				RenderMain.cameraRotationMatrix,
				Vector4f(1f, 1f, 1f, 1f),
				Vector3f(0f, 0f, 0f),
				RendererUtils.createGlintTransform(0.125f)
			)
		
		return listOf(renderer to dynamicTransform)
	}

	override fun getScreenRenderers() = if (renderer.hasScreenData()) listOf(renderer) else emptyList()

	companion object {
		fun Any.immediateRenderer(
			name: String,
			depthTest: () -> Boolean = { false },
			update: RenderBuilder.() -> Unit
		) = ImmediateRenderer(this, name, depthTest, update)
	}
}
