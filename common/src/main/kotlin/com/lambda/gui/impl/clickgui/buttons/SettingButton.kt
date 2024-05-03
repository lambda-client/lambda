package com.lambda.gui.impl.clickgui.buttons

import com.lambda.config.AbstractSetting
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.button.ListButton
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.MathUtils.toInt

abstract class SettingButton <V : Any, T : AbstractSetting<V>> (
    val setting: T,
    owner: WindowComponent<*>
): ListButton(owner) {
    protected var value by setting
    var visible = true

    private var visibilityAnimation by animation.exp(0.0, 1.0, 0.6, ::visible)
    override val showAnimation get() = lerp(0.0, super.showAnimation, visibilityAnimation)
    override val targetHeightOffset: Double get() {
        var out = super.targetHeightOffset
        if (!visible) out -= size.y * 0.5
        return out
    }

    override var accessible: Boolean = false; get() = field && visible

    override fun onEvent(e: GuiEvent) {
        if (e is GuiEvent.Show || e is GuiEvent.Tick) visible = setting.visibility()
        if (e is GuiEvent.Show) visibilityAnimation = visible.toInt().toDouble()
        super.onEvent(e)
    }
}