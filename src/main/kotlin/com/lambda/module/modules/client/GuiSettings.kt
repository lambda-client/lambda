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
    internal val scaleSetting by setting("Scale", 100, 50..300, 1, unit = "%").group(Group.General)

    // Colors
    val primaryColor by setting("Primary Color", Color(130, 200, 255)).group(Group.Colors)
    val secondaryColor by setting("Secondary Color", Color(225, 130, 225)).group(Group.Colors)
    val shade by setting("Shade", true).group(Group.Colors)
    val colorWidth by setting("Shade Width", 200.0, 10.0..1000.0, 10.0).group(Group.Colors)
    val colorHeight by setting("Shade Height", 200.0, 10.0..1000.0, 10.0).group(Group.Colors)
    val colorSpeed by setting("Color Speed", 1.0, 0.1..5.0, 0.1).group(Group.Colors)

    // Snapping
    val snapEnabled by setting("Enable Snapping", true, "Master toggle for HUD snapping").group(Group.Snapping)
    val gridSize by setting("Grid Size", 16f, 2f..128f, 1f, "Grid step in pixels") { snapEnabled }.group(Group.Snapping)
    val snapToEdges by setting("Snap To Element Edges", true) { snapEnabled }.group(Group.Snapping)
    val snapToCenters by setting("Snap To Element Centers", true) { snapEnabled }.group(Group.Snapping)
    val snapToScreenCenter by setting("Snap To Screen Center", true) { snapEnabled }.group(Group.Snapping)
    val snapToGrid by setting("Snap To Grid", true) { snapEnabled }.group(Group.Snapping)
    val snapDistanceElement by setting("Snap Distance (Elements)", 20f, 1f..48f, 1f, "Distance threshold in px") { snapEnabled }.group(Group.Snapping)
    val snapDistanceScreen by setting("Snap Distance (Screen Center)", 14f, 1f..48f, 1f) { snapEnabled }.group(Group.Snapping)
    val snapDistanceGrid by setting("Snap Distance (Grid)", 12f, 1f..48f, 1f) { snapEnabled }.group(Group.Snapping)
    val snapLineColor by setting("Snap Line Color", Color(255, 160, 0, 220)) { snapEnabled }.group(Group.Snapping)

    // HUD Outline
    val hudOutlineCornerRadius by setting("HUD Corner Radius", 6.0f, 0.0f..24.0f, 0.5f).group(Group.HudOutline)
    val hudOutlineHaloColor by setting("HUD Corner Halo Color", Color(140, 140, 140, 90)).group(Group.HudOutline)
    val hudOutlineBorderColor by setting("HUD Corner Border Color", Color(190, 190, 190, 200)).group(Group.HudOutline)
    val hudOutlineHaloThickness by setting("HUD Corner Halo Thickness", 3.0f, 1.0f..6.0f, 0.5f).group(Group.HudOutline)
    val hudOutlineBorderThickness by setting("HUD Corner Border Thickness", 1.5f, 1.0f..4.0f, 0.5f).group(Group.HudOutline)
    val hudOutlineCornerInflate by setting("HUD Corner Inflate", 1.0f, 0.0f..4.0f, 0.5f, "Extra radius for the halo arc").group(Group.HudOutline)

    enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Colors("Colors"),
        Snapping("Snapping"),
        HudOutline("HUD Outline")
    }
}
