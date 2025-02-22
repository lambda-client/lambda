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
import com.lambda.gui.component.core.LayoutBuilder
import com.lambda.module.modules.client.ClickGui
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import kotlin.math.abs

class WindowContent(
    owner: Window,
    private val scrollable: Boolean
) : Layout(owner) {
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
     * Overrides the default content height calculation.
     *
     * Allows specifying a custom lambda that computes the total height of the content,
     * typically based on factors such as padding, spacing, and the dimensions of child elements.
     *
     * @param block a lambda that returns the new content height as a Double.
     */
    @LayoutBuilder
    fun overrideContentHeight(block: () -> Double) {
        contentHeight = block
    }

    /**
     * Sets a custom reordering action for updating the positions of the window content's children.
     *
     * This function allows you to override the default layout update behavior by providing a lambda
     * that will be executed when the children order is recalculated.
     *
     * @param block The lambda that defines the custom reordering logic.
     */
    @LayoutBuilder
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

            if (scrollable) reorder()
        }

        onMouseScroll { delta ->
            if (!scrollable) return@onMouseScroll
            dwheel += delta * 10.0
        }
    }

    /**
 * Returns the calculated content height.
 *
 * This function computes the total height of the content by invoking the designated lambda,
 * which factors in elements such as padding, spacing, and the dimensions of its child components.
 *
 * @return the total height of the content.
 */
fun getContentHeight() = contentHeight()

    companion object {
        /**
             * Creates and attaches a [WindowContent] component to this [Window].
             *
             * The new component is automatically added to the window's children. It is configured to be scrollable if
             * the [scrollable] parameter is true, which also enforces vertical ordering for its child elements.
             *
             * @param scrollable if true, enables user scrolling for the layout.
             */
        @UIBuilder
        fun Window.windowContent(scrollable: Boolean) =
            WindowContent(this, scrollable).apply(children::add)
    }
}
