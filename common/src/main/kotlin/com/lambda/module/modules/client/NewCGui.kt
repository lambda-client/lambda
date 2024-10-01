package com.lambda.module.modules.client

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.newgui.LambdaScreen.Companion.gui
import com.lambda.newgui.LambdaScreen.Companion.toScreen
import com.lambda.newgui.component.window.Window.Companion.window
import com.lambda.util.math.Vec2d
import com.lambda.util.math.setAlpha
import java.awt.Color

object NewCGui : Module(
    name = "NewCGui",
    description = "ggs",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    val titleBarHeight by setting("Title Bar Height", 18.0, 0.0..25.0, 0.1)
    val padding by setting("Padding", 2.0, 1.0..6.0, 0.1)
    val listStep by setting("List Step", 2.0, 0.0..6.0, 0.1)

    val roundRadius by setting("Round Radius", 2.0, 0.0..10.0, 0.1)

    val backgroundColor by setting("Background Color", Color.WHITE.setAlpha(0.4))
    val backgroundShade by setting("Background Shade", true)

    val outline by setting("Outline", true)
    val outlineWidth by setting("Outline Width", 10.0, 1.0..10.0, 0.1) { outline }
    val outlineColor by setting("Outline Color", Color.WHITE.setAlpha(0.6)) { outline }
    val outlineShade by setting("Outline Shade", true) { outline }

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
