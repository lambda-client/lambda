package com.lambda.gui.api.component.sub

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.graphics.animation.AnimationTicker
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.core.list.ChildComponent
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.Mouse
import com.lambda.util.math.ColorUtils.multAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import java.awt.Color
import kotlin.math.abs

abstract class ButtonComponent(final override val owner: WindowComponent<*>) : ChildComponent() {
    abstract val position: Vec2d
    abstract val size: Vec2d

    abstract val text: String
    open val active get() = pressed

    private val actualSize get() = Vec2d(if (size.x == FILL_PARENT) owner.contentRect.size.x else size.x, size.y)
    final override val rect get() = Rect.basedOn(position, actualSize) + owner.contentRect.leftTop

    private val layer = owner.subLayer
    private val animation = AnimationTicker()

    private var activeAnimation by animation.exp(0.0, 1.0, 0.1, ::active)
    private var hoverRectAnimation by animation.exp({ 0.0 }, { 1.0 }, { if (renderHovered) 0.5 else 0.1 }, ::renderHovered)
    private var hoverFontAnimation by animation.exp(0.0, 1.0, 0.5, ::renderHovered)
    private var pressAnimation by animation.exp(0.0, 1.0, 0.5, ::pressed)
    private val interactAnimation get() = lerp(hoverRectAnimation, 1.5, pressAnimation) * 0.4

    private var lastHoveredTime = 0L
    private val renderHovered get() = hovered ||
            System.currentTimeMillis() - lastHoveredTime < 110 // a bit more than 2 ticks

    init {
        // Active color
        layer.rect.build {
            position = rect.shrink(interactAnimation)
            color(GuiSettings.mainColor.multAlpha(activeAnimation * 0.2))
        }

        // Hover glint
        layer.rect.build {
            val hoverRect = Rect.basedOn(rect.leftTop, rect.size.x * hoverRectAnimation, rect.size.y)
            position = hoverRect.shrink(interactAnimation)

            val alpha = interactAnimation * 0.3
            color(GuiSettings.mainColor.multAlpha(alpha))
        }

        // Toggle fx
        layer.rect.build {
            val left  = rect - Vec2d(rect.size.x, 0.0)
            val right = rect + Vec2d(rect.size.x, 0.0)

            position = lerp(left, right, activeAnimation)
                .clamp(rect)
                .shrink(interactAnimation)

            // 0.0 .. 1.0 .. 0.0 animation
            val alpha = 1.0 - (abs(activeAnimation - 0.5) * 2.0)
            val color = GuiSettings.mainColor.multAlpha(alpha * 0.8)

            // "Tail" effect
            val leftColor  = color.multAlpha(1.0 - active.toInt())
            val rightColor = color.multAlpha(active.toInt().toDouble())

            colorH(leftColor, rightColor)
        }

        // Text
        layer.font.build {
            text = this@ButtonComponent.text
            scale = 1.0 - pressAnimation * 0.08

            color = lerp(Color.WHITE, GuiSettings.mainColor, activeAnimation)

            val x = rect.left + ClickGui.windowPadding + interactAnimation + hoverFontAnimation * 2.0
            position = Vec2d(x, rect.center.y)
        }
    }

    abstract fun performClickAction(mouse: Mouse.Button)

    override fun onShow() {
        super.onShow()
        reset()
    }

    override fun onHide() {
        super.onHide()
        reset()
    }

    override fun onTick() {
        animation.tick()
    }

    override fun onRelease() {
        if (hovered) activeMouseButton?.let(::performClickAction)
    }

    override fun onMouseMove(mouse: Vec2d) {
        super.onMouseMove(mouse)

        val time = System.currentTimeMillis()
        if (hovered) lastHoveredTime = time
    }

    private fun reset() {
        activeAnimation = 0.0
        hoverRectAnimation = 0.0
        pressAnimation = 0.0
        lastHoveredTime = 0L
    }

    companion object {
        const val FILL_PARENT = -1.0
    }
}