/*
 * Copyright 2025 Lambda
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

package com.lambda.module.hud

import com.lambda.graphics.renderer.gui.font.FontRenderer
import com.lambda.graphics.renderer.gui.font.FontRenderer.drawString
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Formatting.string
import com.lambda.util.NamedEnum
import com.lambda.util.ServerTPS.averageMSPerTick

object TPS : HudModule(
    name = "TPS",
    description = "Display the server's tick rate",
    defaultTags = setOf(ModuleTag.CLIENT, ModuleTag.NETWORK),
) {
    private val format by setting("Tick format", TickFormat.TPS)

    private val text: String get() = "${format.displayName}: ${format.output().string}${format.unit}"

    // TODO: Replace by LambdaAtlas height cache and actually build a proper text with highlighted parameters

    override val height: Double get() = FontRenderer.getHeight()
    override val width: Double get() = FontRenderer.getWidth(text)

    init {
        onRender {
            drawString(text, position)
        }
    }

    @Suppress("unused")
    private enum class TickFormat(
        val output: () -> Double,
        override val displayName: String,
        val unit: String = ""
    ) : NamedEnum {
        TPS({ 1000 / averageMSPerTick }, "TPS"),
        MSPT({ averageMSPerTick }, "MSPT", " ms"),
        Normalized({ 50 / averageMSPerTick }, "TPS"),
        Percentage({ 5000 / averageMSPerTick }, "TPS", "%")
    }
}
