package com.lambda.newgui.component.window

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.module.modules.client.NewCGui
import com.lambda.newgui.component.VAlign
import com.lambda.newgui.component.layout.Layout
import com.lambda.newgui.component.core.UIBuilder
import com.lambda.newgui.component.window.TitleBar.Companion.titleBar
import com.lambda.newgui.component.window.WindowContent.Companion.windowContent
import com.lambda.util.Mouse
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import com.lambda.util.math.coerceIn
import java.awt.Color

/**
 * Represents a window component
 *
 * Consists of titlebar and content layout
 */
open class Window(
    owner: Layout,
    initialTitle: String,
    initialPosition: Vec2d,
    initialSize: Vec2d,
    draggable: Boolean,
    scrollable: Boolean,
    private val minimizable: Boolean,
    private val resizable: Boolean
) : Layout(owner, false, true) {
    val titleBar = titleBar(initialTitle, draggable)
    val content = windowContent(scrollable)

    private val animation = animationTicker()
    private val cursorController = cursorController()

    init {
        position = initialPosition
        size = initialSize
    }

    // Position
    // ToDo find a way to animate this only when dragging
    /*private val renderX by animation.exp(position::x, 0.8)
    private val renderY by animation.exp(position::y, 0.8)
    private val renderPosition get() = Vec2d(renderX, renderY)*/

    // Size
    private val renderWidth by animation.exp({ size.x }, 0.8)
    private val renderHeight by animation.exp(::targetHeight, 0.8)
    private val targetHeight get() = (if (minimized) 0.0 else size.y).coerceAtLeast(titleBar.size.y)

    // Minimizing
    var minimized = false

    // Resizing
    private var resizeX: Double? = null
    private var resizeY: Double? = null
    private var resizeXHovered = false
    private var resizeYHovered = false

    init {
        // Clamp the window only within the screen bounds
        properties.clampPosition = owner.owner == null

        overrideSize {
            Vec2d(renderWidth, renderHeight)
        }

        with(titleBar) {
            onRender {
                // Update title bar position
                val heightVec = Vec2d(0.0, textField.textHeight * 1.5)
                rect = Rect(this@Window.rect.leftTop, this@Window.rect.rightTop + heightVec)
            }

            onMouseClick { button, action ->
                // Toggle minimizing state when right-clicking title bar
                if (!minimizable) return@onMouseClick
                if (button != Mouse.Button.Right || action != Mouse.Action.Click) return@onMouseClick

                minimized = !minimized
            }
        }

        with(content) {
            properties.scissor = true

            onRender {
                // Update content position
                rect = Rect(
                    titleBar.rect.leftBottom + NewCGui.padding,
                    this@Window.rect.rightBottom - NewCGui.padding
                )
            }
        }

        onShow {
            resizeX = null
            resizeY = null
            resizeXHovered = false
            resizeYHovered = false
        }

        onHide {
            cursorController.reset()
        }

        onRender {
            // Render window background
            filled.build(
                rect,
                2.0,
                Color(50, 50, 50),
                shade = true
            )

            // Render outline
            outline.build(
                rect,
                2.0,
                1.0,
                Color.WHITE,
                true
            )
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

            if (resizeXHovered) resizeX = mousePosition.x - size.x
            if (resizeYHovered) resizeY = mousePosition.y - size.y
        }

        onMouseMove {
            resizeXHovered = false
            resizeYHovered = false

            if (!resizable || minimized) return@onMouseMove

            // Hover state update
            if (selectedChild == null && isHovered) {
                resizeXHovered = mousePosition in Rect(
                    titleBar.rect.rightTop - Vec2d(RESIZE_RANGE, 0.0),
                    rect.rightBottom
                )

                resizeYHovered = mousePosition in Rect(
                    rect.leftBottom - Vec2d(0.0, RESIZE_RANGE),
                    rect.rightBottom
                )
            }

            // Resize
            if (resizeX != null || resizeY != null) {
                val x = resizeX?.let { rx ->
                    mousePosition.x - rx
                } ?: size.x

                val y = resizeY?.let { ry ->
                    mousePosition.y - ry
                } ?: size.y

                size = Vec2d(x, y).coerceIn(
                    80.0, 1000.0,
                    titleBar.size.y + RESIZE_RANGE, 1000.0
                )
            }
        }
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
         * @param scrollable Whether to allow user to scroll the elements
         * Note: applies to elements with [VAlign.TOP] only
         *
         * @param minimizable Whether to allow user to minimize the window
         *
         * @param resizable Whether to allow user to resize the window
         *
         * @param block Actions to perform within content space of the window
         */
        @UIBuilder
        fun Layout.window(
            position: Vec2d = Vec2d.ZERO,
            size: Vec2d = Vec2d(100.0, 300.0),
            title: String = "Untitled",
            draggable: Boolean = true,
            scrollable: Boolean = true,
            minimizable: Boolean = true,
            resizable: Boolean = true,
            block: WindowContent.() -> Unit = {}
        ) = Window(
            this, title,
            position, size,
            draggable, scrollable, minimizable, resizable
        ).apply(children::add).apply {
            block(this.content)
        }

        private const val RESIZE_RANGE = 5.0
    }
}