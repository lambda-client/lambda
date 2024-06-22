package com.lambda.gui.impl

import com.lambda.Lambda.mc
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.graphics.buffer.FrameBuffer
import com.lambda.graphics.shader.Shader
import com.lambda.gui.AbstractGuiConfigurable
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.LambdaGui
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.gui.impl.clickgui.windows.ModuleWindow
import com.lambda.gui.impl.clickgui.windows.tag.CustomModuleWindow
import com.lambda.gui.impl.clickgui.windows.tag.TagWindow
import com.lambda.gui.impl.hudgui.LambdaHudGui
import com.lambda.module.Module
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.Mouse
import com.mojang.blaze3d.systems.RenderSystem.recordRenderCall

abstract class AbstractClickGui(name: String, owner: Module? = null) : LambdaGui(name, owner) {
    protected var hoveredWindow: WindowComponent<*>? = null
    protected var closing = false

    final override var childShowAnimation by animation.exp(0.0, 1.0, {
        if (closing) ClickGui.closeSpeed else ClickGui.openSpeed
    }) { !closing }; private set

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
                frameBuffer.write {
                    windows.onEvent(e)
                }.read(shader) {
                    it["u_Progress"] = childShowAnimation
                }

                return
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

    private inline fun <reified T : ModuleWindow> syncWindows(configWindows: MutableList<T>) = windows.apply {
        // Add windows from config
        configWindows.filter { it !in children }.forEach(children::add)

        // Remove outdated/deleted windows
        children.removeIf {
            it is T && it !in configWindows
        }

        // Update config
        configWindows.clear()
        configWindows.addAll(children.filterIsInstance<T>())
    }

    fun updateWindows() {
        syncWindows<TagWindow>(configurable.mainWindows)

        if (this != LambdaHudGui) {
            syncWindows<CustomModuleWindow>(configurable.customWindows)
        }
    }

    override fun close() {
        if (!isOpen) return
        closing = true
    }
}