package com.lambda.gui.impl.clickgui

import com.lambda.Lambda.mc
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.LambdaGui
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.clickgui.windows.SettingsWindow
import com.lambda.module.modules.client.ClickGui

abstract class AbstractClickGui(name: String = "ClickGui") : LambdaGui(name, ClickGui) {
    val windows = ChildLayer<WindowComponent<*>> { child ->
        child == activeWindow && !closing
    }

    private var activeWindow: WindowComponent<*>? = null

    private var closing = false
    var showAnimation by animation.exp(0.0, 1.0, {
        if (closing) ClickGui.closeSpeed else ClickGui.openSpeed
    }) { !closing }; private set

    private val actionPool = ArrayDeque<() -> Unit>()
    fun scheduleAction(block: () -> Unit) = actionPool.add(block)

    override fun onEvent(e: GuiEvent) {
        while (actionPool.isNotEmpty()) actionPool.removeLast().invoke()

        when (e) {
            is GuiEvent.Show -> {
                activeWindow = null
                closing = false
                showAnimation = 0.0

                windows.children
                    .filterIsInstance<SettingsWindow>()
                    .forEach(WindowComponent<*>::destroy)
            }

            is GuiEvent.Tick -> {
                if (closing && showAnimation < 0.01) mc.setScreen(null)
            }

            is GuiEvent.MouseClick -> {
                activeWindow?.focus()
            }

            is GuiEvent.MouseMove -> {
                activeWindow = windows.children.lastOrNull { child ->
                   e.mouse in child.rect
                }
            }
        }

        windows.onEvent(e)
    }

    override fun close() {
        closing = true
    }
}