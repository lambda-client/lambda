package com.lambda.gui.api.component.button

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.list.ChildComponent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.api.layer.LayerEntry
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.KeyCode
import com.lambda.util.math.ColorUtils.setAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import java.awt.Color
import kotlin.math.abs

abstract class InputBarOverlay (renderer: LayerEntry, owner: ChildLayer.Drawable<InputBarOverlay, *>) : ChildComponent(owner) {
    override val rect: Rect get() = owner.rect
    override var isActive = false

    abstract val pressAnimation: Double
    abstract val interactAnimation: Double
    abstract val hoverFontAnimation: Double
    abstract val showAnimation: Double

    val activeAnimation by owner.gui.animation.exp(0.0, 1.0, 0.7, ::isActive)
    private var typeAnimation by owner.gui.animation.exp({ 0.0 }, 0.2)

    private var targetOffset = 0.0
    private var offset by owner.gui.animation.exp(::targetOffset, 0.4)

    abstract fun getInitText(): String
    abstract fun setValue(string: String)

    open fun isCharAllowed(char: Char): Boolean = true

    private val field = renderer.font {
        scale = lerp(0.5, 1.0, activeAnimation) - pressAnimation * 0.08
        color = Color.WHITE.setAlpha(lerp(0.0, activeAnimation, showAnimation))

        val x = ClickGui.windowPadding + interactAnimation + hoverFontAnimation
        position = Vec2d(rect.left + x, rect.center.y)
        targetOffset = stringWidth
    }

    init {
        renderer.filled {
            val shrink = lerp(rect.size.y * 0.5, 2 + abs(typeAnimation), activeAnimation)
            val x = field.position.x + offset + 2

            position = Rect(
                Vec2d(0.0, rect.top + shrink),
                Vec2d(1.0, rect.bottom - shrink)
            ) + Vec2d(lerp(rect.right, x, activeAnimation), 0.0)

            color(field.color.setAlpha(0.8))
        }
    }

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        when (e) {
            is GuiEvent.Show -> {
                isActive = false
            }

            is GuiEvent.CharTyped -> {
                if (!isActive || !isCharAllowed(e.char)) return
                field.text += e.char
                typeAnimation = 1.0
            }

            is GuiEvent.KeyPress -> {
                if (!isActive) return

                when (e.key) {
                    KeyCode.Enter -> {
                        setValue(field.text)
                        toggle()
                    }

                    KeyCode.Backspace -> {
                        field.text = field.text.dropLast(1)
                        typeAnimation = -1.0
                    }
                }
            }
        }
    }

    fun toggle() {
        isActive = !isActive

        if (isActive) {
            field.text = getInitText()
            targetOffset = field.stringWidth
        }
    }
}