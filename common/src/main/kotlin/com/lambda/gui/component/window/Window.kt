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
import com.lambda.gui.component.core.LayoutBuilder
import com.lambda.gui.component.core.OutlineRect.Companion.outline
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.window.TitleBar.Companion.titleBar
import com.lambda.gui.component.window.WindowContent.Companion.windowContent
import com.lambda.gui.impl.clickgui.core.AnimatedChild
import com.lambda.util.Mouse
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d

/**
 * Represents a window component
 *
 * Consists of titlebar and content layout
 */
open class Window(
    owner: Layout,
    initialTitle: String = "Untitled",
    initialPosition: Vec2d = Vec2d.ZERO,
    initialSize: Vec2d = Vec2d(110, 350),
    draggable: Boolean = true,
    scrollable: Boolean = true,
    private val minimizing: Minimizing = Minimizing.Relative,
    private val resizable: Boolean = true,
    val autoResize: AutoResize = AutoResize.Disabled
) : Layout(owner) {
    protected val animation = animationTicker()
    private val cursorController = cursorController()

    val titleBar = titleBar(initialTitle, draggable)

    val titleBarBackground by titleBar::backgroundRect
    val contentBackground = rect {
        onUpdate {
            rect = Rect(titleBar.leftBottom, this@Window.rightBottom)
            setColor(ClickGui.backgroundColor)

            leftBottomRadius = ClickGui.roundRadius
            rightBottomRadius = ClickGui.roundRadius

            shade = ClickGui.backgroundShade
        }
    }

    val content = windowContent(scrollable)

    val outlineRect = outline {
        onUpdate {
            position = this@Window.position
            size = this@Window.size

            setColor(ClickGui.outlineColor)

            roundRadius = ClickGui.roundRadius
            glowRadius = ClickGui.outlineWidth * ClickGui.outline.toInt().toDouble()

            shade = ClickGui.outlineShade
        }
    }

    // Actions
    private val expandActions = mutableListOf<Window.() -> Unit>()
    private val minimizeActions = mutableListOf<Window.() -> Unit>()

    /**
     * Sets the action to be performed when the window content gets opened.
     *
     * @param action The action to be performed.
     */
    @LayoutBuilder
    fun <T : Window> T.onWindowExpand(action: T.() -> Unit) {
        expandActions += { action() }
    }

    /**
     * Sets the action to be performed when the window content gets closed.
     *
     * @param action The action to be performed.
     */
    @LayoutBuilder
    fun <T : Window> T.onWindowMinimize(action: T.() -> Unit) {
        minimizeActions += { action() }
    }

    // Position
    // ToDo find a way to animate this only when dragging
    /*private val renderX by animation.exp(position::x, 0.8)
    private val renderY by animation.exp(position::y, 0.8)
    private val renderPosition get() = Vec2d(renderX, renderY)*/

    // Minimizing
    var isMinimized = false; set(value) {
        if (field == value) return
        field = value

        val actions = if (!value) expandActions else minimizeActions
        actions.forEach { it(this) }
    }

    var isExpand
        get() = !isMinimized
        set(value) { isMinimized = !value }

    var windowWidth = initialSize.x
    var windowHeight = initialSize.y

    var widthAnimation by animation.exp(0.8, ::windowWidth)
    var heightAnimation by animation.exp(
        min = { 0.0 },
        max = { if (minimizing == Minimizing.Relative) targetHeight else 1.0 },
        speed = 0.7,
        flag = { !isMinimized }
    )

    val targetHeight get() = if (!autoResize.enabled) windowHeight - titleBar.height else content.height

    // Resizing
    private var resizeX: Double? = null
    private var resizeY: Double? = null
    private var resizeXHovered = false
    private var resizeYHovered = false

    init {
        position = initialPosition
        properties.clampPosition = owner is ScreenLayout

        onUpdate {
            width = widthAnimation
            height = titleBar.height + when (minimizing) {
                Minimizing.Disabled -> targetHeight
                Minimizing.Relative -> heightAnimation
                Minimizing.Absolute -> heightAnimation * targetHeight
            }
        }

        titleBar.onMouseAction(Mouse.Button.Right) {
            // Toggle minimizing state when right-clicking title bar
            if (minimizing == Minimizing.Disabled) return@onMouseAction
            isMinimized = !isMinimized
        }

        content.onUpdate {
            val animatedChildren = content.children
                .filterIsInstance<AnimatedChild>()
                .filter { it.isShown }

            animatedChildren.forEachIndexed { i, it ->
                it.index = i
                it.lastIndex = animatedChildren.lastIndex
            }
        }

        onShow {
            resizeX = null
            resizeY = null
            resizeXHovered = false
            resizeYHovered = false

            heightAnimation = 0.0
            /*heightAnimation = when {
                isMinimized -> 0.0
                minimizing == Minimizing.Relative -> targetHeight
                else -> 1.0
            }*/
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

        // Update resize dragging offsets
        onMouse { resizeX = null; resizeY = null }
        onMouse(Mouse.Button.Left, Mouse.Action.Click) {
            if (resizeXHovered) resizeX = mousePosition.x - width
            if (resizeYHovered) resizeY = mousePosition.y - height
        }

        onMouseMove {
            resizeXHovered = false
            resizeYHovered = false

            if (!resizable || isMinimized) return@onMouseMove

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
                    windowWidth = (mousePosition.x - rx).coerceIn(80.0, 1000.0)
                }

                resizeY?.let { ry ->
                    windowHeight = (mousePosition.y - ry).coerceIn(titleBar.height + RESIZE_RANGE, 1000.0)
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
     * [Relative] -> Animation follows the height of the component ( animation(0.0, height) ) (height change is animated)
     * [Absolute] -> Animation does not depend on the height ( animation(0.0, 1.0) * height ) (height change instantly affects the height)
     */
    enum class Minimizing {
        Disabled,
        Relative,
        Absolute;
    }

    companion object {
        /**
         * Creates new empty [Window]
         *
         * @param position The initial position of the window
         *
         * @param size The initial size of the window
         *
         * @param title The title of the window
         *
         * @param draggable Whether to allow user to drag the window
         *
         * @param scrollable Whether to let user scroll the content
         * This will also make your elements be vertically ordered
         *
         * @param minimizing The [Minimizing] mode.
         *
         * @param resizable Whether to allow user to resize the window
         *
         * @param autoResize Indicates if this window could be automatically resized based on content height
         *
         * @param block Actions to perform within content space of the window
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
