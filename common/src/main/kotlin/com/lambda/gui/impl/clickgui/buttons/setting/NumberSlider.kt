package com.lambda.gui.impl.clickgui.buttons.setting

import com.lambda.config.settings.NumericSetting
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.button.InputBarOverlay
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.clickgui.AbstractClickGui
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.gui.impl.clickgui.windows.ModuleWindow
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.Mouse
import com.lambda.util.math.ColorUtils.multAlpha
import com.lambda.util.math.ColorUtils.setAlpha
import com.lambda.util.math.MathUtils.lerp
import com.lambda.util.math.MathUtils.roundToStep
import com.lambda.util.math.MathUtils.typeConvert
import com.lambda.util.math.Vec2d
import com.lambda.util.math.normalize
import java.awt.Color

class NumberSlider <N>(
    setting: NumericSetting<N>,
    owner: ChildLayer.Drawable<*, ModuleButton>
) : SliderSetting<N, NumericSetting<N>>(
    setting, owner
) where N : Number, N : Comparable<N> {
    private val doubleRange get() = setting.range.let { it.start.toDouble()..it.endInclusive.toDouble() }
    private val targetProgress get() = doubleRange.normalize(value.toDouble())
    private val renderProgress0 by animation.exp(::targetProgress, 0.6)
    override val renderProgress get() = lerp(0.0, renderProgress0, showAnimation)

    private val layer = ChildLayer.Drawable(owner.gui, this, owner.renderer, ::rect, InputBarOverlay::isActive)
    private val inputBar: InputBarOverlay = object : InputBarOverlay(renderer, layer) {
        override val pressAnimation     get() = this@NumberSlider.pressAnimation
        override val interactAnimation  get() = this@NumberSlider.interactAnimation
        override val hoverFontAnimation get() = this@NumberSlider.hoverFontAnimation
        override val showAnimation      get() = this@NumberSlider.showAnimation

        override fun getInitText() = value.let(Number::toString)
        override fun setValue(string: String) {
            string.toDoubleOrNull()?.let(::setValue)
        }
    }.apply(layer::addChild)

    override val textColor get() = super.textColor.multAlpha(1.0 - inputBar.activeAnimation)

    init {
        renderer.font {
            text = value.let(Number::toString)

            val progress = 1.0 - inputBar.activeAnimation
            scale = lerp(0.5, 1.0, progress)
            position = Vec2d(rect.right, rect.center.y) - Vec2d(ClickGui.windowPadding + stringWidth, 0.0)
            color = Color.WHITE.setAlpha(lerp(0.0, progress, showAnimation))
        }
    }

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)
        layer.onEvent(e)
    }

    override fun performClickAction(e: GuiEvent.MouseClick) {
        if (e.button != Mouse.Button.Right) return

        val windows = (owner.gui as AbstractClickGui).windows
        windows.children.filterIsInstance<ModuleWindow>().forEach { moduleWindow ->
            moduleWindow.contentComponents.children.forEach { moduleButton ->
                moduleButton.settingsLayer.children
                    .filterIsInstance<NumberSlider<*>>()
                    .apply { (this as MutableList).remove(this@NumberSlider) }
                    .forEach { it.inputBar.isActive = false }
            }
        }

        inputBar.toggle()
    }

    override fun slide(mouse: Vec2d) {
        if (!inputBar.isActive) super.slide(mouse)
    }

    override fun setValueByProgress(progress: Double) {
        setValue(lerp(
            setting.range.start.toDouble(),
            setting.range.endInclusive.toDouble(),
            progress
        ))
    }

    private fun setValue(valueIn: Double) {
        value = value.typeConvert(valueIn.roundToStep(setting.step.toDouble()))
    }
}