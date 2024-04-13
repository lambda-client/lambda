package com.lambda.module.modules.client

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object FontSettings : Module(
    name = "FontSettings",
    description = "Font renderer configuration",
    tag = ModuleTag.CLIENT
) {
    val shadow by setting("Shadow", true)
    val shadowBrightness by setting("Shadow Brightness", 0.35, 0.0..0.5, 0.01, visibility = { shadow })
    val shadowShift by setting("Shadow Shift", 1.0, 0.0..2.0, 0.05, visibility = { shadow })
    val gapSetting by setting("Gap", 1.5, -10.0..10.0, 0.5)
    val baselineOffset by setting("Vertical Offset", 0.0, -10.0..10.0, 0.5)
    val amountOfGlyphs by setting("Glyph Count", 2048, 128..65536, 1, description = "Restart required")
    private val lodBiasSetting by setting("Smoothing", 0.0, -10.0..10.0, 0.5)

    val lodBias get() = lodBiasSetting * 0.25f - 0.75f
}
