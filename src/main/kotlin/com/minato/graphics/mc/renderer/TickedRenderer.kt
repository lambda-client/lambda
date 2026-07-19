
package com.minato.graphics.mc.renderer

import com.minato.Minato.mc
import com.minato.event.events.RenderEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.UnsafeListener.Companion.listenUnsafe
import com.minato.graphics.RenderMain
import com.minato.graphics.mc.RegionRenderer
import com.minato.graphics.mc.RenderBuilder
import com.minato.graphics.mc.RenderDsl
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.util.math.Vec3d
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f


class TickedRenderer(
	owner: Any,
	name: String,
	depthTest: () -> Boolean,
	update: RenderBuilder.() -> Unit
) : AbstractRenderer(name, depthTest) {
	private val renderer = RegionRenderer()

	private var tickCameraPos: Vec3d? = null

	init {
		owner.listenUnsafe<TickEvent.Pre> {
			val depth = depthTest()
			clear()
			tickCameraPos = mc.gameRenderer.camera.pos
			val renderBuilder = RenderBuilder(tickCameraPos ?: return@listenUnsafe, depthTest = depth).also {
				it.update()
			}
			upload(renderBuilder)
		}

		owner.listenUnsafe<RenderEvent.RenderWorld> { render() }
		owner.listenUnsafe<RenderEvent.RenderScreen> { renderScreen() }
	}

	fun clear() {
		renderer.clearData()
		tickCameraPos = null
	}

	fun upload(renderBuilder: RenderBuilder) {
		renderer.upload(renderBuilder.collector)
	}

	override fun getRendererTransforms(): List<Pair<RegionRenderer, GpuBufferSlice>> {
		val currentCameraPos = mc.gameRenderer?.camera?.pos ?: return emptyList()
		val tickCamera = tickCameraPos ?: return emptyList()
		if (!renderer.hasData()) return emptyList()

		val deltaX = (tickCamera.x - currentCameraPos.x).toFloat()
		val deltaY = (tickCamera.y - currentCameraPos.y).toFloat()
		val deltaZ = (tickCamera.z - currentCameraPos.z).toFloat()

		val modelView = Matrix4f(RenderMain.cameraRotationMatrix).m30(0f).m31(0f).m32(0f).translate(deltaX, deltaY, deltaZ)
		val dynamicTransform = RenderSystem.getDynamicUniforms()
			.write(modelView, Vector4f(1f, 1f, 1f, 1f), Vector3f(0f, 0f, 0f), RendererUtils.createGlintTransform(0.25f))
		
		return listOf(renderer to dynamicTransform)
	}

	override fun getScreenRenderers() = if (renderer.hasScreenData()) listOf(renderer) else emptyList()

	companion object {
		@RenderDsl
		fun Any.tickedRenderer(
			name: String,
			depthTest: () -> Boolean = { false },
			update: RenderBuilder.() -> Unit
		) = TickedRenderer(this, name, depthTest, update)
	}
}
