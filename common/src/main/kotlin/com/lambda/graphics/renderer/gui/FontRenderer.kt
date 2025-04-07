/*
 * Copyright 2025 Lambda
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

package com.lambda.graphics.renderer.gui

import com.lambda.event.events.ConnectionEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.buffer.vertex.VertexArray
import com.lambda.graphics.buffer.vertex.attributes.VertexAttrib
import com.lambda.graphics.buffer.vertex.attributes.VertexMode
import com.lambda.graphics.gl.Matrices
import com.lambda.graphics.renderer.gui.font.CachedString
import com.lambda.graphics.renderer.gui.font.CachedString.Companion.SCALE_FACTOR
import com.lambda.graphics.renderer.gui.font.CachedString.Companion.getStringWidth
import com.lambda.graphics.renderer.gui.font.core.LambdaAtlas.height
import com.lambda.graphics.shader.Shader.Companion.shadeUniforms
import com.lambda.graphics.shader.Shader.Companion.shader
import com.lambda.graphics.texture.TextureOwner.bind
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.modules.client.RenderSettings
import com.lambda.util.math.Vec2d
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL30
import java.awt.Color
import java.util.function.Function

/**
 * Renders text and emoji glyphs using a shader-based font rendering system.
 * This class handles text and emoji rendering, shadow effects, and text scaling.
 */
object FontRenderer {
    val vao by lazy { VertexArray(VertexMode.TRIANGLES, VertexAttrib.Group.FONT) }
    private val stringCache = Object2ObjectOpenHashMap<Pair<String, Boolean>, CachedString>()
    private val shader by lazy { shader("renderer/font") }

    val chars get() = RenderSettings.textFont
    val emojis get() = RenderSettings.emojiFont

    init {
        listen<ConnectionEvent.Connect.Pre>(alwaysListen = true) {
            invalidate()
        }

        listen<ConnectionEvent.Connect.Post>(alwaysListen = true) {
            invalidate()
        }
    }

    /**
     * Renders a text string at a specified position with configurable color, scale, shadow, and emoji parsing
     *
     * @param text The text to render.
     * @param position The position to render the text.
     * @param color The color of the text.
     * @param scale The scale factor of the text.
     * @param shadow Whether to render a shadow for the text.
     */
    fun drawString(
        text: String,
        position: Vec2d = Vec2d.ZERO,
        color: Color = Color.WHITE,
        scale: Double = ClickGui.fontScale,
        shadow: Boolean = true,
        shade: Boolean = false
    ) = Matrices.push {
        translate(position.x, position.y)
        scale(scale, scale)

        shader.use()
        shader.shadeUniforms(shade)
        shader["u_FontTexture"] = 0
        shader["u_EmojiTexture"] = 1
        shader["u_SDFMin"] = RenderSettings.sdfMin
        shader["u_SDFMax"] = RenderSettings.sdfMax
        shader["u_Color"] = color

        bind(chars, emojis)

        val string = stringCache.computeIfAbsent(text to shadow, Function<Pair<String, Boolean>, CachedString> {
            CachedString(it.first, it.second)
        })

        vao.linkVbo(string.vbo)
        vao.renderIndices(string.ibo)
    }

    /**
     * Calculates the width of the specified text.
     *
     * @param text The text to measure.
     * @param scale The scale factor for the width calculation.
     * @return The width of the text at the specified scale.
     */
    fun getWidth(text: String, scale: Double = 1.0): Double {
        // Todo: separate width cache?
        val cache = stringCache[text to true] ?: stringCache[text to false]
        return (cache?.cachedWidth ?: getStringWidth(text)) * scale
    }

    /**
     * Computes the effective height of the rendered text
     *
     * The height is derived from the current font's base height, adjusted by a scaling factor
     * that ensures consistent visual proportions
     *
     * @param scale The scale factor for the height calculation.
     * @return The height of the text at the specified scale.
     */
    fun getHeight(scale: Double = 1.0) =
        chars.height * 0.7 * SCALE_FACTOR * scale

    fun invalidate() {
        stringCache.values.forEach {
            glDeleteBuffers(intArrayOf(
                it.vbo.glBuffer.id,
                it.ibo.glBuffer.id
            ))
        }

        stringCache.clear()
    }
}
