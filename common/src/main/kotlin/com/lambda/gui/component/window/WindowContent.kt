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
import com.lambda.event.events.GuiEvent
import com.lambda.module.modules.client.ClickGui
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import kotlin.math.abs

class WindowContent(
    owner: Window,
    private val scrollable: Boolean
) : Layout(owner, false, true) {
    private val animation = animationTicker(false)

    private var dwheel = 0.0
    private var scrollOffset = 0.0
    private var rubberbandDelta = 0.0

    var renderScrollOffset by animation.exp({ scrollOffset + rubberbandDelta }, 0.7)
    private var scrolling = false

    private var contentHeight = {
        ClickGui.padding * 2 +
                children.sumOf(Layout::renderHeight) +
                ClickGui.listStep * (children.size - 1).coerceAtLeast(0)
    }

    private var reorder = block@ {
        children.forEachIndexed { i, child ->
            val prev by lazy { children[i - 1] }

            child.overrideY {
                if (i == 0) {
                    renderPositionY + renderScrollOffset + ClickGui.padding
                } else {
                    prev.renderPositionY + prev.renderHeight + ClickGui.listStep
                }
            }
        }
    }

    /**
     * Overrides the summary height of the content
     */
    fun overrideContentHeight(block: () -> Double) {
        contentHeight = block
    }

    /**
     * Overrides the action performed on ordering update
     */
    fun reorderChildren(block: () -> Unit) {
        reorder = block
    }

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

            if (scrollable) reorder()
        }

        onTick {
            scrollOffset = if (!owner.autoResize.enabled) {
                scrollOffset + dwheel
            } else 0.0

            scrolling = dwheel != 0.0
            dwheel = 0.0

            val prevOffset = scrollOffset
            val maxScroll = renderHeight - getContentHeight() - ClickGui.padding
            scrollOffset = scrollOffset.coerceAtLeast(maxScroll).coerceAtMost(0.0)

            rubberbandDelta += prevOffset - scrollOffset
            rubberbandDelta *= 0.5
            if (abs(rubberbandDelta) < 0.05) rubberbandDelta = 0.0

            animation.tick()
        }

        onRender {
            if (scrollable) reorder()
        }

        onMouseScroll { delta ->
            if (!scrollable) return@onMouseScroll
            dwheel += delta * 10.0
        }
    }

    fun getContentHeight() = contentHeight()

    companion object {
        /**
         * Creates an empty [WindowContent] component
         *
         * @param scrollable Whether to let user scroll this layout
         * This will also make your elements be vertically ordered
         */
        @UIBuilder
        fun Window.windowContent(scrollable: Boolean) =
            WindowContent(this, scrollable).apply(children::add)
    }
}
