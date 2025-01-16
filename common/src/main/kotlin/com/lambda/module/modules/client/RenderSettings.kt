/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.module.modules.client

import com.lambda.graphics.renderer.gui.font.core.LambdaEmoji
import com.lambda.graphics.renderer.gui.font.core.LambdaFont
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import java.awt.Color

object RenderSettings : Module(
    name = "RenderSettings",
    description = "Renderer configuration",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    private val page by setting("Page", Page.Font)

    // Font
    val textFont by setting("Text Font", LambdaFont.FiraSansRegular)
    val emojiFont by setting("Emoji Font", LambdaEmoji.Twemoji)
    val shadow by setting("Shadow", true) { page == Page.Font }
    val shadowBrightness by setting("Shadow Brightness", 0.35, 0.0..0.5, 0.01) { page == Page.Font && shadow }
    val shadowShift by setting("Shadow Shift", 1.0, 0.0..2.0, 0.05) { page == Page.Font && shadow }
    val gap by setting("Gap", 1.5, -10.0..10.0, 0.5) { page == Page.Font }
    val baselineOffset by setting("Vertical Offset", 0.0, -10.0..10.0, 0.5) { page == Page.Font }
    val highlightColor by setting("Text Highlight Color", Color(214, 55, 87), visibility = { page == Page.Font })

    // ESP
    val uploadsPerTick by setting("Uploads", 16, 1..256, 1, unit = " chunk/tick") { page == Page.ESP }
    val rebuildsPerTick by setting("Rebuilds", 64, 1..256, 1, unit = " chunk/tick") { page == Page.ESP }
    val updateFrequency by setting("Update Frequency", 2, 1..10, 1, "Frequency of block updates", unit = " ticks") { page == Page.ESP }
    val outlineWidth by setting("Outline Width", 1.0, 0.1..5.0, 0.1, "Width of block outlines", unit = "px") { page == Page.ESP }

    private enum class Page {
        Font,
        ESP,
    }
}
