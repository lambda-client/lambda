package com.lambda.newgui.component.window

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.module.modules.client.NewCGui
import com.lambda.newgui.component.VAlign
import com.lambda.newgui.component.core.UIBuilder
import com.lambda.newgui.component.layout.Layout
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import com.lambda.util.math.setAlpha
import java.awt.Color
import kotlin.math.abs

class WindowContent(
    owner: Window,
    scrollable: Boolean
) : Layout(owner, false, true) {
    private val animation = animationTicker(false)

    private var dwheel = 0.0
    private var scrollOffset = 0.0
    private var rubberbandDelta = 0.0
    private var renderScrollOffset by animation.exp({ scrollOffset + rubberbandDelta }, 0.7)

    private val scrollableChildren get() = children.filter { it.verticalAlignment == VAlign.TOP }

    init {
        onShow {
            dwheel = 0.0
            scrollOffset = 0.0
            rubberbandDelta = 0.0
            renderScrollOffset = 0.0

            reorderChildren()
        }

        onTick {
            scrollOffset += dwheel
            dwheel = 0.0

            val c = scrollableChildren
            var childHeight = c.sumOf { it.rect.size.y + NewCGui.listStep }
            if (c.isNotEmpty()) childHeight -= NewCGui.listStep

            val range = rect.size.y - childHeight

            val prevOffset = scrollOffset
            scrollOffset = scrollOffset.coerceAtLeast(range).coerceAtMost(0.0)

            rubberbandDelta += prevOffset - scrollOffset
            rubberbandDelta *= 0.5
            if (abs(rubberbandDelta) < 0.05) rubberbandDelta = 0.0

            animation.tick()
        }

        onRender {
            reorderChildren()
        }

        onMouseScroll { delta ->
            if (!scrollable) return@onMouseScroll
            dwheel += delta * 10.0
        }
    }

    private fun reorderChildren() {
        // Skip for closed windows
        if (size.y < 0.1) return

        var offset = renderScrollOffset + NewCGui.padding

        scrollableChildren.forEach { child ->
            child.position = Vec2d(child.position.x, position.y + offset)
            offset += child.rect.size.y + NewCGui.listStep
        }
    }

    companion object {
        /**
         * Creates an empty [WindowContent] component
         *
         * @param scrollable Whether to let user scroll this layout
         */
        @UIBuilder
        fun Window.windowContent(scrollable: Boolean) =
            WindowContent(this, scrollable).apply(children::add)
    }
}