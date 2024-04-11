package com.lambda.gui.impl.clickgui.buttons

import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.sub.ButtonComponent
import com.lambda.module.Module
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d

class ModuleButton(val module: Module, owner: WindowComponent<*>) : ButtonComponent(owner) {
    override val position get() = Vec2d(0.0, heightOffset)
    override val size get() = Vec2d(FILL_PARENT, ClickGui.buttonHeight)

    override val text: String get() = module.name
    override val active: Boolean get() = module.isEnabled

    var heightOffset = 0.0

    override fun performClickAction(mouse: Mouse.Button) {
        when (mouse) {
            Mouse.Button.Left -> module.toggle()
            Mouse.Button.Right -> {
                // open settings window
            }
        }
    }
}