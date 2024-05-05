package com.lambda.gui.impl.clickgui.buttons

import com.lambda.config.AbstractSetting
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.button.ListButton
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.MathUtils.toInt

abstract class SettingButton <V : Any, T : AbstractSetting<V>> (
    val setting: T,
    owner: ChildLayer.Drawable<*>
): ListButton(owner) {
    protected var value by setting
    var visible = false

    private var visibilityAnimation by animation.exp({ 0.0 }, { 1.0 }, { if (visible) 0.2 else 0.8 }, ::visible)
    override val showAnimation get() = lerp(0.0, super.showAnimation, visibilityAnimation)

    override var accessible: Boolean = false; get() = field && visible

    override fun onEvent(e: GuiEvent) {
        if (e is GuiEvent.Show || e is GuiEvent.Tick) visible = setting.visibility()
        if (e is GuiEvent.Show) visibilityAnimation = visible.toInt().toDouble()
        super.onEvent(e)
    }
}