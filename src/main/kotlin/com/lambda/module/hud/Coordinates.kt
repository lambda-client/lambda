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

import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.Formatting.asString
import com.lambda.util.Formatting.string
import com.lambda.util.extension.dimensionName
import com.lambda.util.extension.isNether
import com.lambda.util.math.netherCoord
import com.lambda.util.math.overworldCoord

object Coordinates : HudModule(
    name = "Coordinates",
    description = "Show your coordinates",
    tag = ModuleTag.HUD,
) {
    private val showDimension by setting("Show Dimension", true)
    private val decimals by setting("Decimals", 2, 0..4, 1)

    override fun ImGuiBuilder.buildLayout() {
        runSafe {
            val pos = player.pos.asString(decimals)
            val coord = if (world.isNether) {
                "$pos [${player.overworldCoord.x.string}, ${player.overworldCoord.z.string}]"
            } else {
                "$pos [${player.netherCoord.x.string}, ${player.netherCoord.z.string}]"
            }
            val dimension = if (showDimension) " ${world.dimensionName}" else ""
            textCopyable("$coord$dimension")
        }
    }
}
