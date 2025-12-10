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

package com.lambda.context

import com.lambda.config.groups.BuildConfig
import com.lambda.config.groups.EatConfig
import com.lambda.interaction.managers.breaking.BreakConfig
import com.lambda.interaction.managers.hotbar.HotbarConfig
import com.lambda.interaction.managers.interacting.InteractConfig
import com.lambda.interaction.managers.inventory.InventoryConfig
import com.lambda.interaction.managers.rotating.RotationConfig

interface Automated {
    val buildConfig: BuildConfig
    val breakConfig: BreakConfig
    val interactConfig: InteractConfig
    val rotationConfig: RotationConfig
    val inventoryConfig: InventoryConfig
    val hotbarConfig: HotbarConfig
    val eatConfig: EatConfig
}