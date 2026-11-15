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
import kotlin.math.ceil
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

/**
 * Item stacks for the GUI, drawn by Minecraft exactly as it draws them in an inventory slot:
 * the model with its trims, dyes and glint, and over it the damage bar, the stack count and
 * the cooldown sweep.
 *
 * Nothing about an item is drawn by Lambda. Every icon on screen owns a [Slot] in one strip of
 * cells, the stacks are handed to a [DrawContext] the way a screen would, and a [GuiRenderer] of
 * our own renders what that recorded into the strip's framebuffer instead of the window (see
 * `GuiRendererMixin`). [import] then copies the strip into the Skia [image] the icons draw from.
 * Both only happen when something about a stack changed.
 */
object ItemAtlas {
    /** A cell in the atlas. Set [stack] and [size] to say what it should hold. */
    class Slot {
        var stack: ItemStack = ItemStack.EMPTY

        /** Edge length the icon is drawn at, in pixels: a multiple of [ITEM] to come out pixel for pixel */
        var size = 0

        /** The stack as it was last rendered, a copy because the live one mutates */
        internal var rendered: ItemStack = ItemStack.EMPTY
    }

    /**
     * The framebuffer the GUI renderer has to draw into in place of the window, set for as long
     * as the atlas is being rendered. [scale] is the GUI scale it renders at meanwhile.
     */
    @JvmStatic
    var target: Framebuffer? = null
        private set

    /**
     * The GUI scale the items are rendered at, a whole number as Minecraft's own always is:
     * the first that covers the largest icon. Smaller icons are scaled down from it.
     */
    @JvmStatic
    var scale = 1
        private set

    private val slots = ArrayList<Slot>()
    private var strip: Strip? = null
    private var pendingImport = false

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

    /** What decides how each item's model looks, as of the last render: see [render] */
    private var renderedModels = emptyList<Any>()
    private var renderedAt = 0L

    /** The strip. A new instance every time the items were rendered, so reading it redraws the reader. */
    private var image: Image? by mutableStateOf(null, neverEqualPolicy())

    fun add(slot: Slot) {
        slots += slot
    }

    fun remove(slot: Slot) {
        slots -= slot
    }

    /** Draws [slot]'s item over the scope, its count hanging over the corner as it does over a slot's */
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

    /** Renders the items if any changed. Returns whether [import] has something new to pick up. */
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

        // Recorded every frame, as the game records its own slots. It costs next to nothing and
        // is the only way to learn that a compass turned or a bow was drawn: only the model knows.
        slots.forEachIndexed { index, slot ->
            context.drawItem(slot.stack, index * CELL, 0)
            context.drawStackOverlay(mc.textRenderer, slot.stack, index * CELL, 0)
        }
        val models = ArrayList<Any>()
        var moving = slots.any { it.stack.hasGlint() || mc.player?.itemCooldownManager?.isCoolingDown(it.stack) == true }
        state.forEachItemElement {
            models += it.state().modelKey
            moving = moving || it.state().isAnimated
        }
        val changed = scale != this.scale || models != renderedModels ||
                slots.any { !ItemStack.areEqual(it.stack, it.rendered) }
        // What moves by itself, a glint or a cooldown, is redrawn at a steady rate instead of on
        // every frame. It looks no different, and at a high frame rate costs a fraction.
        val now = System.nanoTime()
        if (!changed && !(moving && now - renderedAt >= ANIMATION_INTERVAL)) {
            state.clear()
            return false
        }
        renderedAt = now

        val width = slots.size * CELL * scale
        val height = CELL * scale
        val strip = strip?.takeIf { it.width == width && it.height == height } ?: run {
            strip?.close()
            Strip(width, height).also { strip = it }
        }
        this.scale = scale
        renderedModels = models
        slots.forEach { it.rendered = it.stack.copy() }
        draw(strip.framebuffer)
        pendingImport = true
        return true
    }

    /** Copies what [render] drew into [image]. Runs while Skia holds the GL context. */
    fun import(skia: DirectContext) {
        if (!pendingImport) return
        pendingImport = false
        val previous = image
        image = strip?.snapshot(skia)
        // Safe at once: anything Skia recorded with the old strip holds its own reference to it
        previous?.close()
    }

    /** Has the GUI renderer draw what [context] recorded into [framebuffer] */
    private fun draw(framebuffer: Framebuffer) {
        RenderSystem.getDevice().createCommandEncoder()
            .clearColorAndDepthTextures(framebuffer.colorAttachment, 0, framebuffer.depthAttachment, 1.0)

        val projection = RenderSystem.getProjectionMatrixBuffer()
        val projectionType = RenderSystem.getProjectionType()
        target = framebuffer
        try {
            // A frame of its own each time, or the renderer would keep showing the glint it cached
            renderer.incrementFrame()
            renderer.render(mc.gameRenderer.fogRenderer.getFogBuffer(FogRenderer.FogType.NONE))
        } finally {
            target = null
            if (projection != null) RenderSystem.setProjectionMatrix(projection, projectionType)
        }
    }

    /** The framebuffer the items are rendered into, and the Skia surface wrapped around it to copy them out */
    private class Strip(val width: Int, val height: Int) : AutoCloseable {
        val framebuffer = SimpleFramebuffer("Lambda GUI items", width, height, true)

        // Resolved now, as game rendering, so no framebuffer object is created once Skia has the context
        private val glFramebuffer = (framebuffer.colorAttachment as GlTexture)
            .getOrCreateFramebuffer((RenderSystem.getDevice() as GlBackend).bufferManager, null)
        private var renderTarget: BackendRenderTarget? = null
        private var surface: Surface? = null

        fun snapshot(skia: DirectContext): Image? {
            val surface = surface ?: run {
                val renderTarget = BackendRenderTarget.makeGL(width, height, 0, 0, glFramebuffer, FramebufferFormat.GR_GL_RGBA8)
                    .also { renderTarget = it }
                Surface.makeFromBackendRenderTarget(
                    skia, renderTarget, SurfaceOrigin.BOTTOM_LEFT, SurfaceColorFormat.RGBA_8888, ColorSpace.sRGB
                ).also { surface = it }
            } ?: return null
            // The game drew into the framebuffer behind Skia's back; without this the
            // snapshot Skia cached the first time would be handed out forever
            surface.notifyContentWillChange(ContentChangeMode.DISCARD)
            return surface.makeImageSnapshot()
        }

        override fun close() {
            surface?.close()
            renderTarget?.close()
            framebuffer.delete()
        }
    }

    /** Nanoseconds between two renders of items that animate: 75 a second, enough for any shimmer */
    private const val ANIMATION_INTERVAL = 1_000_000_000L / 75

    /** Edge length of an item, in the units Minecraft lays its GUI out in */
    const val ITEM = 16

    /** Edge length of a cell: an item, and the two units a stack count's shadow reaches past it */
    private const val CELL = 18
}
