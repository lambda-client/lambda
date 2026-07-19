
package com.minato.graphics.mc.renderer

import com.minato.Minato.mc
import com.minato.event.events.RenderEvent
import com.minato.event.listener.UnsafeListener.Companion.listenUnsafe
import com.minato.graphics.RenderMain
import com.minato.graphics.mc.RegionRenderer
import com.minato.graphics.mc.RenderBuilder
import com.minato.graphics.mc.RenderDsl
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
		@RenderDsl
		fun Any.immediateRenderer(
			name: String,
			depthTest: () -> Boolean = { false },
			update: RenderBuilder.() -> Unit
		) = ImmediateRenderer(this, name, depthTest, update)
	}
}
