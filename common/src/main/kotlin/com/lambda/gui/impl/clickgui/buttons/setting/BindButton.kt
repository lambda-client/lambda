package com.lambda.gui.impl.clickgui.buttons.setting

import com.lambda.config.settings.complex.KeyBindSetting
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.button.InputBarOverlay
import com.lambda.gui.api.component.core.list.ChildLayer
import com.lambda.gui.impl.AbstractClickGui
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.util.KeyCode
import com.lambda.util.math.ColorUtils.multAlpha
import com.lambda.util.extension.displayValue

class BindButton(
    setting: KeyBindSetting,
    owner: ChildLayer.Drawable<SettingButton<*, *>, ModuleButton>,
) : SettingButton<KeyCode, KeyBindSetting>(setting, owner) {
    private val layer = ChildLayer.Drawable(owner.gui, this, owner.renderer, ::rect, InputBarOverlay::isActive)
    private val inputBar: InputBarOverlay = object : InputBarOverlay(renderer, layer) {
        override val pressAnimation get() = this@BindButton.pressAnimation
        override val interactAnimation get() = this@BindButton.interactAnimation
        override val hoverFontAnimation get() = this@BindButton.hoverFontAnimation
        override val showAnimation get() = this@BindButton.showAnimation
        override val isKeyBind = true

        override fun getText() = value.displayValue
        override fun setKeyValue(key: KeyCode) {
            value = key
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
