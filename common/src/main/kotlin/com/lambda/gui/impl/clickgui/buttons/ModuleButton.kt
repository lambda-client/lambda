package com.lambda.gui.impl.clickgui.buttons

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.button.ListButton
import com.lambda.gui.impl.clickgui.windows.SettingsWindow
import com.lambda.module.Module
import com.lambda.util.Mouse

class ModuleButton(val module: Module, owner: WindowComponent<*>) : ListButton(owner) {
    override val text get() = module.name
    override val active get() = module.isEnabled
    private val gui = owner.owner

    private val settingsWindow = SettingsWindow(this, gui)

    override fun performClickAction(e: GuiEvent.MouseClick) {
        when (e.button) {
            Mouse.Button.Left -> if (hovered) module.toggle()
            Mouse.Button.Right -> {
                gui.scheduleAction {
                    gui.windows.addChild(settingsWindow.apply {
                        position = e.mouse
                    })
                }
            }
        }
    }

    override fun equals(other: Any?) =
        (other as? ModuleButton)?.module == module

    override fun hashCode() =
        module.hashCode()
}