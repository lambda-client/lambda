package com.lambda.newgui.component.window

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

    // Minimizing
    var minimized = true

    // Resizing
    private var resizeX: Double? = null
    private var resizeY: Double? = null

    init {
        position = initialPosition
        size = initialSize

        // Clamp the window only within the screen bounds
        properties.clampPosition = owner.owner == null

        onShow {
            with(content) {
                properties.scissorChildren = true

                rectUpdate {
                    Rect(
                        titleBar.rect.leftBottom + NewCGui.padding,
                        this@Window.rect.rightBottom - NewCGui.padding
                    )
                }
            }

            resizeX = null
            resizeY = null
        }

        onRender {
            filled.build(
                rect,
                2.0,
                Color(50, 50, 50),
                shade = true
            )

            outline.build(
                rect,
                2.0,
                1.0,
                Color.WHITE,
                true
            )
        }

        onMouseClick { button: Mouse.Button, action: Mouse.Action ->
            resizeX = null
            resizeY = null

            if (!resizable) return@onMouseClick
            if (selectedChild != null) return@onMouseClick
            if (button != Mouse.Button.Left || action != Mouse.Action.Click) return@onMouseClick

            val resizeXHovered = mousePosition in Rect(
                titleBar.rect.rightTop - Vec2d(RESIZE_RANGE, 0.0),
                rect.rightBottom
            )

            val resizeYHovered = mousePosition in Rect(
                rect.leftBottom - Vec2d(0.0, RESIZE_RANGE),
                rect.rightBottom
            )

            if (resizeXHovered) resizeX = mousePosition.x - size.x
            if (resizeYHovered) resizeY = mousePosition.y - size.y
        }

        onMouseMove {
            if (resizeX == null && resizeY == null) return@onMouseMove

            val x = resizeX?.let { rx ->
                mousePosition.x - rx
            } ?: size.x

            val y = resizeY?.let { ry ->
                mousePosition.y - ry
            } ?: size.y

            size = Vec2d(x, y).coerceIn(10.0, 1000.0, titleBar.size.y, 1000.0)
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