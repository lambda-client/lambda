package com.lambda.gui.impl.clickgui

import com.lambda.Lambda.mc
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.LambdaGui
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.gui.impl.clickgui.windows.ModuleWindow
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.Mouse
import com.mojang.blaze3d.systems.RenderSystem.recordRenderCall

abstract class AbstractClickGui(name: String = "ClickGui") : LambdaGui(name, ClickGui) {
    private var activeWindow: WindowComponent<*>? = null
    private var closing = false

    final override var childShowAnimation by animation.exp(0.0, 1.0, {
        if (closing) ClickGui.closeSpeed else ClickGui.openSpeed
    }) { !closing }; private set

    val windows = ChildLayer<WindowComponent<*>, AbstractClickGui>(this, this, ::rect) { child ->
        child == activeWindow && !closing
    }

    private val actionPool = ArrayDeque<() -> Unit>()
    fun scheduleAction(block: () -> Unit) = actionPool.add(block)

    override fun onEvent(e: GuiEvent) {
        while (actionPool.isNotEmpty()) actionPool.removeLast().invoke()

        when (e) {
            is GuiEvent.Show -> {
                activeWindow = null
                closing = false
                childShowAnimation = 0.0
            }

            is GuiEvent.Tick -> {
                if (closing && childShowAnimation < 0.01) mc.setScreen(null)
            }

            is GuiEvent.MouseClick -> {
                if (e.action == Mouse.Action.Click) activeWindow?.focus()
            }

            is GuiEvent.MouseMove -> {
                activeWindow = windows.children.lastOrNull { child ->
                   e.mouse in child.rect
                }
            }
        }

        windows.onEvent(e)
    }

    fun showWindow(window: WindowComponent<*>) {
        // we have to wait some time to place this window over other ones
        recordRenderCall {
            windows.children.add(window)
        }
    }

    fun unfocusSettings() {
        windows.children.filterIsInstance<ModuleWindow>().forEach { moduleWindow ->
            moduleWindow.contentComponents.children.forEach { moduleButton ->
                moduleButton.settingsLayer.children.forEach(SettingButton<*, *>::unfocus)
            }
        }
    }

    override fun close() {
        if (!isOpen) return
        closing = true
    }
}