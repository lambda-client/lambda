package com.lambda.newgui.component.window

import com.lambda.module.modules.client.NewCGui
import com.lambda.newgui.component.layout.Layout
import com.lambda.newgui.component.core.UIBuilder
import com.lambda.newgui.component.HAlign
import com.lambda.newgui.component.core.TextField.Companion.textField
import com.lambda.newgui.component.layout.ListLayout.Companion.listLayout
import com.lambda.newgui.component.window.Window.TitleBar.Companion.titleBar
import com.lambda.util.Mouse
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import java.awt.Color

/**
 * Represents a window component
 *
 * Contains titlebar and content layout
 */
class Window(
    owner: Layout,
    initialTitle: String
) : Layout(owner, false, true) {
    val titleBar = titleBar(initialTitle)
    val content = listLayout {
        rectUpdate {
            Rect(
                titleBar.rect.leftBottom + NewCGui.padding,
                this@Window.rect.rightBottom - NewCGui.padding
            )
        }
    }

    override val clampPosition = true

    init {
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

    /**
     * Represents a titlebar component
     */
    class TitleBar(
        owner: Window,
        title: String
    ) : Layout(owner, true, true) {
        val textField = textField(title) {
            horizontalAlignment = HAlign.CENTER
        }

        private var dragOffset: Vec2d? = null

        init {
            rectUpdate {
                Rect(owner.rect.leftTop, owner.rect.rightTop + Vec2d(0.0, renderer.font.getHeight() * 1.5))
            }

            onShow {
                dragOffset = null
            }

            onMouseClick { button: Mouse.Button, action: Mouse.Action ->
                dragOffset = if (button == Mouse.Button.Left && action == Mouse.Action.Click) {
                    mousePosition - owner.position
                } else null
            }

            onMouseMove { mouse ->
                dragOffset?.let { drag ->
                    owner.position = mouse - drag
                }
            }
        }

        companion object {
            @UIBuilder
            fun Window.titleBar(
                text: String,
            ) = TitleBar(this, text).apply(children::add)
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
         * @param block Actions to perform within this component
         */
        @UIBuilder
        fun Layout.window(
            position: Vec2d = Vec2d.ZERO,
            size: Vec2d = Vec2d(100.0, 300.0),
            title: String = "Untitled",
            block: Window.() -> Unit = {}
        ) = Window(this, title).apply(children::add).apply {
            this.position = position
            this.size = size
            block(this)
        }
    }
}