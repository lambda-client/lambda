package com.lambda.module.modules.client

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.newgui.LambdaScreen.Companion.gui
import com.lambda.newgui.LambdaScreen.Companion.toScreen
import com.lambda.newgui.component.window.Window.Companion.window
import com.lambda.util.math.Vec2d

object NewCGui : Module(
    name = "NewCGui",
    description = "ggs",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    val titleBarHeight by setting("Title Bar Height", 4.0, 0.0..10.0, 0.1)
    val padding by setting("Padding", 2.0, 0.0..6.0, 0.1)
    val listStep by setting("List Step", 2.0, 0.0..6.0, 0.1)

    private val clickGuiLayout =
        gui {
            window(position = Vec2d.ONE * 20.0, title = "Test window") {
                repeat(6) {
                    window(Vec2d.ONE * 5.0, Vec2d.ONE * 60.0) {

                    }
                }
            }
        }

    val CLICK_GUI = clickGuiLayout.toScreen("New Click Gui")

    init {
        onEnable {
            CLICK_GUI.show()
        }
    }
}
