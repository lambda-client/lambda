package com.lambda.gui.api.component.button

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.core.list.ChildComponent
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.math.ColorUtils.multAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import java.awt.Color
import kotlin.math.abs

abstract class ButtonComponent(
    final override val owner: WindowComponent<*>
) : ChildComponent() {
    abstract val position: Vec2d
    abstract val size: Vec2d

    abstract val text: String
    abstract val active: Boolean

    private val actualSize get() = Vec2d(if (size.x == FILL_PARENT) owner.contentRect.size.x else size.x, size.y)
    final override val rect get() = Rect.basedOn(position, actualSize) + owner.contentRect.leftTop

    private val layer = owner.subLayer
    private val renderer = layer.entry()
    protected val animation = owner.animation

    private var activeAnimation by animation.exp(0.0, 1.0, 0.15, ::active)
    private var toggleFxDirection by animation.exp(0.0, 1.0, 0.6, ::active)
    private var hoverRectAnimation by animation.exp({ 0.0 }, { 1.0 }, { if (renderHovered) 0.6 else 0.07 }, ::renderHovered)
    private var hoverFontAnimation by animation.exp(0.0, 1.0, 0.5, ::renderHovered)
    private var pressAnimation by animation.exp(0.0, 1.0, 0.5, ::pressed)
    private val interactAnimation get() = lerp(hoverRectAnimation, 1.5, pressAnimation) * 0.4
    private val showAnimationRaw by animation.exp(0.0, 1.0, 0.7, owner::isOpen)
    private val showAnimation get() = lerp(0.0, showAnimationRaw, owner.showAnimation)

    private var lastHoveredTime = 0L
    private val renderHovered get() = hovered ||
            System.currentTimeMillis() - lastHoveredTime < 110 // a bit more than 2 ticks

    init {
        // Active color
        renderer.rect {
            position = rect.shrink(interactAnimation)
            color(GuiSettings.mainColor.multAlpha(activeAnimation * 0.3 * showAnimation))
        }

        // Hover glint
        renderer.rect {
            val hoverRect = Rect.basedOn(rect.leftTop, rect.size.x * hoverRectAnimation, rect.size.y)
            position = hoverRect.shrink(interactAnimation)

            val alpha = interactAnimation * 0.2
            color(GuiSettings.mainColor.multAlpha(alpha))
        }

        // Toggle fx
        renderer.rect {
            val left  = rect - Vec2d(rect.size.x, 0.0)
            val right = rect + Vec2d(rect.size.x, 0.0)

            position = lerp(left, right, activeAnimation)
                .clamp(rect)
                .shrink(interactAnimation)

            // 0.0 .. 1.0 .. 0.0 animation
            val alpha = 1.0 - (abs(activeAnimation - 0.5) * 2.0)
            val color = GuiSettings.mainColor.multAlpha(alpha * 0.6 * showAnimation)

            // "Tail" effect
            val leftColor  = color.multAlpha(1.0 - toggleFxDirection)
            val rightColor = color.multAlpha(toggleFxDirection)

            colorH(leftColor, rightColor)
        }

        // Text
        renderer.font {
            text = this@ButtonComponent.text
            scale = 1.0 - pressAnimation * 0.08

            color = lerp(Color.WHITE, GuiSettings.mainColor, activeAnimation).multAlpha(showAnimation)

            val x = rect.left + ClickGui.windowPadding + interactAnimation + hoverFontAnimation
            position = Vec2d(x, rect.center.y)
        }
    }

    abstract fun performClickAction(e: GuiEvent.MouseClick)

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        when (e) {
            is GuiEvent.Show, is GuiEvent.Hide -> reset()

            is GuiEvent.MouseMove -> {
                val time = System.currentTimeMillis()
                if (hovered) lastHoveredTime = time
            }
        }
    }

    override fun onRelease(e: GuiEvent.MouseClick) {
        performClickAction(e)
    }

    override fun onRemove() {
        renderer.destroy()
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