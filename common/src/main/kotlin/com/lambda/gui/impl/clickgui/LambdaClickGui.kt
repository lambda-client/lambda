package com.lambda.gui.impl.clickgui

import com.lambda.gui.api.LambdaGui
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.core.IListComponent
import com.lambda.gui.api.component.sub.ButtonComponent
import com.lambda.module.ModuleRegistry
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d

class LambdaClickGui : LambdaGui, IListComponent<WindowComponent<ButtonComponent>> {
    override val name = "Lambda ClickGui"
    override val children = mutableListOf<WindowComponent<ButtonComponent>>()

    init {
        children.add(object : WindowComponent<ButtonComponent>() {
            override val title = "Test Window"
            override var width = 110.0
            override var height = 300.0

            init {
                var buh = 0.0
                val component = this
                ModuleRegistry.modules.forEach { module ->
                    children.add(object : ButtonComponent(component) {
                        override val position = Vec2d(0.0, buh)
                        override val size get() = Vec2d(FILL_PARENT, 11.0)

                        override val text: String get() = module.name
                        override val active: Boolean get() = module.isEnabled

                        override fun performClickAction(mouse: Mouse.Button) {
                            when (mouse) {
                                Mouse.Button.Left -> module.toggle()
                                Mouse.Button.Right -> {
                                    // open settings window
                                }
                            }
                        }
                    })
                    buh += 12.0
                }
            }
        })
    }
}