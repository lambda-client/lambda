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

package com.lambda.gui

import com.lambda.Lambda.mc
import com.lambda.core.Loadable
import com.lambda.event.EventFlow.post
import com.lambda.event.events.GuiEvent
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.path
import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import imgui.ImGui
import imgui.ImGuiIO
import imgui.flag.ImGuiConfigFlags
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import net.minecraft.client.gl.GlBackend
import net.minecraft.client.texture.GlTexture
import org.lwjgl.opengl.GL11.glViewport
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER
import kotlin.math.abs

object DearImGui : Loadable {
    val implGlfw = ImGuiImplGlfw()
    val implGl3 = ImGuiImplGl3()

    val io: ImGuiIO get() = ImGui.getIO()
    const val DEFAULT_FLAGS = ImGuiConfigFlags.NavEnableKeyboard or // Enable Keyboard Controls
            ImGuiConfigFlags.NavEnableSetMousePos or // Move the cursor using the keyboard
            ImGuiConfigFlags.DockingEnable

    private var lastScale = 0f
    private var lastScaleChangeTimestamp = 0L
    private var scaleChanged = false
    private var targetScale = 0f

    private fun updateScale(scale: Float) {
        io.fonts.clear()
        val baseFontSize = 13f
        io.fonts.addFontFromFileTTF("fonts/FiraSans-Regular.ttf".path, baseFontSize * scale)
        io.fonts.build()

        implGl3.createFontsTexture()
    }

    fun render() {
        val scale = (GuiSettings.scaleSetting / 100.0).toFloat()

        if (lastScale == 0f) {
            targetScale = scale
            updateScale(targetScale)
            lastScale = targetScale
        }

        if (scale > 0 && abs(scale - lastScale) > 0.001f) {
            if (abs(scale - targetScale) > 0.001f) {
                lastScaleChangeTimestamp = System.currentTimeMillis()
                scaleChanged = true
                targetScale = scale
            }
        }

        if (scaleChanged && (lastScaleChangeTimestamp + 1000 < System.currentTimeMillis())) {
            updateScale(targetScale)
            lastScale = targetScale
            scaleChanged = false
        }

        val framebuffer = mc.framebuffer
        val prevFramebuffer = (framebuffer.getColorAttachment() as GlTexture).getOrCreateFramebuffer(
            (RenderSystem.getDevice() as GlBackend).framebufferManager,
            null
        )

        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, prevFramebuffer)

        implGlfw.newFrame()
        implGl3.newFrame()

        ClickGui.applyStyle(lastScale)
        ImGui.newFrame()

        GuiEvent.NewFrame.post()
        ImGui.render()
        GuiEvent.EndFrame.post()

        implGl3.renderDrawData(ImGui.getDrawData())
    }

    fun destroy() {
        implGlfw.shutdown()
        implGl3.shutdown()
        ImGui.destroyContext()
    }

    init {
        ImGui.createContext()

        io.configFlags = DEFAULT_FLAGS
        io.iniFilename = "lambda.ini"

        implGlfw.init(mc.window.handle, true)
        implGl3.init()
    }
}
