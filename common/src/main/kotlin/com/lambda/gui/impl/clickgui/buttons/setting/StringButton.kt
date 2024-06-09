package com.lambda.gui.impl.clickgui.buttons.setting

import com.lambda.config.settings.StringSetting
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.button.InputBarOverlay
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.AbstractClickGui
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.util.math.ColorUtils.multAlpha

class StringButton(
    setting: StringSetting,
    owner: ChildLayer.Drawable<SettingButton<*, *>, ModuleButton>,
) : SettingButton<String, StringSetting>(setting, owner) {
    private val layer = ChildLayer.Drawable(owner.gui, this, owner.renderer, ::rect, InputBarOverlay::isActive)
    private val inputBar: InputBarOverlay = object : InputBarOverlay(renderer, layer) {
        override val pressAnimation get() = this@StringButton.pressAnimation
        override val interactAnimation get() = this@StringButton.interactAnimation
        override val hoverFontAnimation get() = this@StringButton.hoverFontAnimation
        override val showAnimation get() = this@StringButton.showAnimation

        override fun getText() = value
        override fun setStringValue(string: String) {
            value = string
        }
    }.apply(layer.children::add)

    override val textColor get() = super.textColor.multAlpha(1.0 - inputBar.activeAnimation)

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)
        layer.onEvent(e)
    }

    override fun unfocus() {
        inputBar.isActive = false
    }

    override fun performClickAction(e: GuiEvent.MouseClick) {
        if (!inputBar.isActive) (owner.gui as? AbstractClickGui)?.unfocusSettings()
        inputBar.toggle()
    }
}