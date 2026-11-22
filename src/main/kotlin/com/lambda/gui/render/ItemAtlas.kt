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

package com.lambda.gui.render

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import com.lambda.Lambda.mc
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.gl.Framebuffer
import net.minecraft.client.gl.GlBackend
import net.minecraft.client.gl.SimpleFramebuffer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.render.GuiRenderer
import net.minecraft.client.gui.render.state.GuiRenderState
import net.minecraft.client.render.fog.FogRenderer
import net.minecraft.client.texture.GlTexture
import net.minecraft.item.ItemStack
import org.jetbrains.skia.BackendRenderTarget
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ContentChangeMode
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.FramebufferFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import org.jetbrains.skia.SurfaceColorFormat
import org.jetbrains.skia.SurfaceOrigin
import kotlin.math.ceil

object ItemAtlas {
    class Slot {
        var stack: ItemStack = ItemStack.EMPTY

        var size = 0

        internal var rendered: ItemStack = ItemStack.EMPTY
    }

    @JvmStatic
    var target: Framebuffer? = null
        private set

    @JvmStatic
    var scale = 1
        private set

    private val slots = ArrayList<Slot>()
    private var strip: Strip? = null
    private var pendingImport = false
    private var rearranged = false

    private val state = GuiRenderState()
    private val context by lazy { DrawContext(mc, state, 0, 0) }
    private val renderer by lazy {
        GuiRenderer(
            state,
            mc.bufferBuilders.entityVertexConsumers,
            mc.gameRenderer.entityRenderCommandQueue,
            mc.gameRenderer.entityRenderDispatcher,
            emptyList()
        )
    }

    private var renderedModels = emptyList<Any>()
    private var renderedAt = 0L

    private var image: Image? by mutableStateOf(null, neverEqualPolicy())

    fun add(slot: Slot) {
        slots += slot
        rearranged = true
    }

    fun remove(slot: Slot) {
        slots -= slot
        rearranged = true
    }

    fun DrawScope.drawItem(slot: Slot) {
        val strip = image ?: return
        val index = slots.indexOf(slot)
        val cell = CELL * scale
        if (index < 0 || slot.rendered.isEmpty || (index + 1) * cell > strip.width) return
        drawIntoCanvas { canvas ->
            canvas.skiaCanvas.drawImageRect(
                strip,
                Rect.makeXYWH(index * cell.toFloat(), 0f, cell.toFloat(), cell.toFloat()),
                Rect.makeWH(size.width * CELL / ITEM, size.height * CELL / ITEM),
                SamplingMode.LINEAR,
                null,
                true
            )
        }
    }

    fun render(): Boolean {
        if (slots.isEmpty()) {
            if (strip == null) return false
            strip?.close()
            strip = null
            pendingImport = true
            return true
        }
        val scale = ceil(slots.maxOf { it.size } / ITEM.toFloat()).toInt()
        if (scale <= 0) return false

        slots.forEachIndexed { index, slot ->
            context.drawItem(slot.stack, index * CELL, 0)
            context.drawStackOverlay(mc.textRenderer, slot.stack, index * CELL, 0)
        }
        val models = ArrayList<Any>()
        var moving =
            slots.any {
                it.stack.hasGlint() || mc.player?.itemCooldownManager?.isCoolingDown(it.stack) == true
            }
        state.forEachItemElement {
            models += it.state().modelKey
            moving = moving || it.state().isAnimated
        }
        val changed =
            rearranged || scale != this.scale || models != renderedModels ||
                    slots.any { !ItemStack.areEqual(it.stack, it.rendered) }
        val now = System.nanoTime()
        if (!changed && !(moving && now - renderedAt >= ANIMATION_INTERVAL)) {
            state.clear()
            return false
        }
        renderedAt = now

        val width = slots.size * CELL * scale
        val height = CELL * scale
        val strip = strip?.takeIf { it.width == width && it.height == height }
            ?: run {
                strip?.close()
                Strip(width, height).also { strip = it }
            }
        this.scale = scale
        rearranged = false
        renderedModels = models
        slots.forEach { it.rendered = it.stack.copy() }
        draw(strip.framebuffer)
        pendingImport = true
        return true
    }

    fun release() {
        slots.clear()
        image?.close()
        image = null
        strip?.close()
        strip = null
        pendingImport = false
        renderedModels = emptyList()
    }

    fun import(skia: DirectContext) {
        if (!pendingImport) return
        pendingImport = false
        val previous = image
        image = strip?.snapshot(skia)
        previous?.close()
    }

    private fun draw(framebuffer: Framebuffer) {
        RenderSystem.getDevice().createCommandEncoder()
            .clearColorAndDepthTextures(
                framebuffer.colorAttachment,
                0,
                framebuffer.depthAttachment,
                1.0
            )

        val projection = RenderSystem.getProjectionMatrixBuffer()
        val projectionType = RenderSystem.getProjectionType()
        target = framebuffer
        try {
            renderer.incrementFrame()
            renderer.render(mc.gameRenderer.fogRenderer.getFogBuffer(FogRenderer.FogType.NONE))
        } finally {
            target = null
            if (projection != null) RenderSystem.setProjectionMatrix(projection, projectionType)
        }
    }

    private class Strip(val width: Int, val height: Int) : AutoCloseable {
        val framebuffer = SimpleFramebuffer("Lambda GUI items", width, height, true)

        private val glFramebuffer =
            (framebuffer.colorAttachment as GlTexture)
                .getOrCreateFramebuffer((RenderSystem.getDevice() as GlBackend).bufferManager, null)
        private var renderTarget: BackendRenderTarget? = null
        private var surface: Surface? = null

        fun snapshot(skia: DirectContext): Image? {
            val surface = surface
                ?: run {
                    val renderTarget =
                        BackendRenderTarget.makeGL(
                            width,
                            height,
                            0,
                            0,
                            glFramebuffer,
                            FramebufferFormat.GR_GL_RGBA8
                        ).also { renderTarget = it }
                    Surface.makeFromBackendRenderTarget(
                        skia,
                        renderTarget,
                        SurfaceOrigin.BOTTOM_LEFT,
                        SurfaceColorFormat.RGBA_8888,
                        ColorSpace.sRGB
                    ).also { surface = it }
                } ?: return null
            surface.notifyContentWillChange(ContentChangeMode.DISCARD)
            return surface.makeImageSnapshot()
        }

        override fun close() {
            surface?.close()
            renderTarget?.close()
            framebuffer.delete()
        }
    }

    private const val ANIMATION_INTERVAL = 1_000_000_000L / 75

    const val ITEM = 16

    private const val CELL = 18
}
