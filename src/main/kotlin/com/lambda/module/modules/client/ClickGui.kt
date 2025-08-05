/*
 * Copyright 2025 Lambda
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

import com.lambda.Lambda
import com.lambda.event.events.KeyboardEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.gui.LambdaScreen
import com.lambda.module.Module
import com.lambda.module.modules.player.InventoryMove.hasInputOrNull
import com.lambda.module.tag.ModuleTag
import com.lambda.util.KeyCode
import com.lambda.util.math.setAlpha
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.AnvilScreen
import net.minecraft.client.gui.screen.ingame.CommandBlockScreen
import net.minecraft.client.gui.screen.ingame.SignEditScreen
import net.minecraft.text.Text
import java.awt.Color

object ClickGui : Module(
    name = "ClickGui",
    description = "ImGui",
    tag = ModuleTag.CLIENT,
    defaultKeybind = KeyCode.Y,
) {
    val titleBarHeight by setting("Title Bar Height", 18.0, 10.0..25.0, 0.1)
    val moduleHeight by setting("Module Height", 16.0, 10.0..25.0, 0.1)
    val settingsHeight by setting("Settings Height", 16.0, 10.0..25.0, 0.1)
    val padding by setting("Padding", 1.0, 1.0..6.0, 0.1)
    val listStep by setting("List Step", 1.0, 0.0..6.0, 0.1)
    val autoResize by setting("Auto Resize", false)

    val roundRadius by setting("Round Radius", 3.0, 0.0..10.0, 0.1)

    val backgroundTint by setting("Background Tint", Color.BLACK.setAlpha(0.4))
    val backgroundCornerTint by setting("Background Corner Tint", Color.WHITE.setAlpha(0.4))
    val cornerTintWidth by setting("Corner Tint Width", 100.0, 1.0..500.0, 0.1)
    val cornerTintShade by setting("Corner Tint Shade", true)

    val titleBackgroundColor by setting("Title Background Color", Color(80, 80, 80))
    val backgroundColor by setting("Background Color", titleBackgroundColor)
    val backgroundShade by setting("Background Shade", true)

    val outline by setting("Outline", true)
    val outlineWidth by setting("Outline Width", 0.5, 0.5..5.0, 0.1) { outline }
    val outlineColor by setting("Outline Color", Color.WHITE) { outline }
    val outlineShade by setting("Outline Shade", true) { outline }

    val glow by setting("Glow", true)
    val glowWidth by setting("Glow Width", 8.0, 1.0..20.0, 0.1) { glow }
    val glowColor by setting("Glow Color", Color.WHITE.setAlpha(0.35)) { glow }
    val glowShade by setting("Glow Shade", true) { glow }

    val fontScale by setting("Font Scale", 1.0, 0.5..2.0, 0.1)
    val fontOffset by setting("Font Offset", 4.0, 0.0..5.0, 0.1)
    val dockingGridSize by setting("Docking Grid Size", 1.0, 0.1..10.0, 0.1)

    val moduleEnabledColor by setting("Module Enabled Color", Color.WHITE.setAlpha(0.4))
    val moduleDisabledColor by setting("Module Disabled Color", Color.WHITE.setAlpha(0.0))
    val moduleHoverAccent by setting("Module Hover Accent", 0.15, 0.0..0.3, 0.01)
    val moduleOpenAccent by setting("Module Open Accent", 0.3, 0.0..0.5, 0.01)

    val multipleSettingWindows by setting("Multiple Setting Windows", false)

    val hudPadding by setting("Hud Padding", 3.0, 0.0..10.0, 0.1)

    val Screen?.hasInput: Boolean
        get() = this is ChatScreen ||
                this is SignEditScreen ||
                this is AnvilScreen ||
                this is CommandBlockScreen

    init {
        /*listenUnsafe<KeyboardEvent.Press> {
            if (it.translated == keybind && it.isReleased)
                toggle()
        }*/

        onEnable {
            // When there is a screen active, we don't want to replace the screen because it will interfere with the
            // game.
            if (!mc.currentScreen.hasInput)
                mc.setScreen(LambdaScreen)
        }
    }
}
