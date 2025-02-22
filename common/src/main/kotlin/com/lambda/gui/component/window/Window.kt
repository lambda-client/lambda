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

package com.lambda.gui.component.window

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.module.modules.client.ClickGui
import com.lambda.gui.ScreenLayout
import com.lambda.gui.component.core.FilledRect.Companion.rect
import com.lambda.gui.component.core.OutlineRect.Companion.outline
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.window.TitleBar.Companion.titleBar
import com.lambda.gui.component.window.WindowContent.Companion.windowContent
import com.lambda.util.Mouse
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import com.lambda.util.math.lerp

/**
 * Represents a window component
 *
 * Consists of titlebar and content layout
 */
open class Window(
    owner: Layout,
    initialTitle: String = "Untitled",
    initialPosition: Vec2d = Vec2d.ZERO,
    initialSize: Vec2d = Vec2d(110, 300),
    draggable: Boolean = true,
    scrollable: Boolean = true,
    private val minimizing: Minimizing = Minimizing.Relative,
    private val resizable: Boolean = true,
    val autoResize: AutoResize = AutoResize.Disabled
) : Layout(owner) {
    private val animation = animationTicker()
    private val cursorController = cursorController()

    val titleBar = titleBar(initialTitle, draggable)

    protected val titleBarBackground by titleBar::backgroundRect
    protected val contentBackground = rect { // It's here because content cannot contain something by default
        onUpdate {
            rectangle = Rect(titleBar.leftBottom, this@Window.rightBottom)
            setColor(ClickGui.backgroundColor)

            leftBottomRadius = ClickGui.roundRadius
            rightBottomRadius = ClickGui.roundRadius

            shade = ClickGui.backgroundShade
        }
    }

    val content = windowContent(scrollable)

    protected val outlineRect = outline {
        onUpdate {
            rectangle = this@Window.rect
            setColor(ClickGui.outlineColor)

            roundRadius = ClickGui.roundRadius
            glowRadius = ClickGui.outlineWidth * ClickGui.outline.toInt().toDouble()

            shade = ClickGui.outlineShade
        }
    }

    // Position
    // ToDo find a way to animate this only when dragging
    /*private val renderX by animation.exp(position::x, 0.8)
    private val renderY by animation.exp(position::y, 0.8)
    private val renderPosition get() = Vec2d(renderX, renderY)*/

    // Minimizing
    var minimized = false
    private var heightAnimation by animation.exp(
        min = { 0.0 },
        max = { if (minimizing == Minimizing.Relative) targetHeight else 1.0 },
        speed = 0.8,
        flag = { !minimized }
    )

    private val targetHeight get() = if (!autoResize.enabled) height - titleBar.renderHeight else content.getContentHeight()

    // Resizing
    private var resizeX: Double? = null
    private var resizeY: Double? = null
    private var resizeXHovered = false
    private var resizeYHovered = false

    init {
        position = initialPosition
        size = initialSize

        overrideSize(animation.exp(::width, 0.8)::value) {
            titleBar.renderHeight + when (minimizing) {
                Minimizing.Disabled -> targetHeight
                Minimizing.Relative -> heightAnimation
                Minimizing.Absolute -> heightAnimation * targetHeight
            }
        }

        properties.clampPosition = owner is ScreenLayout
        content.properties.scissor = true

        titleBar.onMouseClick { button, action ->
            // Toggle minimizing state when right-clicking title bar
            if (minimizing == Minimizing.Disabled) return@onMouseClick
            if (button != Mouse.Button.Right || action != Mouse.Action.Click) return@onMouseClick

            minimized = !minimized
        }

        onShow {
            resizeX = null
            resizeY = null
            resizeXHovered = false
            resizeYHovered = false
            heightAnimation = when {
                minimized -> 0.0
                minimizing == Minimizing.Relative -> targetHeight
                else -> 1.0
            }
        }

        onTick {
            // Update cursor
            val rxh = resizeXHovered || resizeX != null
            val ryh = resizeYHovered || resizeY != null

            val cursor = when {
                rxh && ryh -> Mouse.Cursor.ResizeHV
                rxh -> Mouse.Cursor.ResizeH
                ryh -> Mouse.Cursor.ResizeV
                else -> Mouse.Cursor.Arrow
            }

            cursorController.setCursor(cursor)
        }

        onMouseClick { button: Mouse.Button, action: Mouse.Action ->
            // Update resize dragging offsets
            resizeX = null
            resizeY = null

            if (button != Mouse.Button.Left || action != Mouse.Action.Click) return@onMouseClick

            if (resizeXHovered) resizeX = mousePosition.x - renderWidth
            if (resizeYHovered) resizeY = mousePosition.y - renderHeight
        }

        onMouseMove {
            resizeXHovered = false
            resizeYHovered = false

            if (!resizable || minimized) return@onMouseMove

            // Hover state update
            if (selectedChild != titleBar && content.selectedChild == null && isHovered) {
                resizeXHovered = mousePosition in Rect(
                    rightTop - Vec2d(RESIZE_RANGE, 0.0),
                    rightBottom
                )

                resizeYHovered = !autoResize.enabled && mousePosition in Rect(
                    leftBottom - Vec2d(0.0, RESIZE_RANGE),
                    rightBottom
                )
            }

            // Resize
            if (resizeX != null || resizeY != null) {
                resizeX?.let { rx ->
                    width = (mousePosition.x - rx).coerceIn(80.0, 1000.0)
                }

                resizeY?.let { ry ->
                    height = (mousePosition.y - ry).coerceIn(titleBar.renderHeight + RESIZE_RANGE, 1000.0)
                }
            }
        }
    }

    enum class AutoResize(private val isEnabled: () -> Boolean) {
        Disabled({ false }),
        ByConfig({ ClickGui.autoResize }),
        ForceEnabled({ true });

        val enabled get() = isEnabled()
    }

    /**
     * [Disabled] -> No ability to minimize the window
     * [Relative] -> Animation follows the height of the component ( animation(0.0, height) )
     * [Absolute] -> Animation does not depend on the height ( animation(0.0, 1.0) * height )
     */
    enum class Minimizing {
        Disabled,
        Relative,
        Absolute;
    }

    companion object {
        /**
         * Creates a new [Window] instance, adds it to the current layout, and configures it with the specified properties.
         *
         * The window is initialized with the given position, size, title, and behavior settings. Its content can be customized
         * further via the provided lambda.
         *
         * @param position the initial position of the window.
         * @param size the initial size of the window.
         * @param title the title displayed in the window's title bar.
         * @param draggable if true, allows the window to be dragged.
         * @param scrollable if true, enables vertical scrolling for the window’s content.
         * @param minimizing the minimizing behavior of the window (e.g., [Minimizing.Relative]).
         * @param resizable if true, permits the window to be resized.
         * @param autoResize if enabled, allows the window to automatically adjust its size based on its content height.
         * @param block a lambda to configure the window's content.
         */
        @UIBuilder
        fun Layout.window(
            position: Vec2d = Vec2d.ZERO,
            size: Vec2d = Vec2d(120.0, 300.0),
            title: String = "Untitled",
            draggable: Boolean = true,
            scrollable: Boolean = true,
            minimizing: Minimizing = Minimizing.Relative,
            resizable: Boolean = true,
            autoResize: AutoResize = AutoResize.Disabled,
            block: WindowContent.() -> Unit = {}
        ) = Window(
            this, title,
            position, size,
            draggable, scrollable, minimizing, resizable,
            autoResize
        ).apply(children::add).apply {
            block(this.content)
        }

        private const val RESIZE_RANGE = 5.0
    }
}
