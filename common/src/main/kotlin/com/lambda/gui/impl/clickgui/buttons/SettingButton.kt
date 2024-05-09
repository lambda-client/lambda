package com.lambda.gui.impl.clickgui.buttons

import com.lambda.config.AbstractSetting
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.component.button.ListButton
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.util.math.MathUtils.lerp

abstract class SettingButton <V : Any, T : AbstractSetting<V>> (
    val setting: T,
    owner: ChildLayer.Drawable<*>
): ListButton(owner) {
    protected var value by setting

    val visible; get() = setting.visibility()

    private var visibilityAnimation by animation.exp(0.0, 1.0, 0.7, ::visible)
    override val showAnimation get() = lerp(0.0, super.showAnimation, visibilityAnimation)
    override var activeAnimation = 0.0

    override var renderHeightOffset
        get() = heightOffset + lerp(-size.y, 0.0, visibilityAnimation)
        set(value) { heightOffset = value }
}