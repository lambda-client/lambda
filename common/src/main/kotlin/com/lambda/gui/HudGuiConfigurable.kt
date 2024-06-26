package com.lambda.gui

import com.lambda.gui.impl.hudgui.LambdaHudGui
import com.lambda.module.tag.ModuleTag

object HudGuiConfigurable : AbstractGuiConfigurable(
    LambdaHudGui, ModuleTag.hudDefaults, "hudgui"
)