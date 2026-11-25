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

package com.lambda.gui.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import net.minecraft.client.texture.GlTexture
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.Color
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.FramebufferFormat
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0
import org.lwjgl.opengl.GL30.GL_DEPTH24_STENCIL8
import org.lwjgl.opengl.GL30.GL_DEPTH_STENCIL_ATTACHMENT
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_RENDERBUFFER
import org.lwjgl.opengl.GL30.glBindFramebuffer
import org.lwjgl.opengl.GL30.glBindRenderbuffer
import org.lwjgl.opengl.GL30.glDeleteFramebuffers
import org.lwjgl.opengl.GL30.glDeleteRenderbuffers
import org.lwjgl.opengl.GL30.glFramebufferRenderbuffer
import org.lwjgl.opengl.GL30.glFramebufferTexture2D
import org.lwjgl.opengl.GL30.glGenFramebuffers
import org.lwjgl.opengl.GL30.glGenRenderbuffers
import org.lwjgl.opengl.GL30.glRenderbufferStorage
import kotlin.coroutines.CoroutineContext

@OptIn(InternalComposeUiApi::class)
internal class SceneHost(
	context: CoroutineContext,
	content: @Composable () -> Unit
) : AutoCloseable {
	val platform = ScenePlatform()

	private val recomposer = FrameRecomposer(RenderThread + context)
	private var invalidated = true

	private val scene: ComposeScene =
		CanvasLayersComposeScene(
			frameRecomposer = recomposer,
			platformContext = platform,
			invalidateDraw = { invalidated = true },
		).also { it.setContent(content = content) }

	val input = SceneInput(scene)

	private var skia: DirectContext? = null
	private var target: SkiaTarget? = null
	private var underlayTarget: SkiaTarget? = null

	val texture: GpuTextureView? get() = target?.view
	val underlayTexture: GpuTextureView? get() = underlayTarget?.view

	fun advance(size: IntSize, density: Float): Boolean {
		if (scene.size != size || scene.density.density != density) {
			scene.density = Density(density)
			scene.size = size
			platform.resize(size, density)
			invalidated = true
		}
		recomposer.performFrame(System.nanoTime())
		scene.measureAndLayout()
		return invalidated || scene.hasPendingDraw || target == null
	}

	fun draw(underlay: GraphicsLayer? = null, beforeScene: (DirectContext) -> Unit = {}) {
		val size = scene.size ?: return
		val outgrown =
			listOfNotNull(
				target?.takeIf { it.size != size },
				underlayTarget?.takeIf { it.size != size || underlay == null }
			)
		val target = target?.takeIf { it !in outgrown } ?: SkiaTarget(size).also { target = it }
		val underlayTarget =
			if (underlay == null) null
			else underlayTarget?.takeIf { it !in outgrown } ?: SkiaTarget(size)
		this.underlayTarget = underlayTarget
		GlState.borrow {
			val skia = skia ?: DirectContext.makeGL().also { skia = it }
			outgrown.forEach { it.detach() }
			val surface = target.attach(skia)
			val underlaySurface = underlayTarget?.attach(skia)
			skia.resetGLAll()
			beforeScene(skia)
			surface.canvas.clear(Color.TRANSPARENT)
			scene.draw(surface.canvas.asComposeCanvas())
			surface.flushAndSubmit()
			if (underlay != null && underlaySurface != null) {
				underlaySurface.canvas.clear(Color.TRANSPARENT)
				CanvasDrawScope().draw(
					scene.density,
					LayoutDirection.Ltr,
					underlaySurface.canvas.asComposeCanvas(),
					size.toSize()
				) { drawLayer(underlay) }
				underlaySurface.flushAndSubmit()
			}
		}
		outgrown.forEach { it.close() }
		invalidated = false
	}

	override fun close() {
		scene.close()
		recomposer.close()
	}

	fun closeAndFree() {
		runCatching { scene.close() }
		runCatching { recomposer.close() }
		val freed = listOfNotNull(target, underlayTarget)
		target = null
		underlayTarget = null
		GlState.borrow {
			freed.forEach { it.detach() }
			skia?.close()
		}
		skia = null
		freed.forEach { it.close() }
	}
}

private class SkiaTarget(val size: IntSize) : AutoCloseable {
	private val texture: GpuTexture =
		RenderSystem.getDevice().createTexture(
			{ "Lambda GUI" },
			GpuTexture.USAGE_RENDER_ATTACHMENT or GpuTexture.USAGE_TEXTURE_BINDING,
			TextureFormat.RGBA8,
			size.width, size.height, 1, 1
		)
	val view: GpuTextureView = RenderSystem.getDevice().createTextureView(texture)

	private var framebuffer = 0
	private var stencil = 0
	private var renderTarget: BackendRenderTarget? = null
	private var surface: Surface? = null

	fun attach(skia: DirectContext): Surface = surface
		?: run {
			framebuffer = glGenFramebuffers()
			stencil = glGenRenderbuffers()
			glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)
			glFramebufferTexture2D(
				GL_FRAMEBUFFER,
				GL_COLOR_ATTACHMENT0,
				GL_TEXTURE_2D,
				(texture as GlTexture).glId,
				0
			)
			glBindRenderbuffer(GL_RENDERBUFFER, stencil)
			glRenderbufferStorage(
				GL_RENDERBUFFER,
				GL_DEPTH24_STENCIL8,
				size.width,
				size.height
			)
			glFramebufferRenderbuffer(
				GL_FRAMEBUFFER,
				GL_DEPTH_STENCIL_ATTACHMENT,
				GL_RENDERBUFFER,
				stencil
			)

			val renderTarget =
				BackendRenderTarget.makeGL(
					size.width,
					size.height,
					0,
					8,
					framebuffer,
					FramebufferFormat.GR_GL_RGBA8
				).also { renderTarget = it }
			Surface.makeFromBackendRenderTarget(
				skia,
				renderTarget,
				SurfaceOrigin.BOTTOM_LEFT,
				SurfaceColorFormat.RGBA_8888,
				ColorSpace.sRGB
			)?.also { surface = it } ?: error("Skia rejected the GUI render target")
		}

	fun detach() {
		surface?.close()
		surface = null
		renderTarget?.close()
		renderTarget = null
		if (framebuffer != 0) glDeleteFramebuffers(framebuffer)
		if (stencil != 0) glDeleteRenderbuffers(stencil)
		framebuffer = 0
		stencil = 0
	}

	override fun close() {
		view.close()
		texture.close()
	}
}
