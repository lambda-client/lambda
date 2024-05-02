package com.lambda.gui.impl.clickgui

import com.lambda.Lambda.mc
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.LambdaGui
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.module.modules.client.ClickGui

abstract class AbstractClickGui(name: String = "ClickGui") : LambdaGui(name, ClickGui) {
    val windows = ChildLayer<WindowComponent<*>> { child ->
        child == activeWindow && !closing
    }

    private var activeWindow: WindowComponent<*>? = null

    private var closing = false
    var guiAnimation by animation.exp(0.0, 1.0, {
        if (closing) ClickGui.closeSpeed else ClickGui.openSpeed
    }) { !closing }; private set

    private val actionPool = ArrayDeque<() -> Unit>()
    fun scheduleAction(block: () -> Unit) = actionPool.add(block)

    override fun onEvent(e: GuiEvent) {
        while (actionPool.isNotEmpty()) actionPool.last().invoke()

        when (e) {
            is GuiEvent.Show -> {
                activeWindow = null
                closing = false
                guiAnimation = 0.0
            }

            is GuiEvent.Tick -> {
                if (closing && guiAnimation < 0.01) mc.setScreen(null)
            }

            is GuiEvent.MouseClick -> {
                // move active window into foreground
                activeWindow?.let {
                    windows.children.apply {
                        remove(it)
                        add(it)
                    }
                }
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