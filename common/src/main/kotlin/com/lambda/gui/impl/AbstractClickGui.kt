/*
 * Copyright 2024 Lambda
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

package com.lambda.gui.impl

import com.lambda.Lambda.mc
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.graphics.buffer.FrameBuffer
import com.lambda.graphics.shader.Shader
import com.lambda.gui.AbstractGuiConfigurable
import com.lambda.gui.GuiConfigurable
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.LambdaGui
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.gui.impl.clickgui.windows.ModuleWindow
import com.lambda.gui.impl.clickgui.windows.tag.CustomModuleWindow
import com.lambda.gui.impl.clickgui.windows.tag.TagWindow
import com.lambda.module.Module
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.Mouse
import kotlin.reflect.KMutableProperty0

abstract class AbstractClickGui(name: String, owner: Module? = null) : LambdaGui(name, owner) {
    protected var hoveredWindow: WindowComponent<*>? = null
    protected var closing = false

    final override var childShowAnimation by animation.exp(
        0.0, 1.0,
        { if (closing) ClickGui.closeSpeed else ClickGui.openSpeed }
    ) { !closing }; private set

    val windows = ChildLayer<WindowComponent<*>, AbstractClickGui>(this, this, ::rect) { child ->
        child == hoveredWindow && !closing
    }

    private val frameBuffer = FrameBuffer()
    private val shader = Shader("post/cgui_animation", "renderer/pos_tex")

    abstract val moduleFilter: (Module) -> Boolean
    abstract val configurable: AbstractGuiConfigurable

    private var lastTickedUpdate = 0L

    private val actionPool = ArrayDeque<() -> Unit>()
    fun scheduleAction(block: () -> Unit) = actionPool.add(block)

    override fun onEvent(e: GuiEvent) {
        while (actionPool.isNotEmpty()) actionPool.removeLast().invoke()

        when (e) {
            is GuiEvent.Render -> {
                if (childShowAnimation < 0.99) {
                    frameBuffer.write {
                        windows.onEvent(e)
                    }.read(shader) {
                        it["u_Progress"] = childShowAnimation
                    }

                    return
                }
            }

            is GuiEvent.Show -> {
                hoveredWindow = null
                closing = false
                childShowAnimation = 0.0
                updateWindows()
            }

            is GuiEvent.Tick -> {
                val time = System.currentTimeMillis()
                if (time - lastTickedUpdate > 1000L) {
                    lastTickedUpdate = time
                    updateWindows()
                }

                if (closing && childShowAnimation < 0.01) mc.setScreen(null)
            }

            is GuiEvent.MouseClick -> {
                if (e.action == Mouse.Action.Click) hoveredWindow?.focus()
            }

            is GuiEvent.MouseMove -> {
                hoveredWindow = windows.children.lastOrNull { child ->
                    e.mouse in child.rect
                }
            }
        }

        windows.onEvent(e)
    }

    fun unfocusSettings() {
        windows.children.filterIsInstance<ModuleWindow>().forEach { moduleWindow ->
            moduleWindow.contentComponents.children.forEach { moduleButton ->
                moduleButton.settingsLayer.children.forEach(SettingButton<*, *>::unfocus)
            }
        }
    }

    private inline fun <reified T : ModuleWindow> syncWindows(prop: KMutableProperty0<MutableList<T>>) = windows.apply {
        var configWindows by prop

        // Add windows from config
        configWindows.filter { it !in children }.forEach(children::add)

        // Remove outdated/deleted windows
        children.removeIf {
            it is T && it !in configWindows
        }

        // Update config
        configWindows = children.filterIsInstance<T>().toMutableList()
    }

    private fun updateWindows() {
        syncWindows<TagWindow>(configurable::mainWindows)

        (configurable as? GuiConfigurable)?.let {
            syncWindows<CustomModuleWindow>(it::customWindows)
        }
    }

    override fun close() {
        if (!isOpen) return
        closing = true
    }
}
