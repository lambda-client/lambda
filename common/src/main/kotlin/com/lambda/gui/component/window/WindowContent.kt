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
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.Rect
import kotlin.math.abs

class WindowContent(
    owner: Window,
    scrollable: Boolean
) : Layout(owner) {
    private val window = owner
    private val animation = animationTicker(false)

    private var dwheel = 0.0
    private var scrollOffset = 0.0
    private var rubberbandDelta = 0.0

    private var renderScrollOffset by animation.exp(0.7) { scrollOffset + rubberbandDelta }

    override val scissorRect: Rect
        get() = Rect(window.titleBar.leftBottom, window.rightBottom)

    override val renderSelf: Boolean
        get() = window.heightAnimation > 0.05

    /**
     * Orders the children set vertically
     */
    @LayoutBuilder
    fun listify() {
        children.forEachIndexed { i, it ->
            val prev = children.getOrNull(i - 1) ?: run {
                it.overrideY {
                    this.renderPositionY + ClickGui.padding
                }

                return@forEachIndexed
            }

            it.overrideY {
                prev.renderPositionY + layoutHeight(prev, true) + ClickGui.listStep
            }
        }
    }

    init {
        properties.scissor = true

        overrideX(owner.titleBar::renderPositionX)
        overrideY {
            owner.titleBar.let { it.renderPositionY + it.renderHeight } + renderScrollOffset * scrollable.toInt()
        }

        overrideWidth(owner::renderWidth)
        overrideHeight {
            children.sumOf { layoutHeight(it, false) } +
                    ClickGui.listStep * (children.size - 1).coerceAtLeast(0) +
                    ClickGui.padding * 2
        }

        onShow {
            dwheel = 0.0
            scrollOffset = 0.0
            rubberbandDelta = 0.0
            renderScrollOffset = 0.0
        }

        onTick {
            scrollOffset = if (!owner.autoResize.enabled) {
                scrollOffset + dwheel
            } else 0.0

            dwheel = 0.0

            val prevOffset = scrollOffset
            scrollOffset = scrollOffset.coerceAtLeast(
                owner.targetHeight - renderHeight
            ).coerceAtMost(0.0)

            rubberbandDelta += prevOffset - scrollOffset
            rubberbandDelta *= 0.5
            if (abs(rubberbandDelta) < 0.05) rubberbandDelta = 0.0

            animation.tick()
        }

        onMouseScroll { delta ->
            dwheel += delta * 10.0
        }
    }

    private fun layoutHeight(layout: Layout, animate: Boolean): Double {
        var height = layout.renderHeight
        val animated = layout as? AnimatedWindowChild ?: return height

        height *= if (!animate) animated.staticShowAnimation
        else animated.showAnimation

        return height
    }

    companion object {
        /**
         * Creates an empty [WindowContent] component
         */
        @UIBuilder
        fun Window.windowContent(scrollable: Boolean) =
            WindowContent(this, scrollable).apply(children::add)
    }
}
