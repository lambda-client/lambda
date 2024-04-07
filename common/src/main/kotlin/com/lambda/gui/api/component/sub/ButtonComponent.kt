package com.lambda.gui.api.component.sub

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.graphics.animation.AnimationTicker
import com.lambda.gui.api.component.InteractiveComponent
import com.lambda.gui.api.component.WindowComponent
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.Mouse
import com.lambda.util.math.ColorUtils.setAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import java.awt.Color

abstract class ButtonComponent(private val base: WindowComponent<*>) : InteractiveComponent() {
    abstract val position: Vec2d
    abstract val size: Vec2d

    abstract val text: String
    open val active get() = pressed

    private val actualSize get() = Vec2d(if (size.x == FILL_PARENT) base.contentRect.size.x else size.x, size.y)
    final override val rect get() = Rect.basedOn(position, actualSize) + base.contentRect.leftTop

    private val layer = base.subLayer
    private val animation = AnimationTicker()

    private val activeAnimation by animation.exp(0.0, 1.0, 0.5, ::active)
    private val hoverAnimation by animation.exp({ 0.0 }, { 1.0 }, { if (hovered) 0.5 else 0.1 }, ::hovered)
    private val pressAnimation by animation.exp(0.0, 1.0, 0.5, ::pressed)
    private val interactAnimation get() = lerp(hoverAnimation, 1.5, pressAnimation) * 0.4

    init {
        layer.rect.build {
            position = Rect.basedOn(rect.leftTop, rect.size.x * activeAnimation, rect.size.y).shrink(interactAnimation)
            color(ClickGui.mainColor)
        }

        layer.rect.build {
            position = rect.shrink(interactAnimation)
            color(Color.WHITE.setAlpha(interactAnimation * 0.2))
        }

        layer.font.build {
            text = this@ButtonComponent.text
            scale = 1.0 - pressAnimation * 0.05

            val x = rect.left + ClickGui.padding + interactAnimation + hoverAnimation * 0.5
            position = Vec2d(x, rect.center.y)
        }
    }

    abstract fun performClickAction(mouse: Mouse.Button)

    override fun onTick() {
        animation.tick()
    }

    override fun onRelease() {
        if (hovered) activeMouseButton?.let(::performClickAction)
    }

    companion object {
        const val FILL_PARENT = -1.0
    }
}