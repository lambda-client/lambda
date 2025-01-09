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

import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag
import com.lambda.util.TpsClock.normalizedTickRate
import com.lambda.util.math.MathUtils.format

object TPS : HudModule(
    name = "TPS",
    description = "Display the server's tick rate",
    defaultTags = setOf(ModuleTag.CLIENT, ModuleTag.NETWORK),
) {
    private val format by setting("Tick format", TickFormat.Tick)

    private val text: String get() = "${format.string}: ${format.output().format(2)}"

    // TODO: Replace by LambdaAtlas height cache

    override val height: Double get() = 20.0
    override val width: Double get() = 50.0

    init {
        onRender {
            font.build(text, position)
        }
    }

    private enum class TickFormat(val output: () -> Double, val string: String) {
        Tick({ normalizedTickRate * 20 }, "TPS"),
        Milliseconds({ normalizedTickRate * 50 }, "MSPS"),
        Normalized({ normalizedTickRate }, "TPSN"),
        Percentage({ normalizedTickRate * 100 }, "TPS%")
    }
}
