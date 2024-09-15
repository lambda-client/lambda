package com.lambda.newgui.component.window

import com.lambda.newgui.Layout
import com.lambda.newgui.UIBuilder
import com.lambda.newgui.component.core.TextField.Companion.textField
import com.lambda.newgui.component.window.Window.ContentSpace.Companion.contentSpace
import com.lambda.newgui.component.window.Window.TitleBar.Companion.titleBar
import com.lambda.util.Mouse
import com.lambda.util.math.ColorUtils.setAlpha
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import java.awt.Color

class Window(
    owner: Layout,
    initialPosition: Vec2d,
    initialSize: Vec2d,
    initialTitle: String
) : Layout(owner, false, true) {
    var position = initialPosition
    var size = initialSize

    val titleBar = titleBar(initialTitle)
    val content = contentSpace {
        Rect(titleBar.rect.leftBottom, rect.rightBottom) - position
    }

    init {
        rect {
            Rect.basedOn(position, size)
        }

        onRender {
            filled.build(rect, 2.0, Color.BLACK.setAlpha(0.2))
        }
    }

    class TitleBar(
        owner: Window,
        initialTitle: String
    ) : Layout(owner, true, true) {
        val textField = textField(initialTitle)

        private var dragOffset: Vec2d? = null

        init {
            rect {
                Rect(Vec2d.ZERO, Vec2d(owner.rect.size.x, renderer.font.getHeight() * 1.25))
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

    open class ContentSpace(owner: Layout, rectBlock: () -> Rect) : Layout(owner, false, true) {
        init {
            rect(rectBlock)
        }

        companion object {
            @UIBuilder
            fun Layout.contentSpace(
                rect: () -> Rect,
            ) = ContentSpace(this, rect).apply(children::add)
        }
    }

    companion object {
        @UIBuilder
        fun Layout.window(
            position: Vec2d,
            size: Vec2d = Vec2d(100.0, 300.0),
            title: String = "Untitled",
            block: Window.() -> Unit
        ) = Window(this, position, size, title).apply(children::add).apply(block)
    }
}