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

import com.lambda.util.NamedEnum
import net.minecraft.item.Item

interface EatConfig {
    val eatFood: Boolean
    val minFoodLevel: Int
    val eatUntilFull: Boolean
    val eatOnFire: Boolean
    val eatHeal: Boolean

    val selectionMode: SelectionMode
    val whitelist: List<Item>
    val blacklist: List<Item>

    enum class SelectionMode(override val displayName: String): NamedEnum {
        Whitelist("Whitelist"),
        Blacklist("Blacklist")
    }
}