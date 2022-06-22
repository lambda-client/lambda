package com.lambda.client.gui.rgui.windows

import com.lambda.client.commons.interfaces.Nameable
import com.lambda.client.gui.rgui.WindowComponent
import com.lambda.client.setting.GuiConfig
import com.lambda.client.setting.configs.AbstractConfig

/**
 * Window with no rendering
 */
open class CleanWindow(
    name: String,
    posX: Float,
    posY: Float,
    width: Float,
    height: Float,
    settingGroup: SettingGroup,
    config: AbstractConfig<out Nameable> = GuiConfig
) : WindowComponent(name, posX, posY, width, height, settingGroup, config)