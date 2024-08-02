package com.lambda.gui.api.component.button

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.RenderLayer
import com.lambda.gui.api.component.core.list.ChildComponent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.KeyCode
import com.lambda.util.math.ColorUtils.multAlpha
import com.lambda.util.math.ColorUtils.setAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import java.awt.Color
import kotlin.math.abs

abstract class InputBarOverlay (val renderer: RenderLayer, owner: ChildLayer.Drawable<InputBarOverlay, *>) : ChildComponent(owner) {
    override val rect: Rect get() = owner.rect
    override var isActive = false

    protected abstract val pressAnimation: Double
    protected abstract val interactAnimation: Double
    protected abstract val hoverFontAnimation: Double
    protected abstract val showAnimation: Double
    protected open val isKeyBind: Boolean = false

    val activeAnimation by owner.gui.animation.exp(0.0, 1.0, 0.7, ::isActive)
    private var typeAnimation by owner.gui.animation.exp({ 0.0 }, 0.2)

    private var targetOffset = 0.0
    private var offset by owner.gui.animation.exp(::targetOffset, 0.4)

    abstract fun getText(): String
    open fun setStringValue(string: String) {}
    open fun setKeyValue(key: KeyCode) {}

    open fun isCharAllowed(string: String, char: Char): Boolean = true

    private var typed = ""

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        when (e) {
            is GuiEvent.Show -> {
                isActive = false
            }

            is GuiEvent.Render -> {
                // Value text
                renderer.font.apply {
                    val text = getText()
                    val scale = lerp(0.5, 1.0, 1.0 - activeAnimation)
                    val position = Vec2d(rect.right, rect.center.y) - Vec2d(ClickGui.windowPadding + getWidth(text, scale), 0.0)
                    val color = Color.WHITE.setAlpha(lerp(0.0, 1.0 - activeAnimation, showAnimation))

                    build(text, position, color, scale)
                }

                val textStartX = rect.left + ClickGui.windowPadding + interactAnimation + hoverFontAnimation
                val textColor = Color.WHITE.setAlpha(lerp(0.0, activeAnimation, showAnimation))

                // Typing field
                renderer.font.apply {
                    val scale = lerp(0.5, 1.0, activeAnimation) - pressAnimation * 0.08
                    val position = Vec2d(textStartX, rect.center.y)

                    targetOffset = getWidth(typed, scale)
                    build(typed, position, textColor, scale)
                }

                // Separator
                renderer.filled.apply {
                    val shrink = lerp(rect.size.y * 0.5, 2 + abs(typeAnimation), activeAnimation)

                    val rect = Rect(
                        Vec2d(0.0, rect.top + shrink),
                        Vec2d(1.0, rect.bottom - shrink)
                    ) + Vec2d(lerp(rect.right, textStartX + offset + 2, activeAnimation), 0.0)

                    build(rect, color = textColor.multAlpha(0.8))
                }
            }

            is GuiEvent.CharTyped -> {
                if (!isActive || !isCharAllowed(typed, e.char) || isKeyBind) return
                typed += e.char
                typeAnimation = 1.0
            }

            is GuiEvent.KeyPress -> {
                if (!isActive) return

                if (isKeyBind) {
                    val key = when (e.key) {
                        KeyCode.DELETE, KeyCode.BACKSPACE -> KeyCode.UNBOUND
                        KeyCode.ESCAPE -> return
                        else -> e.key
                    }

                    setKeyValue(key)
                    toggle()
                    return
                }

                when (e.key) {
                    KeyCode.ENTER -> {
                        setStringValue(typed)
                        toggle()
                    }

                    KeyCode.BACKSPACE -> {
                        typed = typed.dropLast(1)
                        typeAnimation = -1.0
                    }

                    else -> {}
                }
            }
        }
    }

    fun toggle() {
        isActive = !isActive
        if (isActive) typed = getText().filter { isCharAllowed("", it) }
    }
}