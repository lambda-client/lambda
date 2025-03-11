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

package com.lambda.module.hud

import com.lambda.graphics.renderer.gui.font.FontRenderer.drawString
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask
import com.lambda.util.math.Vec2d

object TaskFlowHUD : HudModule(
    name = "TaskFlowHud",
    defaultTags = setOf(ModuleTag.CLIENT),
) {
    override val width = 200.0
    override val height = 200.0

    init {
        onRender {
            drawString(RootTask.toString(), Vec2d.ZERO)
        }
    }
}
