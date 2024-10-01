package com.lambda.newgui.component.window

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.module.modules.client.NewCGui
import com.lambda.newgui.component.VAlign
import com.lambda.newgui.component.core.FilledRect.Companion.rect
import com.lambda.newgui.component.core.OutlineRect.Companion.outline
import com.lambda.newgui.component.layout.Layout
import com.lambda.newgui.component.core.UIBuilder
import com.lambda.newgui.component.window.TitleBar.Companion.titleBar
import com.lambda.newgui.component.window.WindowContent.Companion.windowContent
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
    initialTitle: String,
    initialPosition: Vec2d,
    initialSize: Vec2d,
    draggable: Boolean,
    scrollable: Boolean,
    private val minimizable: Boolean,
    private val resizable: Boolean,
    val autoResize: AutoResize,
    useBatching: Boolean,
) : Layout(owner, useBatching, true) {
    private val animation = animationTicker()
    private val cursorController = cursorController()

    val titleBar = titleBar(initialTitle, draggable)
    val content = windowContent(scrollable)

    protected val titleBarRect = rect {
        rectangle = titleBar.rect
        setColor(NewCGui.titleBackgroundColor)

        val radius = NewCGui.roundRadius
        leftTopRadius = radius
        rightTopRadius = radius
        leftBottomRadius = radius * (1 - minimizeAnimation)
        rightBottomRadius = radius * (1 - minimizeAnimation)

        shade = NewCGui.backgroundShade
    }

    protected val contentRect = rect {
        rectangle = Rect(titleBar.leftBottom, this@Window.rightBottom)
        setColor(NewCGui.backgroundColor)

        leftBottomRadius = NewCGui.roundRadius
        rightBottomRadius = NewCGui.roundRadius

        shade = NewCGui.backgroundShade
    }

    protected val outlineRect = outline {
        rectangle = this@Window.rect
        setColor(NewCGui.outlineColor)

        roundRadius = NewCGui.roundRadius
        glowRadius = NewCGui.outlineWidth * NewCGui.outline.toInt().toDouble()

        shade = NewCGui.outlineShade
    }

    // Position
    // ToDo find a way to animate this only when dragging
    /*private val renderX by animation.exp(position::x, 0.8)
    private val renderY by animation.exp(position::y, 0.8)
    private val renderPosition get() = Vec2d(renderX, renderY)*/

    // Minimizing
    var minimized = false
    private val minimizeAnimation by animation.exp(0.0, 1.0, 0.8) { !minimized }

    // Resizing
    private var resizeX: Double? = null
    private var resizeY: Double? = null
    private var resizeXHovered = false
    private var resizeYHovered = false

    init {
        position = initialPosition
        size = initialSize

        overrideWidth(animation.exp(::width, 0.8)::value)

        overrideHeight {
            val rawHeight = if (!autoResize.enabled) height
            else titleBar.renderHeight + content.getContentHeight()
            lerp(minimizeAnimation, titleBar.renderHeight, rawHeight)
        }

        properties.clampPosition = owner.owner == null
        content.properties.scissor = true

        with(titleBar) {
            overrideSize(
                this@Window::renderWidth,
                NewCGui::titleBarHeight
            )

            onMouseClick { button, action ->
                // Toggle minimizing state when right-clicking title bar
                if (!minimizable) return@onMouseClick
                if (button != Mouse.Button.Right || action != Mouse.Action.Click) return@onMouseClick

                minimized = !minimized
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

            if (resizeXHovered) resizeX = mousePosition.x - width
            if (resizeYHovered) resizeY = mousePosition.y - height
        }

        onMouseMove {
            resizeXHovered = false
            resizeYHovered = false

            if (!resizable || minimized) return@onMouseMove

            // Hover state update
            if (selectedChild != titleBar && isHovered) {
                resizeXHovered = mousePosition in Rect(
                    rightTop - Vec2d(RESIZE_RANGE, 0.0),
                    rightBottom
                )

                resizeYHovered = mousePosition in Rect(
                    leftBottom - Vec2d(0.0, RESIZE_RANGE),
                    rightBottom
                )
            }

            // Resize
            if (resizeX != null || resizeY != null) {
                val x = resizeX?.let { rx ->
                    mousePosition.x - rx
                } ?: width

                val y = resizeY?.let { ry ->
                    if (autoResize.enabled) return@let null
                    mousePosition.y - ry
                } ?: height

                width = x.coerceIn(80.0, 1000.0)
                height = y.coerceIn(titleBar.renderHeight + RESIZE_RANGE, 1000.0)
            }
        }
    }

    enum class AutoResize(private val isEnabled: () -> Boolean) {
        Disabled({ false }),
        ByConfig({ NewCGui.autoResize }),
        ForceEnabled({ true });

        val enabled get() = isEnabled()
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
         * @param autoResize Indicates if this window could be automatically resized based on content height
         *
         * @param block Actions to perform within content space of the window
         */
        @UIBuilder
        fun Layout.window(
            position: Vec2d = Vec2d.ZERO,
            size: Vec2d = Vec2d(115.0, 300.0),
            title: String = "Untitled",
            draggable: Boolean = true,
            scrollable: Boolean = true,
            minimizable: Boolean = true,
            resizable: Boolean = true,
            autoResize: AutoResize = AutoResize.Disabled,
            useBatching: Boolean = false,
            block: WindowContent.() -> Unit = {}
        ) = Window(
            this, title,
            position, size,
            draggable, scrollable, minimizable, resizable,
            autoResize,
            useBatching
        ).apply(children::add).apply {
            block(this.content)
        }

        private const val RESIZE_RANGE = 5.0
    }
}