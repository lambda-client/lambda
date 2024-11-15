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

import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.graphics.animation.AnimationTicker
import com.lambda.gui.impl.clickgui.LambdaClickGui
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import java.awt.Color

object GuiSettings : Module(
    name = "GuiSettings",
    description = "Visual behaviour configuration",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    private val page by setting("Page", Page.General)

    // General
    private val scaleSetting by setting("Scale", 100, 50..300, 1, unit = "%", visibility = { page == Page.General }).apply {
        onValueSet { _, _ ->
            lastChange = System.currentTimeMillis()
        }
    }

    // Colors
    val primaryColor by setting("Primary Color", Color(130, 200, 255), visibility = { page == Page.Colors })
    val secondaryColor by setting("Secondary Color", Color(225, 130, 225), visibility = { page == Page.Colors })
    val backgroundColor by setting("Background Color", Color(50, 50, 50, 150), visibility = { page == Page.Colors })
    val shade by setting("Shade", true, visibility = { page == Page.Colors })
    val shadeBackground by setting("Shade Background", true, visibility = { page == Page.Colors })
    val colorWidth by setting("Shade Width", 400.0, 10.0..1000.0, 10.0, visibility = { page == Page.Colors })
    val colorHeight by setting("Shade Height", 400.0, 10.0..1000.0, 10.0, visibility = { page == Page.Colors })
    val colorSpeed by setting("Color Speed", 1.0, 0.1..10.0, 0.1, visibility = { page == Page.Colors })

    val mainColor: Color get() = if (shade) Color.WHITE else primaryColor

    val shadeColor1 get() = primaryColor
    val shadeColor2 get() = secondaryColor

    enum class Page {
        General,
        Colors
    }

    private var targetScale = 2.0
        get() {
            val update = System.currentTimeMillis() - lastChange > 200 || !LambdaClickGui.isOpen
            if (update) field = scaleSetting / 100.0 * 2.0
            return field
        }

    private val animation = with(AnimationTicker()) {
        unsafeListener<TickEvent.Pre>(alwaysListen = true) {
            tick()
        }

        exp({ targetScale }, 0.5).apply {
            unsafeListener<ConnectionEvent.Connect.Pre>(alwaysListen = true) {
                setValue(targetScale)
            }
        }
    }

    private var lastChange = 0L
    val scale by animation
}
