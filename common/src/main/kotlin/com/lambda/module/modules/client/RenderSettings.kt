package com.lambda.module.modules.client

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object RenderSettings : Module(
    name = "RenderSettings",
    description = "Renderer configuration",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    private val page by setting("Page", Page.Font)

    // Font
    val shadow by setting("Shadow", true) { page == Page.Font }
    val shadowBrightness by setting("Shadow Brightness", 0.35, 0.0..0.5, 0.01) { page == Page.Font && shadow }
    val shadowShift by setting("Shadow Shift", 1.0, 0.0..2.0, 0.05) { page == Page.Font && shadow }
    val gap by setting("Gap", 1.5, -10.0..10.0, 0.5) { page == Page.Font }
    val baselineOffset by setting("Vertical Offset", 0.0, -10.0..10.0, 0.5) { page == Page.Font }
    private val lodBiasSetting by setting("Smoothing", 0.0, -10.0..10.0, 0.5) { page == Page.Font }

    // ESP
    val uploadsPerTick by setting("Uploads", 16, 1..256, 1, unit = " chunk/tick") { page == Page.ESP }
    val rebuildsPerTick by setting("Rebuilds", 64, 1..256, 1, unit = " chunk/tick") { page == Page.ESP }
    val vertexMapping by setting("Vertex Mapping", true) { page == Page.ESP }
    val updateFrequency by setting("Update Frequency", 2, 1..10, 1, "Frequency of block updates", unit = " ticks") { page == Page.ESP }
    val outlineWidth by setting("Outline Width", 1.0, 0.1..5.0, 0.1, "Width of block outlines", unit = "px") { page == Page.ESP }

    val lodBias get() = lodBiasSetting * 0.25f - 0.75f

    private enum class Page {
        Font,
        ESP
    }
}
