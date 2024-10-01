package com.lambda.newgui.component.window

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.module.modules.client.NewCGui
import com.lambda.newgui.component.VAlign
import com.lambda.newgui.component.core.UIBuilder
import com.lambda.newgui.component.layout.Layout
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
        overrideX { owner.titleBar.renderPositionX }
        overrideY { owner.titleBar.let { it.renderPositionY + it.renderHeight } }
        overrideWidth { owner.renderWidth }
        overrideHeight { owner.renderHeight - owner.titleBar.renderHeight }

        onShow {
            dwheel = 0.0
            scrollOffset = 0.0
            rubberbandDelta = 0.0
            renderScrollOffset = 0.0

            reorderChildren()
        }

        onTick {
            scrollOffset = if (!owner.autoResize.enabled) {
                scrollOffset + dwheel
            } else 0.0

            dwheel = 0.0

            val prevOffset = scrollOffset
            val maxScroll = renderHeight - getContentHeight() - NewCGui.padding * 2
            scrollOffset = scrollOffset.coerceAtLeast(maxScroll).coerceAtMost(0.0)

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
        if (renderHeight < 0.1) return

        var offset = renderScrollOffset + NewCGui.padding

        scrollableChildren.forEach { child ->
            child.positionY = renderPositionY + offset
            offset += child.renderHeight + NewCGui.listStep
        }
    }

    fun getContentHeight(): Double {
        val c = scrollableChildren

        val components = c.sumOf(Layout::renderHeight)
        val step = NewCGui.listStep * (c.size - 1).coerceAtLeast(0)
        val padding = NewCGui.padding * 2

        return components + step + padding
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