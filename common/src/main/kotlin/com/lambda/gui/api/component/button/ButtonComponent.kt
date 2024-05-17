package com.lambda.gui.api.component.button

import com.lambda.core.LambdaSound
import com.lambda.core.SoundManager.playSoundRandomly
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.list.ChildComponent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.Mouse
import com.lambda.util.math.ColorUtils.multAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import java.awt.Color

abstract class ButtonComponent(
    owner: ChildLayer.Drawable<*, *>
) : ChildComponent(owner) {
    abstract val position: Vec2d
    abstract val size: Vec2d

    abstract val text: String
    protected open val textColor get() = lerp(Color.WHITE, GuiSettings.mainColor, activeAnimation).multAlpha(showAnimation)
    protected abstract var activeAnimation: Double

    private val actualSize get() = Vec2d(if (size.x == FILL_PARENT) owner.rect.size.x else size.x, size.y)
    final override val rect get() = Rect.basedOn(position, actualSize) + owner.rect.leftTop

    val renderer = owner.renderer.entry()
    protected val animation = owner.gui.animation

    private var hoverRectAnimation by animation.exp({ 0.0 }, { 1.0 }, { if (renderHovered) 0.6 else 0.07 }, ::renderHovered)
    protected var hoverFontAnimation by animation.exp(0.0, 1.0, 0.5, ::renderHovered)
    protected var pressAnimation by animation.exp(0.0, 1.0, 0.5) { activeButton != null }
    protected val interactAnimation get() = lerp(hoverRectAnimation, 1.5, pressAnimation) * 0.4
    override val childShowAnimation: Double get() = owner.childShowAnimation
    protected open val showAnimation get() = owner.childShowAnimation

    private var lastHoveredTime = 0L
    private val renderHovered get() = hovered || System.currentTimeMillis() - lastHoveredTime < 110

    // Removes button shrinking if there's no space between buttons
    protected val shrinkAnimation get() = lerp(0.0, interactAnimation, ClickGui.buttonStep)

    init {
        // Active color
        renderer.filled {
            position = rect.shrink(shrinkAnimation)
            shade = GuiSettings.shade
            color(GuiSettings.mainColor.multAlpha(activeAnimation * 0.3 * showAnimation))
        }

        // Hover glint
        renderer.filled {
            val hoverRect = Rect.basedOn(rect.leftTop, rect.size.x * hoverRectAnimation, rect.size.y)
            position = hoverRect.shrink(shrinkAnimation)
            shade = GuiSettings.shade

            val alpha = interactAnimation * 0.2 * showAnimation
            color(GuiSettings.mainColor.multAlpha(alpha))
        }

        // Text
        renderer.font {
            text = this@ButtonComponent.text
            scale = 1.0 - pressAnimation * 0.08

            color = textColor

            val x = ClickGui.windowPadding + interactAnimation + hoverFontAnimation
            position = Vec2d(rect.left + x, rect.center.y)
        }
    }

    open fun performClickAction(e: GuiEvent.MouseClick) {}

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

    override fun onPress(e: GuiEvent.MouseClick) {
        val pitch = if (e.button == Mouse.Button.Left) 1.0 else 0.9
        playSoundRandomly(LambdaSound.BUTTON_CLICK.event, pitch)
    }

    override fun onRelease(e: GuiEvent.MouseClick) {
        if (hovered) performClickAction(e)
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