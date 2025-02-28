/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.module.modules.client

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.gui.ScreenLayout.Companion.gui
import com.lambda.gui.component.core.FilledRect.Companion.rect
import com.lambda.gui.impl.clickgui.ModuleWindow.Companion.moduleWindow
import com.lambda.util.math.Vec2d
import com.lambda.util.math.setAlpha
import java.awt.Color

object ClickGui : Module(
    name = "ClickGui",
    description = "sexy again",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    val titleBarHeight by setting("Title Bar Height", 18.0, 10.0..25.0, 0.1)
    val moduleHeight by setting("Module Height", 16.0, 10.0..25.0, 0.1)
    val settingsHeight by setting("Settings Height", 16.0, 10.0..25.0, 0.1)
    val padding by setting("Padding", 1.0, 1.0..6.0, 0.1)
    val listStep by setting("List Step", 1.0, 0.0..6.0, 0.1)
    val autoResize by setting("Auto Resize", false)

    val roundRadius by setting("Round Radius", 3.0, 0.0..10.0, 0.1)

    val backgroundTint by setting("Background Tint", Color.BLACK.setAlpha(0.4))

    val titleBackgroundColor by setting("Title Background Color", Color(60, 60, 60))
    val backgroundColor by setting("Background Color", titleBackgroundColor)
    val backgroundShade by setting("Background Shade", true)

    val outline by setting("Outline", true)
    val outlineWidth by setting("Outline Width", 6.0, 1.0..10.0, 0.1) { outline }
    val outlineColor by setting("Outline Color", Color.WHITE.setAlpha(0.6)) { outline }
    val outlineShade by setting("Outline Shade", true) { outline }
    val fontScale by setting("Font Scale", 1.0, 0.5..2.0, 0.1)
    val fontOffset by setting("Font Offset", 4.0, 0.0..5.0, 0.1)
    val dockingGridSize by setting("Docking Grid Size", 1.0, 0.1..10.0, 0.1)

    val moduleEnabledColor by setting("Module Enabled Color", Color.WHITE.setAlpha(0.5))
    val moduleDisabledColor by setting("Module Disabled Color", Color.WHITE.setAlpha(0.0))
    val moduleHoverAccent by setting("Module Hover Accent", 0.15, 0.0..0.3, 0.01)
    val moduleOpenAccent by setting("Module Open Accent", 0.3, 0.0..0.5, 0.01)

    val multipleSettingWindows by setting("Multiple Setting Windows", false)

    val SCREEN get() = gui("Click Gui") {
        rect {
            onUpdate {
                rectangle = owner!!.rect
                setColor(backgroundTint)
            }
        }

        var x = 10.0
        val y = x

        ModuleTag.defaults.forEach { tag ->
            x += moduleWindow(tag, Vec2d(x, y)).renderWidth + 5
        }
    }

    init {
        onEnable {
            SCREEN.show()
            toggle()
        }
    }
}
