package com.lambda.newgui.component.window

import com.lambda.module.modules.client.NewCGui
import com.lambda.newgui.component.VAlign
import com.lambda.newgui.component.layout.Layout
import com.lambda.newgui.component.core.UIBuilder
import com.lambda.newgui.component.window.TitleBar.Companion.titleBar
import com.lambda.newgui.component.window.WindowContent.Companion.windowContent
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import java.awt.Color

/**
 * Represents a window component
 *
 * Consists of titlebar and content layout
 */
open class Window(
    owner: Layout,
    initialTitle: String,
    draggable: Boolean,
    scrollable: Boolean
) : Layout(owner, false, true) {
    val titleBar = titleBar(initialTitle, draggable)
    val content = windowContent(scrollable)

    init {
        // Clamp the window only within the screen bounds
        properties.clampPosition = owner.owner == null

        onShow {
            content.properties.scissorChildren = true

            content.rectUpdate {
                Rect(
                    titleBar.rect.leftBottom + NewCGui.padding,
                    this@Window.rect.rightBottom - NewCGui.padding
                )
            }
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
         * @param block Actions to perform within content space of the window
         */
        @UIBuilder
        fun Layout.window(
            position: Vec2d = Vec2d.ZERO,
            size: Vec2d = Vec2d(100.0, 300.0),
            title: String = "Untitled",
            draggable: Boolean = true,
            scrollable: Boolean = true,
            block: WindowContent.() -> Unit = {}
        ) = Window(this, title, draggable, scrollable).apply(children::add).apply {
            this.position = position
            this.size = size
            block(this.content)
        }
    }
}