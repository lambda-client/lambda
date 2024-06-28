package com.lambda.gui.impl.clickgui.buttons.setting

import com.lambda.config.AbstractSetting
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.module.modules.client.ClickGui
import com.lambda.module.modules.client.GuiSettings
import com.lambda.sound.LambdaSound
import com.lambda.sound.SoundManager.playSound
import com.lambda.util.Mouse
import com.lambda.util.math.ColorUtils.multAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.Vec2d
import com.lambda.util.math.transform

abstract class Slider<V : Any, T : AbstractSetting<V>>(
    setting: T, owner: ChildLayer.Drawable<SettingButton<*, *>, ModuleButton>,
) : SettingButton<V, T>(setting, owner) {
    protected abstract val progress: Double

    // Force this slider to follow mouse when dragging instead of rounding to the closest setting value
    private val progressAnimation by animation.exp({ mouseX?.let(::getProgressByMouse) ?: progress }, 0.6)
    private val renderProgress get() = lerp(0.0, progressAnimation, showAnimation)

    protected abstract fun setValueByProgress(progress: Double)
    private var lastPlayedValue = value
    private var lastPlayedTiming = 0L

    private var mouseX: Double? = null; get() {
        if (activeButton != Mouse.Button.Left) field = null
        return field
    }

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        when (e) {
            is GuiEvent.Render -> {
                // Slider rect
                renderer.filled.build(
                    rect = rect.moveSecond(Vec2d(-rect.size.x * (1.0 - renderProgress), 0.0)).shrink(shrinkAnimation),
                    roundRadius = ClickGui.buttonRadius,
                    color = GuiSettings.mainColor.multAlpha(showAnimation * 0.3),
                    shade = GuiSettings.shade
                )

                slide()
            }

            is GuiEvent.MouseMove -> {
                mouseX = e.mouse.x
            }
        }
    }

    override fun onPress(e: GuiEvent.MouseClick) {
        super.onPress(e)
        mouseX = e.mouse.x
    }

    protected open fun slide() = mouseX?.let { mouseX ->
        setValueByProgress(getProgressByMouse(mouseX))
        playClickSound()
    }

    protected fun playClickSound() {
        val time = System.currentTimeMillis()
        if (lastPlayedValue == value || time - lastPlayedTiming < 50) return

        lastPlayedValue = value
        lastPlayedTiming = time

        playSound(LambdaSound.BUTTON_CLICK.event, lerp(0.9, 1.2, progress))
    }

    private fun getProgressByMouse(mouseX: Double) =
        transform(mouseX, rect.left, rect.right, 0.0, 1.0).coerceIn(0.0, 1.0)
}
