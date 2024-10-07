package com.lambda.module.modules.client

import com.lambda.module.Module
import com.lambda.module.ModuleRegistry
import com.lambda.module.tag.ModuleTag
import com.lambda.newgui.ScreenLayout.Companion.gui
import com.lambda.newgui.impl.clickgui.ModuleLayout.Companion.moduleLayout
import com.lambda.newgui.impl.clickgui.ModuleWindow.Companion.moduleWindow
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
    val autoResize by setting("Auto Resize", false)

    val roundRadius by setting("Round Radius", 2.0, 0.0..10.0, 0.1)

    val titleBackgroundColor by setting("Title Background Color", Color.WHITE.setAlpha(0.4))
    val backgroundColor by setting("Background Color", Color.WHITE.setAlpha(0.25))
    val backgroundShade by setting("Background Shade", true)

    val outline by setting("Outline", true)
    val outlineWidth by setting("Outline Width", 10.0, 1.0..10.0, 0.1) { outline }
    val outlineColor by setting("Outline Color", Color.WHITE.setAlpha(0.6)) { outline }
    val outlineShade by setting("Outline Shade", true) { outline }

    private val SCREEN get() = gui("New Click Gui") {
        val tags = ModuleTag.defaults
        val modules = ModuleRegistry.modules

        tags.forEachIndexed { i, tag ->
            val windowPosition = Vec2d.ONE * 20.0 + Vec2d.RIGHT * ((115.0 * i) + (i + 1) * 4)

            moduleWindow(tag, windowPosition) {
                modules.filter {
                    it.defaultTags.firstOrNull() == tag
                }.forEach { module ->
                    moduleLayout(module)
                }
            }
        }
    }

    init {
        onEnable {
            SCREEN.show()
            toggle()
        }
    }
}
