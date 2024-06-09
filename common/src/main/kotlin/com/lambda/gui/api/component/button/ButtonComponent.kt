package com.lambda.gui.api.component.button

import com.lambda.sound.LambdaSound
import com.lambda.sound.SoundManager.playSoundRandomly
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
    protected open val centerText = false

    protected abstract var activeAnimation: Double

    private val actualSize get() = Vec2d(if (size.x == FILL_PARENT) owner.rect.size.x else size.x, size.y)
    final override val rect get() = Rect.basedOn(position, actualSize) + owner.rect.leftTop

    protected val renderer = owner.renderer
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

    open fun performClickAction(e: GuiEvent.MouseClick) {}

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        when (e) {
            is GuiEvent.Show, is GuiEvent.Hide -> reset()

            is GuiEvent.Render -> {
                // Active color
                renderer.filled.build(
                    rect = rect.shrink(shrinkAnimation),
                    color = GuiSettings.mainColor.multAlpha(activeAnimation * 0.3 * showAnimation),
                    shade = GuiSettings.shade
                )

                // Hover glint
                val hoverRect = Rect.basedOn(rect.leftTop, rect.size.x * hoverRectAnimation, rect.size.y)
                renderer.filled.build(
                    rect = hoverRect.shrink(shrinkAnimation),
                    color = GuiSettings.mainColor.multAlpha(interactAnimation * 0.2 * showAnimation),
                    shade = GuiSettings.shade
                )

                // Text
                val textScale = 1.0 - pressAnimation * 0.08
                val textX = ClickGui.windowPadding + interactAnimation + hoverFontAnimation
                val textXCentered = rect.size.x * 0.5 - renderer.font.getWidth(text, textScale) * 0.5
                renderer.font.build(
                    text = text,
                    position = Vec2d(rect.left + if (!centerText) textX else textXCentered, rect.center.y),
                    color = textColor,
                    scale = textScale
                )
            }

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
