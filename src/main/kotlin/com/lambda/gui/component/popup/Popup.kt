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

package com.lambda.gui.component.popup

import com.lambda.event.events.GuiEvent
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.component.core.FilledRect.Companion.rect
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.component.layout.Layout.Companion.animationTicker
import com.lambda.gui.component.layout.Layout.Companion.layout
import com.lambda.gui.component.window.Window
import com.lambda.gui.component.window.Window.Companion.window
import com.lambda.module.modules.client.ClickGui.backgroundTint
import com.lambda.util.Mouse
import com.lambda.util.math.multAlpha

open class Popup(
    requestor: Layout,
    title: String
) {
    private val baseHider = LayoutHideService { base }
    protected val windowHider = LayoutHideService { window }

    private val animation = requestor.animationTicker()
    private val tintAnimation by animation.exp(0.0, 1.0, 0.6) { windowHider.isShown }
    private val root = requestor.root

    private val base: Layout = root.layout {
        rect {
            onUpdate {
                width = this@layout.width
                height = this@layout.height
                setColor(backgroundTint.multAlpha(tintAnimation))
            }
        }

        onUpdate {
            properties.interactionPassthrough = !windowHider.isShown
        }
    }.apply {
        destroy()
    }

    /**
     * The shown window on the popup screen
     */
    val window = base.window(
        title = title,
        draggable = false,
        scrollable = false,
        minimizing = Window.Minimizing.Disabled,
        resizable = false,
        autoResize = Window.AutoResize.Disabled
    ).apply {
        destroy()
    }

    fun show() {
        windowHider.isShown = true
        baseHider.isShown = true
        window.onEvent(GuiEvent.Update)
    }

    open fun hide() {
        windowHider.isShown = false
    }

    init {
        requestor.apply {
            root.onMouse(action = Mouse.Action.Click) {
                if (!window.isHovered) hide()
            }

            root.onTick {
                if (tintAnimation == 0.0 && !windowHider.isShown)
                    baseHider.isShown = false
            }

            root.onUpdate {
                base.width = root.width
                base.height = root.height
                window.positionX = (base.width - window.width) * 0.5
                window.positionY = (base.height - window.height) * 0.5 - window.titleBar.height

                baseHider.update()
                windowHider.update()
            }
        }
    }

    abstract class Returnable <V : Any>(
        requestor: Layout,
        title: String
    ) : Popup(requestor, title) {
        private var receiver: ((V) -> Unit)? = null
        protected abstract fun buildOutput(): V

        override fun hide() {
            if (windowHider.isShown) {
                receiver?.invoke(buildOutput())
                receiver = null
            }
            super.hide()
        }

        companion object {
            /**
             * Shows the given [popupLayout].
             *
             * @param block The action to run on the popup exit.
             */
            fun <V: Any> popup(popupLayout: Returnable<V>, block: (V) -> Unit) = popupLayout.apply {
                receiver = block
                show()
            }
        }
    }

    protected class LayoutHideService(val layoutBlock: () -> Layout) {
        private var lastShown = false
        var isShown = false

        fun update() {
            val layout = layoutBlock()
            val owner = layout.owner ?: throw IllegalStateException(
                "Cannot hide root layout."
            )

            val prev = lastShown
            val value = isShown
            lastShown = value

            if (prev == value) return

            if (value) {
                owner.children += layout
                layout.onEvent(GuiEvent.Show)
            } else {
                owner.children -= layout
                layout.onEvent(GuiEvent.Hide)
            }
        }
    }
}