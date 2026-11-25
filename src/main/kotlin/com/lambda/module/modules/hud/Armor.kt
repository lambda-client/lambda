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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.lambda.config.settings.comparable.enumSetting
import com.lambda.config.settings.numeric.floatSetting
import com.lambda.gui.component.ItemIcon
import com.lambda.gui.core.observe
import com.lambda.gui.core.observeSafe
import com.lambda.gui.hud.HudText
import com.lambda.module.HudModule
import com.lambda.module.ModuleTag
import com.lambda.util.NamedEnum
import net.minecraft.entity.EquipmentSlot

@Suppress("unused")
object Armor : HudModule(
    name = "Armor",
    description = "Shows the armor you are wearing, as it looks in your inventory",
    tag = ModuleTag.HUD,
) {
    private enum class Layout(override val displayName: String) : NamedEnum {
        Horizontal("Horizontal"),
        Vertical("Vertical"),
    }

    private val layout by enumSetting("Layout", Layout.Horizontal)
    private val iconSize by floatSetting("Icon Size", 16f, 8f..64f, 1f, "Items are drawn at the nearest whole multiple of their 16 pixels, as Minecraft draws them")

    private val slots =
        listOf(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
        )

    @Composable
    override fun Content() {
        val worn by observeSafe { slots.map(player::getEquippedStack).filterNot { it.isEmpty } }
        val layout by observe { layout }
        val size by observe { iconSize.dp }

        val pieces = worn.orEmpty()
        if (pieces.isEmpty()) {
            HudText("No armor")
            return
        }
        val spacing = Arrangement.spacedBy(size / 8)
        when (layout) {
            Layout.Horizontal ->
                Row(horizontalArrangement = spacing) {
                    pieces.forEach { ItemIcon(it, size) }
                }
            Layout.Vertical ->
                Column(verticalArrangement = spacing) {
                    pieces.forEach { ItemIcon(it, size) }
                }
        }
    }
}
