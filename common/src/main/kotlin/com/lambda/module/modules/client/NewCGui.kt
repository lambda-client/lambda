package com.lambda.module.modules.client

import com.lambda.Lambda.mc
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.newgui.LambdaScreen.Companion.gui
import com.lambda.newgui.LambdaScreen.Companion.toScreen
import com.lambda.newgui.component.HAlign
import com.lambda.newgui.component.core.TextField.Companion.textField
import com.lambda.newgui.component.window.Window.Companion.window
import com.lambda.util.math.Vec2d
import java.awt.Color

object NewCGui : Module(
    name = "NewCGui",
    description = "ggs",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    val titleBarHeight by setting("Title Bar Height", 4.0, 0.0..10.0, 0.1)

    private val clickGuiLayout =
        gui {
            window(position = Vec2d.ONE * 20.0, title = "Test window") {
                titleBar.textField.apply {
                    text = "Overriding the title"

                    // Making it align the left corner and have 3px offset from the left side
                    alignment = HAlign.LEFT
                    offset = 3.0
                }

                textField("Text field over the window")

                content.textField("Text field inside of the content region") {
                    alignment = HAlign.CENTER
                    scale = 0.5

                    onTick {
                        // Dynamically updating states
                        color = if (mc.player?.isDead == true) Color.RED else Color.GREEN
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
