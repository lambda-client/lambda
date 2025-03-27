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
import com.lambda.gui.component.core.FilledRect
import com.lambda.gui.component.core.FilledRect.Companion.rect
import com.lambda.gui.component.core.FilledRect.Companion.rectBehind
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.component.window.Window
import com.lambda.gui.impl.clickgui.module.ModuleLayout
import com.lambda.gui.impl.clickgui.module.setting.SettingLayout
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.Vec2d
import com.lambda.util.math.lerp
import com.lambda.util.math.multAlpha
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

        if ((this as? SettingLayout<*>)?.isVisible == true) speed *= 0.8

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

        onWindowExpand {
            if (ClickGui.multipleSettingWindows) return@onWindowExpand

            val close = if (this !is ModuleLayout) {
                owner.children.filterIsInstance<AnimatedChild>()
            } else {
                val base = owner // window content
                    .owner // window
                    ?.owner  // environment with windows

                base?.children?.filterIsInstance<Window>()?.flatMap { window ->
                    window.content.children.filterIsInstance<AnimatedChild>()
                } ?: mutableListOf()
            }

            close.forEach {
                if (it != this) it.isMinimized = true
            }
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

    companion object {
        fun AnimatedChild.animatedBackground(
            enableProgress: () -> Double = { hoverAnimation }
        ) = rectBehind(titleBar) {
            // base rect with lowest y to avoid children overlying
            onUpdate {
                val enableAnimation = enableProgress()

                rect = this@animatedBackground.rect.shrink(shrink)
                shade = ClickGui.backgroundShade

                val openRev = 1.0 - openAnimation     // 1.0  <->  0.0
                val openRevSigned = openRev * 2 - 1   // 1.0  <-> -1.0
                val enableRev = 1.0 - enableAnimation // 1.0  <->  0.0

                var progress = enableAnimation

                // hover: +0.1 to alpha if minimized, -0.1 to alpha if maximized and enabled
                progress += hoverAnimation * ClickGui.moduleHoverAccent *
                        lerp(enableAnimation, 1.0, openRevSigned)

                // +0.4 to alpha if opened and disabled
                progress += openAnimation * ClickGui.moduleOpenAccent * enableRev

                // interpolate and set the color
                setColor(
                    lerp(progress,
                        ClickGui.moduleDisabledColor,
                        ClickGui.moduleEnabledColor
                    ).multAlpha(showAnimation)
                )

                setRadius(hoverAnimation)

                if (isLast && ClickGui.autoResize) {
                    leftBottomRadius = ClickGui.roundRadius - (ClickGui.padding + shrink)
                    rightBottomRadius = leftBottomRadius
                }
            }

            rect { // hover fx
                onUpdate {
                    val base = this@rect.owner as FilledRect

                    position = base.position
                    size = base.size
                    shade = base.shade

                    setRadius(
                        base.leftTopRadius,
                        base.rightTopRadius,
                        base.rightBottomRadius,
                        base.leftBottomRadius
                    )

                    val hoverColor = Color.WHITE.setAlpha(
                        ClickGui.moduleHoverAccent * hoverAnimation * (1.0 - openAnimation) * showAnimation
                    )

                    setColorH(hoverColor.setAlpha(0.0), hoverColor)
                }
            }
        }
    }
}