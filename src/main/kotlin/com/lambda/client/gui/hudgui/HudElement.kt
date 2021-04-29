package com.lambda.client.gui.hudgui

import com.lambda.client.gui.rgui.Component
import com.lambda.client.setting.GuiConfig
import com.lambda.client.setting.settings.SettingRegister

internal abstract class HudElement(
    name: String,
    alias: Array<String> = emptyArray(),
    category: Category,
    description: String,
    alwaysListening: Boolean = false,
    enabledByDefault: Boolean = false,
) : AbstractHudElement(name, alias, category, description, alwaysListening, enabledByDefault, GuiConfig),
    SettingRegister<Component> by GuiConfig