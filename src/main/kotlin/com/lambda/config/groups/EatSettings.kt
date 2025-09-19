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

package com.lambda.config.groups

import com.lambda.config.Configurable
import com.lambda.event.events.TickEvent
import com.lambda.interaction.request.hotbar.HotbarConfig
import com.lambda.util.NamedEnum
import net.minecraft.item.Item
import net.minecraft.item.Items

class EatSettings(
    c: Configurable,
    baseGroup: NamedEnum,
    vis: () -> Boolean = { true }
) : EatConfig {
    val defaultWhitelist = listOf(Items.GOLDEN_CARROT)

    override val eatFood by c.setting("Eat Food", true, "Whether food should be eaten", vis).group(baseGroup)
    override val eatUntilFull by c.setting("Eat Until Full", false, "Eat until the food level is full")  { vis() && eatFood }.group(baseGroup)
    override val minFoodLevel by c.setting("Minimum Food Level", 6, 0..20, 1, "The minimum food level to eat food", " food level") { vis() && eatFood }.group(baseGroup)
    override val eatOnFire by c.setting("Eat On Fire", false, "Eat when you are on fire")  { vis() && eatFood }.group(baseGroup)
    override val eatHeal by c.setting("Eat Heal", false, "Eat healing food when you are below the minimum health level")  { vis() && eatFood }.group(baseGroup)
    override val selectionMode by c.setting("Selection Mode", EatConfig.SelectionMode.Whitelist, "The selection mode for eating")  { vis() && eatFood }.group(baseGroup)
    override val whitelist by c.setting("Whitelist", defaultWhitelist, defaultWhitelist, "The whitelist of items to eat")  { vis() && eatFood }.group(baseGroup)
    override val blacklist by c.setting("Blacklist", listOf(), listOf<Item>(), "The blacklist of items to eat")  { vis() && eatFood }.group(baseGroup)
}