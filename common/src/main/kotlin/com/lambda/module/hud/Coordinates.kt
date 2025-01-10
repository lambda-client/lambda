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

import com.lambda.context.SafeContext
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.Formatting.string

object Coordinates : HudModule(
    name = "Coordinates",
    description = "Show your coordinates",
    defaultTags = setOf(ModuleTag.CLIENT),
) {
    private val SafeContext.text: String
        get() = "Position: ${player.pos.string}"

    // TODO: Replace by LambdaAtlas height cache and actually build a proper text with highlighted parameters

    override val height: Double get() = 20.0
    override val width: Double get() = 50.0

    init {
        onRender {
            runSafe {
                font.build(text, position)
            }
        }
    }
}
