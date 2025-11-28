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

import com.lambda.config.AutomationConfig.Companion.DEFAULT.edit
import com.lambda.config.groups.FormatterConfig
import com.lambda.config.groups.FormatterSettings
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.Formatting.format
import com.lambda.util.NamedEnum
import com.lambda.util.extension.dimensionName
import com.lambda.util.extension.isNether
import com.lambda.util.math.Vec2d
import com.lambda.util.math.netherCoord
import com.lambda.util.math.overworldCoord

object Coordinates : HudModule(
    name = "Coordinates",
    description = "Show your coordinates",
    tag = ModuleTag.HUD,
) {
    private val page by setting("Page", Page.CurrentDimension)
    private val showDimension by setting("Show Dimension", true)

    private val formatter = FormatterSettings(this, Page.CurrentDimension).apply { ::timeFormat.edit { hide() } }
    private val otherFormatter = FormatterSettings(this, Page.OtherDimension).apply {
        ::timeFormat.edit { hide() }
        ::group.edit { defaultValue(FormatterConfig.TupleGrouping.SquareBrackets) }
    }

    override fun ImGuiBuilder.buildLayout() {
        runSafe {
            val position = player.pos.format(formatter)
            val otherDimensionPos =
                if (world.isNether) player.overworldCoord.let { Vec2d(it.x, it.z) }.format(otherFormatter)
                else player.netherCoord.let { Vec2d(it.x, it.z) }.format(otherFormatter)

            val text = "$position $otherDimensionPos"

            val withDimension =
                if (showDimension) "$text ${world.dimensionName}"
                else text

            textCopyable(withDimension)
        }
    }

    enum class Page(override val displayName: String) : NamedEnum {
        CurrentDimension("Current Dimension"),
        OtherDimension("Other Dimension"),
    }
}
