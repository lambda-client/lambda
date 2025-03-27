/*
 * Copyright 2025 Lambda
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

package com.lambda.gui.impl.clickgui.core

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.component.core.FilledRect.Companion.rect
import com.lambda.gui.component.core.LayoutBuilder
import com.lambda.gui.component.core.OutlineRect.Companion.outline
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.core.insertLayout
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.impl.clickgui.module.setting.SettingSlider
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.Mouse
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.Vec2d
import com.lambda.util.math.multAlpha
import com.lambda.util.math.setAlpha
import com.lambda.util.math.transform
import java.awt.Color

class SliderLayout(
    owner: Layout,
    private val isVertical: Boolean
) : AnimatedChild(owner, "") {
    // Not a great solution
    private val setting = owner as? SettingSlider<*>
    private val showAnim get() = setting?.showAnimation ?: showAnimation
    private val pressedBut get() = setting?.pressedButton ?: pressedButton

    // Actions
    private var getProgressBlock = { 0.0 }
    private var setProgressBlock = { _: Double -> }

    @LayoutBuilder
    fun progress(action: SliderLayout.() -> Double) {
        getProgressBlock = { action(this) }
    }

    @LayoutBuilder
    fun onSlide(action: SliderLayout.(Double) -> Unit) {
        setProgressBlock = { action(this, it) }
    }

    private val renderProgress get() = renderProgress0 * showAnim
    private val renderProgress0 by animation.exp(0.7) {
        if (pressedBut == Mouse.Button.Left) dragProgress else getProgressBlock()
    }

    private val bg = rect { // background
        onUpdate {
            rect = this@SliderLayout.rect
                .moveFirst(Vec2d.RIGHT * ClickGui.fontOffset)
                .moveSecond(Vec2d.LEFT * ClickGui.fontOffset)

            shade = ClickGui.backgroundShade
            setColor(Color.BLACK.setAlpha(0.25 * showAnim))
            setRadius(100.0)
        }
    }

    private val dragProgress: Double get() = transform(
        if (isVertical) mousePosition.y - bg.positionY else mousePosition.x - bg.positionX,
        0.0, if (isVertical) bg.height else bg.width,
        isVertical.toInt().toDouble(), 1.0 - isVertical.toInt().toDouble()
    ).coerceIn(0.0, 1.0)

    init {
        (setting ?: this).onMouseMove {
            if (pressedBut != Mouse.Button.Left) return@onMouseMove
            setProgressBlock(dragProgress)
        }

        (setting ?: this).onMouse(Mouse.Button.Left, Mouse.Action.Click) {
            if (pressedBut != Mouse.Button.Left) return@onMouse
            setProgressBlock(dragProgress)
        }

        rect {
            onUpdate { // progress
                rect = bg.rect

                if (isVertical) {
                    height *= renderProgress
                    positionY = bg.positionY + bg.height - height
                } else {
                    width *= renderProgress
                }

                shade = ClickGui.backgroundShade
                setColor(Color.WHITE.setAlpha(0.25 * showAnim))
                setRadius(100.0)
            }
        }

        outline {
            onUpdate {
                rect = bg.rect
                val c = Color.BLACK.setAlpha(0.3 * showAnim)
                val a = transform(renderProgress, 0.5, 1.0, 0.0, 1.0).coerceIn(0.0, 1.0)
                setColorH(c, c.multAlpha(a))
                roundRadius = 100.0
            }
        }
    }

    companion object {
        /**
         * Creates a [SliderLayout].
         */
        @UIBuilder
        fun Layout.slider(
            isVertical: Boolean = false,
            block: SliderLayout.() -> Unit = {}
        ) = SliderLayout(this, isVertical).apply(children::add).apply(block)

        /**
         * Adds a [SliderLayout] behind given [layout]
         */
        @UIBuilder
        fun Layout.sliderBehind(
            layout: Layout,
            isVertical: Boolean = false,
            block: SliderLayout.() -> Unit = {}
        ) = SliderLayout(this, isVertical).insertLayout(this, layout, false).apply(block)

        /**
         * Adds a [SliderLayout] over given [layout]
         */
        @UIBuilder
        fun Layout.sliderOver(
            layout: Layout,
            isVertical: Boolean = false,
            block: SliderLayout.() -> Unit = {}
        ) = SliderLayout(this, isVertical).insertLayout(this, layout, true).apply(block)
    }
}