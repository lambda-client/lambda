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
import com.lambda.gui.component.HAlign
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.component.window.Window
import com.lambda.gui.impl.clickgui.module.SettingLayout
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.Vec2d
import com.lambda.util.math.lerp
import com.lambda.util.math.setAlpha
import com.lambda.util.math.transform
import java.awt.Color
import kotlin.math.pow

abstract class AnimatedChild(
    owner: Layout,
    initialTitle: String = "Untitled",
    initialPosition: Vec2d = Vec2d.ZERO,
    initialSize: Vec2d = Vec2d(110, 350),
    draggable: Boolean = false,
    scrollable: Boolean = false,
    minimizing: Minimizing = Minimizing.Disabled,
    resizable: Boolean = false,
    autoResize: AutoResize = AutoResize.Disabled
) : Window(owner, initialTitle, initialPosition, initialSize, draggable, scrollable, minimizing, resizable, autoResize) {
    private val window get() = owner?.owner as? Window
    private val animatedWindow get() = owner?.owner as? AnimatedChild

    // Hovering
    protected val hovered get() = isHovered || isExpand || System.currentTimeMillis() - lastHover < 80
    var hoverAnimation by animation.exp(0.0, 1.0, { if (hovered) 0.8 else 0.3 }, ::hovered)
    private var lastHover = 0L // ToDo: replace with timer
    open val shrink get() = 0.5 * lerp(openAnimation, 1.0, 0.5) * (hoverAnimation.pow(3) + pressAnimation)

    protected var pressAnimation by animation.exp(0.0, 1.0, 0.6) { isPressed && content.selectedChild == null }
    protected var openAnimation by animation.exp(1.0, 0.0, 0.6, ::isMinimized)

    // Show animation for when the component is shown or hidden
    open val isShown get() = true
    private val isShownInternal get() = window?.isExpand != false && isShown
    var showAnimation by animation.exp(0.0, 1.0, {
        var speed = 0.7

        if (lastIndex != 0) {
            var start = speed
            var end = speed
            when (ClickGui.animationCurve) {
                ClickGui.AnimationCurve.Normal -> start = ClickGui.smoothness
                ClickGui.AnimationCurve.Static -> {}
                ClickGui.AnimationCurve.Reverse -> end = ClickGui.smoothness
            }
            speed = transform(index.toDouble(), 0.0, lastIndex.toDouble(), start, end)
        }

        if ((this as? SettingLayout<*, *>)?.isVisible == true) speed *= 0.8

        speed + isShownInternal.toInt() * 0.1
    }) { isShownInternal }.apply {
        if (window == null) this.setValue(1.0)
    }; protected set

    // Animation without index-based slowdown
    var staticShowAnimation by animation.exp(0.0, 1.0, 0.7, ::isShown)

    // Index for smooth "ordered" animation
    var index = 0
    var lastIndex = 0

    protected val isLast
        get() = index == lastIndex

    override val isHovered: Boolean
        get() = super.isHovered && isShown

    override val renderSelf: Boolean
        get() = showAnimation > 0.0 && super.renderSelf

    init {
        isMinimized = true
        openAnimation = 0.0

        onShow {
            showAnimation = 0.0
            staticShowAnimation = 0.0
        }

        onUpdate {
            if (isHovered) lastHover = System.currentTimeMillis()
        }

        titleBar.textField.use {
            textHAlignment = HAlign.LEFT

            onUpdate {
                offsetX = lerp(
                    showAnimation,
                    -5.0,
                    ClickGui.fontOffset + hoverAnimation * 2 - pressAnimation
                )

                scale = 1.0 *
                        lerp(showAnimation, 0.7, 1.0) *
                        lerp(pressAnimation, 1.0, 0.95)

                color = Color.WHITE.setAlpha(showAnimation)
            }
        }

        listOf(
            titleBarBackground,
            contentBackground,
            outlineRect
        ).forEach(Layout::destroy)
    }
}