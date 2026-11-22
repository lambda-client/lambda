/*
 * Copyright 2026 Lambda
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

package com.lambda.module.modules.hud

import com.lambda.Lambda
import com.lambda.graphics.texture.TextureOwner.upload
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImGui
import com.lambda.module.HudModule
import com.lambda.module.ModuleTag

@Suppress("unused")
object Watermark : HudModule(
    name = "Watermark",
    tag = ModuleTag.HUD,
    enabledByDefault = true,
) {
    private val texture = upload("textures/lambda.png")
    private val showModName by setting("Show name of the mod", true)
    private val showVersion by setting("Show version of the client", false)

    override fun ImGuiBuilder.buildLayout() {
        val height = ImGui.getTextLineHeight()1

        val imageSizeX = texture.width * (height/texture.height)
        ImGui.image(texture.id.toLong(), imageSizeX, height)

        if (showModName){
            sameLine()
            text(Lambda.MOD_NAME)
        }
        if (showVersion){
            sameLine()
            text(Lambda.VERSION)
        }
    }
}
