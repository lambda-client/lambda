package com.lambda.gui.impl.clickgui.buttons

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.button.ListButton
import com.lambda.gui.impl.clickgui.windows.SettingsWindow
import com.lambda.module.Module
import com.lambda.util.Mouse
import com.mojang.blaze3d.systems.RenderSystem.recordRenderCall

class ModuleButton(val module: Module, owner: WindowComponent<*>) : ListButton(owner) {
    override val text get() = module.name
    override val active get() = module.isEnabled
    private val gui = owner.owner

    override fun performClickAction(e: GuiEvent.MouseClick) {
        when (e.button) {
            Mouse.Button.Left -> if (hovered) module.toggle()
            Mouse.Button.Right -> {
                gui.apply {
                    // Open new settings window or move existing one to the cursor
                    val settingsWindow = windows.children.filterIsInstance<SettingsWindow>()
                        .firstOrNull { it.button == this@ModuleButton }?.apply {
                            position = e.mouse
                        } ?: SettingsWindow(this@ModuleButton, this).apply {
                            forceSetPosition(e.mouse)

                            scheduleAction {
                                windows.addChild(this)
                            }
                        }

                    // we have to wait this tag window to be focused after handling a click event
                    // to place settings window over it
                    recordRenderCall {
                        settingsWindow.focus()
                    }
                }
            }
        }
    }

    override fun equals(other: Any?) =
        (other as? ModuleButton)?.module == module

    override fun hashCode() =
        module.hashCode()
}