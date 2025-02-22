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

package com.lambda.gui.impl.clickgui

import com.lambda.module.tag.ModuleTag
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.component.window.Window
import com.lambda.gui.component.window.WindowContent
import com.lambda.gui.impl.clickgui.ModuleLayout.Companion.moduleLayout
import com.lambda.module.ModuleRegistry
import com.lambda.util.math.Vec2d

class ModuleWindow(
    owner: Layout,
    val tag: ModuleTag, // todo: tag system
    initialPosition: Vec2d
) : Window(owner, tag.name, initialPosition, minimizing = Minimizing.Absolute, autoResize = AutoResize.ByConfig) {
    init {
        ModuleRegistry.modules
            .filter { it.defaultTags.firstOrNull() == tag }
            .map { module -> content.moduleLayout(module) }
            .let { moduleLayouts ->
                moduleLayouts.forEachIndexed { i, it ->
                    it.isLast = moduleLayouts.lastIndex == i
                }
            }
    }

    companion object {
        /**
         * Creates and attaches a [ModuleWindow] to the current layout.
         *
         * This extension function instantiates a [ModuleWindow] with the provided module [tag]
         * and optional initial [position]. It adds the new window to the layout's children and applies
         * the given [block] to configure the window's [WindowContent].
         *
         * @param tag the module tag used to filter the modules displayed in the window.
         * @param position the window's initial position; defaults to [Vec2d.ZERO].
         * @param block an optional lambda for configuring the window's content.
         */
        @UIBuilder
        fun Layout.moduleWindow(
            tag: ModuleTag,
            position: Vec2d = Vec2d.ZERO,
            block: WindowContent.() -> Unit = {}
        ) = ModuleWindow(this, tag, position).apply(children::add).apply {
            block(this.content)
        }
    }
}
