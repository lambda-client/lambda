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

import com.lambda.event.events.ClientEvent
import com.lambda.event.events.KeyPressEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import com.lambda.gui.impl.clickgui.LambdaClickGui
import com.lambda.gui.impl.hudgui.LambdaHudGui
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.KeyCode

object ClickGui : Module(
    name = "ClickGui",
    description = "Sexy",
    defaultTags = setOf(ModuleTag.CLIENT),
    defaultKeybind = KeyCode.RIGHT_SHIFT
) {
    // General
    val windowRadius by setting("Window Radius", 2.0, 0.0..10.0, 0.1)
    val glowRadius by setting("Glow Radius", 2.0, 0.0..20.0, 0.1)
    val buttonRadius by setting("Button Radius", 0.0, 0.0..10.0, 0.1)
    val windowPadding by setting("Window Padding", 2.0, 0.0..10.0, 0.1)
    val buttonHeight by setting("Button Height", 11.0, 8.0..20.0, 0.1)
    val buttonStep by setting("Button Step", 0.0, 0.0..5.0, 0.1)
    val settingsFontScale by setting("Settings Font Scale", 0.92, 0.5..1.0, 0.01)

    // Animation
    val openSpeed by setting("Open Speed", 0.5, 0.1..1.0, 0.01)
    val closeSpeed by setting("Close Speed", 0.5, 0.1..1.0, 0.01)
    val scrollSpeed by setting("Scroll Speed", 1.0, 0.1..10.0, 0.01)

    // Alignment
    val allowHAlign by setting("Allow H Docking", false)
    val allowVAlign by setting("Allow V Docking", true)
    val dockingGridSize by setting("Docking Grid Size", 1.0, 0.0..20.0, 0.5)

    init {
        onEnable {
            LambdaClickGui.show()
        }

        onDisable {
            LambdaClickGui.close()
            LambdaHudGui.close()
        }

        listener<KeyPressEvent>(priority = Int.MAX_VALUE) { event ->
            if (mc.options.commandKey.isPressed) return@listener
            if (keybind == KeyCode.UNBOUND) return@listener
            if (event.translated != keybind) return@listener
            // ToDo: Exception for ui text input
            toggle()
        }

        unsafeListener<ClientEvent.Shutdown> {
            disable()
        }
    }
}
