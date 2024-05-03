package com.lambda.gui.impl.clickgui.windows

import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.ListWindow
import com.lambda.gui.impl.clickgui.AbstractClickGui
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.gui.impl.clickgui.buttons.SettingButton
import com.lambda.gui.impl.clickgui.buttons.setting.BooleanButton
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.math.MathUtils.lerp

class SettingWindow(
    val button: ModuleButton,
    owner: AbstractClickGui
) : ListWindow<SettingButton<*, *>>(owner) {
    private val module = button.module

    override val title = module.name
    override var width = button.owner.width
    override var height = 0.0

    private val showAnimation0 by animation.exp(0.0, 1.0, ClickGui.openSpeed, ::isOpen)
    override val showAnimation get() = lerp(0.0, showAnimation0, owner.showAnimation)

    init {
        button.module.settings.mapNotNull { setting ->
            when (setting) {
                is BooleanSetting -> BooleanButton(setting, this)
                else -> null
            }
        }.forEach(contentComponents::addChild)
    }

    override fun onEvent(e: GuiEvent) {
        super.onEvent(e)

        if (e is GuiEvent.Show || e is GuiEvent.Tick) {
            val c = contentComponents.children.filter { it.visible }
            height = c.sumOf { it.size.y } + ClickGui.buttonStep * (c.size - 1)
            if (showAnimation < 0.05 && !isOpen) destroy()
        }
    }
}