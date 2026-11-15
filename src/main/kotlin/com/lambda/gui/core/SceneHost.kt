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
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.textures.TextureFormat
import kotlinx.coroutines.Dispatchers
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

/**
 * A Compose scene that renders into a texture through Skia, on the game's own OpenGL context.
 *
 * The scene is retained: [advance] runs composition, animation and layout every frame, which is
 * cheap when nothing changed, and reports whether any pixel did. Only then does [draw] have to
 * hand the context to Skia, so an idle interface costs the game nothing but compositing [texture].
 */
@OptIn(InternalComposeUiApi::class)
internal class SceneHost(content: @Composable () -> Unit) : AutoCloseable {
    val platform = ScenePlatform()

    private val recomposer = FrameRecomposer(Dispatchers.Unconfined)
    private var invalidated = true

    private val scene: ComposeScene = CanvasLayersComposeScene(
        frameRecomposer = recomposer,
        platformContext = platform,
        invalidateDraw = { invalidated = true },
    ).also { it.setContent(content = content) }

    val input = SceneInput(scene)

    private var skia: DirectContext? = null
    private var target: SkiaTarget? = null

    /** What the scene last drew, premultiplied; null until the first [draw] */
    val texture: GpuTextureView? get() = target?.view

    /** Brings the scene up to date for this frame and returns whether it has to be drawn again */
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

    /** Draws the scene. [beforeScene] runs first with Skia in control of the context, to import textures. */
    fun draw(beforeScene: (DirectContext) -> Unit = {}) {
        val size = scene.size ?: return
        GlState.borrow {
            val skia = skia ?: DirectContext.makeGL().also { skia = it }
            val target = target?.takeIf { it.size == size } ?: run {
                target?.close()
                SkiaTarget(skia, size).also { target = it }
            }
            // Everything the game did since the last draw happened behind Skia's back
            skia.resetGLAll()
            beforeScene(skia)
            target.surface.canvas.clear(Color.TRANSPARENT)
            scene.draw(target.surface.canvas.asComposeCanvas())
            target.surface.flushAndSubmit()
        }
        invalidated = false
    }

    /** Stops the scene. Its GPU resources are left to die with the context, which is going away too. */
    override fun close() {
        scene.close()
        recomposer.close()
    }
}

/**
 * The texture Skia draws the scene into. The colour texture belongs to the game so its pipelines
 * can sample it; the framebuffer around it is ours because Skia needs a stencil buffer for clips.
 */
private class SkiaTarget(skia: DirectContext, val size: IntSize) : AutoCloseable {
    private val texture: GpuTexture = RenderSystem.getDevice().createTexture(
        { "Lambda GUI" },
        GpuTexture.USAGE_RENDER_ATTACHMENT or GpuTexture.USAGE_TEXTURE_BINDING,
        TextureFormat.RGBA8,
        size.width, size.height, 1, 1
    )
    val view: GpuTextureView = RenderSystem.getDevice().createTextureView(texture)

    private val framebuffer = glGenFramebuffers()
    private val stencil = glGenRenderbuffers()
    private val renderTarget: BackendRenderTarget
    val surface: Surface

    init {
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, (texture as GlTexture).glId, 0)
        glBindRenderbuffer(GL_RENDERBUFFER, stencil)
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, size.width, size.height)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, stencil)

        renderTarget = BackendRenderTarget.makeGL(size.width, size.height, 0, 8, framebuffer, FramebufferFormat.GR_GL_RGBA8)
        surface = Surface.makeFromBackendRenderTarget(
            skia, renderTarget, SurfaceOrigin.BOTTOM_LEFT, SurfaceColorFormat.RGBA_8888, ColorSpace.sRGB
        ) ?: error("Skia rejected the GUI render target")
    }

    override fun close() {
        surface.close()
        renderTarget.close()
        glDeleteFramebuffers(framebuffer)
        glDeleteRenderbuffers(stencil)
        view.close()
        texture.close()
    }
}
