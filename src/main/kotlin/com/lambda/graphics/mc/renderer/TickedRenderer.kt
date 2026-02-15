

package com.lambda.graphics.mc.renderer

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
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


class TickedRenderer(
	owner: Any,
	name: String,
	depthTest: SafeContext.() -> Boolean,
	update: RenderBuilder.(SafeContext) -> Unit
) : AbstractRenderer(name, depthTest) {
	private val renderer = RegionRenderer()

	private var tickCameraPos: Vec3d? = null

	private var _currentFontAtlas: SDFFontAtlas? = null
	override val currentFontAtlas: SDFFontAtlas? get() = _currentFontAtlas

	init {
		owner.listen<TickEvent.Pre> {
			val depth = depthTest()
			clear()
			tickCameraPos = mc.gameRenderer.camera.pos
			val renderBuilder = RenderBuilder(tickCameraPos ?: return@listen, depthTest = depth).also {
				it.update(this)
			}
			upload(renderBuilder)
		}

		owner.listen<RenderEvent.RenderWorld> { render() }
		owner.listen<RenderEvent.RenderScreen> { renderScreen() }
	}

	fun clear() {
		renderer.clearData()
		tickCameraPos = null
	}

	fun upload(renderBuilder: RenderBuilder) {
		renderer.upload(renderBuilder.collector)
		_currentFontAtlas = renderBuilder.fontAtlas
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
		fun Any.tickedRenderer(
			name: String,
			depthTest: SafeContext.() -> Boolean = { false },
			update: RenderBuilder.(SafeContext) -> Unit
		) = TickedRenderer(this, name, depthTest, update)
	}
}
