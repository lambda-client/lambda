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

package com.lambda.module.modules.client

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.NamedEnum
import java.awt.Color

object GuiSettings : Module(
    name = "GuiSettings",
    description = "Visual behaviour configuration",
    tag = ModuleTag.CLIENT,
) {
    // General
    private val scaleSetting by setting("Scale", 100, 50..300, 1, unit = "%").group(Group.General)

    // Colors
    val primaryColor by setting("Primary Color", Color(130, 200, 255)).group(Group.Colors)
    val secondaryColor by setting("Secondary Color", Color(225, 130, 225)).group(Group.Colors)
    val shade by setting("Shade", true).group(Group.Colors)
    val colorWidth by setting("Shade Width", 200.0, 10.0..1000.0, 10.0).group(Group.Colors)
    val colorHeight by setting("Shade Height", 200.0, 10.0..1000.0, 10.0).group(Group.Colors)
    val colorSpeed by setting("Color Speed", 1.0, 0.1..5.0, 0.1).group(Group.Colors)

    enum class Group(override val displayName: String): NamedEnum {
        General("General"),
        Colors("Colors")
    }
}
