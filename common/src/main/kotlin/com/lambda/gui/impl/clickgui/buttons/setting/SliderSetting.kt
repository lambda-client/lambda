package com.lambda.gui.impl.clickgui.buttons.setting

import com.lambda.config.AbstractSetting
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.util.math.transform

abstract class SliderSetting <V : Any, T : AbstractSetting<V>>(
    setting: T, owner: ChildLayer.Drawable<*>
) : SettingButton<V, T>(setting, owner) {
    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        if (e is GuiEvent.MouseMove && pressed) {
            val p = transform(e.mouse.x, rect.left, rect.right, 0.0, 1.0)
            setValueByProgress(p.coerceIn(0.0, 1.0))
        }
    }

    protected abstract fun setValueByProgress(progress: Double)
}