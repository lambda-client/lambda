package com.lambda.gui.impl.clickgui.buttons

import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.button.ListButton
import com.lambda.gui.impl.clickgui.windows.SettingsWindow
import com.lambda.module.Module
import com.lambda.util.Mouse

class ModuleButton(val module: Module, owner: WindowComponent<*>) : ListButton(owner) {
    override val text get() = module.name
    override val active get() = module.isEnabled

    override fun performClickAction(mouse: Mouse.Button) {
        when (mouse) {
            Mouse.Button.Left -> if (hovered) module.toggle()
            Mouse.Button.Right -> {
                val gui = owner.owner

                gui.scheduleAction {
                    val settingsWindow = SettingsWindow(this, gui)
                    gui.windows.addChild(settingsWindow)
                }
            }
        }
    }

    override fun equals(other: Any?) =
        (other as? ModuleButton)?.module == module

    override fun hashCode() =
        module.hashCode()
}