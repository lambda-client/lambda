package com.lambda.gui.impl.clickgui.buttons.setting

import com.lambda.config.settings.NumericSetting
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.math.ColorUtils.setAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.MathUtils.roundToStep
import com.lambda.util.math.MathUtils.typeConvert
import com.lambda.util.math.Vec2d
import com.lambda.util.math.normalize
import java.awt.Color

class NumberSlider <N>(
    setting: NumericSetting<N>,
    owner: ChildLayer.Drawable<*>
) : SliderSetting<N, NumericSetting<N>>(
    setting, owner
) where N : Number, N : Comparable<N> {
    private val doubleRange get() = setting.range.let { it.start.toDouble()..it.endInclusive.toDouble() }
    private val targetProgress get() = doubleRange.normalize(value.toDouble())
    private val renderProgress0 by animation.exp(::targetProgress, 0.6)
    override val renderProgress get() = lerp(0.0, renderProgress0, showAnimation)

    init {
        renderer.font {
            text = value.let(Number::toString)
            position = Vec2d(rect.right, rect.center.y) - Vec2d(ClickGui.windowPadding + stringWidth, 0.0)
            color = Color.WHITE.setAlpha(showAnimation)
        }
    }

    override fun setValueByProgress(progress: Double) {
        value = value.typeConvert(lerp(
            setting.range.start.toDouble(),
            setting.range.endInclusive.toDouble(),
            progress
        ).roundToStep(setting.step.toDouble()))
    }
}