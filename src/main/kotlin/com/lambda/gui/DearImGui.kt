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
import com.lambda.gui.dsl.ImGuiBuilder
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

object DearImGui : Loadable {
    val implGlfw = ImGuiImplGlfw()
    val implGl3 = ImGuiImplGl3()

    val io: ImGuiIO get() = ImGui.getIO()
    const val DEFAULT_FLAGS = ImGuiConfigFlags.NavEnableKeyboard or // Enable Keyboard Controls
            ImGuiConfigFlags.NavEnableSetMousePos or // Move the cursor using the keyboard
            ImGuiConfigFlags.DockingEnable

    fun render(block: ImGuiBuilder.() -> Unit) {
        // Minecraft will not bind the framebuffer unless it is needed, so do it manually and hope Vulcan never gets real:tm:
        val framebuffer = mc.framebuffer
        val prevFramebuffer = (framebuffer.getColorAttachment() as GlTexture).getOrCreateFramebuffer((RenderSystem.getDevice() as GlBackend).framebufferManager, null)

        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, prevFramebuffer)
        glViewport(0, 0, framebuffer.textureWidth, framebuffer.textureHeight)

        implGlfw.newFrame()
        implGl3.newFrame()
        ImGui.newFrame()

        ImGuiBuilder.block()

        ImGui.render()
        implGl3.renderDrawData(ImGui.getDrawData())

        GlStateManager._glBindFramebuffer(GL_FRAMEBUFFER, prevFramebuffer)
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
        (13..24).forEach { size ->
            io.fonts.addFontFromFileTTF("fonts/FiraSans-Bold.ttf".path, size.toFloat())
            io.fonts.addFontFromFileTTF("fonts/FiraSans-Regular.ttf".path, size.toFloat())
        }
        io.fonts.build()

        implGlfw.init(mc.window.handle, true)
        implGl3.init()
    }
}
