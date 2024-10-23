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

package com.lambda.module

import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.animation.AnimationTicker
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.RenderLayer
import com.lambda.gui.api.component.core.DockingRect
import com.lambda.module.tag.ModuleTag
import com.lambda.util.KeyCode
import com.lambda.util.math.Vec2d

abstract class HudModule(
    name: String,
    description: String = "",
    defaultTags: Set<ModuleTag> = setOf(),
    alwaysListening: Boolean = false,
    enabledByDefault: Boolean = false,
    defaultKeybind: KeyCode = KeyCode.UNBOUND,
) : Module(name, description, defaultTags, alwaysListening, enabledByDefault, defaultKeybind) {
    private val renderCallables = mutableListOf<RenderLayer.() -> Unit>()

    protected abstract val width: Double
    protected abstract val height: Double

    private val rectHandler = object : DockingRect() {
        private var relativePosX by setting("Position X", 0.0, -10000.0..10000.0, 0.1) { false }
        private var relativePosY by setting("Position Y", 0.0, -10000.0..10000.0, 0.1) { false }
        override var relativePos
            get() = Vec2d(relativePosX, relativePosY);
            set(value) {
                relativePosX = value.x; relativePosY = value.y
            }

        override val width get() = this@HudModule.width
        override val height get() = this@HudModule.height

        override val autoDocking by setting("Auto Docking", true).apply {
            onValueChange { _, _ ->
                autoDocking()
            }
        }

        override var dockingH by setting("Docking H", HAlign.LEFT) { !autoDocking }.apply {
            onValueChange { from, to ->
                val delta = to.multiplier - from.multiplier
                relativePosX += delta * (size.x - screenSize.x)
            }
        }

        override var dockingV by setting("Docking V", VAlign.TOP) { !autoDocking }.apply {
            onValueChange { from, to ->
                val delta = to.multiplier - from.multiplier
                relativePosY += delta * (size.y - screenSize.y)
            }
        }
    }

    var position by rectHandler::position
    val rect by rectHandler::rect
    val animation = AnimationTicker()

    private val renderer = RenderLayer()

    protected fun onRender(block: RenderLayer.() -> Unit) =
        renderCallables.add(block)

    init {
        listener<RenderEvent.GUI.HUD> { event ->
            rectHandler.screenSize = event.screenSize

            renderCallables.forEach { function ->
                function.invoke(renderer)
            }

            renderer.render()
        }

        listener<TickEvent.Pre> {
            animation.tick()
        }
    }
}
