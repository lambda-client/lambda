package com.lambda.gui.impl.clickgui.buttons.setting

import com.lambda.config.AbstractSetting
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.math.ColorUtils.multAlpha
import com.lambda.util.math.Vec2d
import com.lambda.util.math.transform

abstract class SliderSetting <V : Any, T : AbstractSetting<V>>(
    setting: T, owner: ChildLayer.Drawable<*>
) : SettingButton<V, T>(setting, owner) {
    protected abstract val renderProgress: Double
    protected abstract fun setValueByProgress(progress: Double)

    init {
        renderer.filled {
            position = rect.moveSecond(Vec2d(-rect.size.x * (1.0 - renderProgress), 0.0)).shrink(interactAnimation)
            shade = GuiSettings.shade
            color(GuiSettings.mainColor.multAlpha(showAnimation * 0.3))
        }
    }

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        if (e is GuiEvent.MouseMove && pressed) {
            val p = transform(e.mouse.x, rect.left, rect.right, 0.0, 1.0)
            setValueByProgress(p.coerceIn(0.0, 1.0))
        }
    }
}