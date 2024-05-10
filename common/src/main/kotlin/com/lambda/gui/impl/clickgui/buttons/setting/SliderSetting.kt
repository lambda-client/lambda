package com.lambda.gui.impl.clickgui.buttons.setting

import com.lambda.config.AbstractSetting
import com.lambda.core.LambdaSound
import com.lambda.core.SoundManager.playSound
import com.lambda.core.SoundManager.playSoundRandomly
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.math.ColorUtils.multAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.Vec2d
import com.lambda.util.math.transform
import kotlin.math.abs

abstract class SliderSetting <V : Any, T : AbstractSetting<V>>(
    setting: T, owner: ChildLayer.Drawable<*>
) : SettingButton<V, T>(setting, owner) {
    protected abstract val renderProgress: Double
    protected abstract fun setValueByProgress(progress: Double)
    private var lastPlayedValue = value
    private var lastPlayedTiming = 0L

    init {
        renderer.filled {
            position = rect.moveSecond(Vec2d(-rect.size.x * (1.0 - renderProgress), 0.0)).shrink(interactAnimation)
            shade = GuiSettings.shade
            color(GuiSettings.mainColor.multAlpha(showAnimation * 0.3))
        }
    }

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        when (e) {
            is GuiEvent.MouseMove -> {
                if (!pressed) return
                val p = transform(e.mouse.x, rect.left, rect.right, 0.0, 1.0).coerceIn(0.0, 1.0)
                setValueByProgress(p)

                val time = System.currentTimeMillis()
                if (lastPlayedValue == value || time - lastPlayedTiming < 50) return

                lastPlayedValue = value
                lastPlayedTiming = time

                playSound(LambdaSound.BUTTON_CLICK.event, lerp(0.9, 1.2, p))
            }
        }
    }
}