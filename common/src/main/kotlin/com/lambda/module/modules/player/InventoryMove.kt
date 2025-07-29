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

package com.lambda.module.modules.player

import com.lambda.Lambda.mc
import com.lambda.gui.LambdaScreen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.AnvilScreen
import net.minecraft.client.gui.screen.ingame.CommandBlockScreen
import net.minecraft.client.gui.screen.ingame.SignEditScreen

object InventoryMove : Module(
    name = "InventoryMove",
    description = "Allows you to move with GUIs opened",
    defaultTags = setOf(ModuleTag.PLAYER, ModuleTag.MOVEMENT)
) {
    private val speed by setting("Rotation Speed", 5, 1..20, 1, unit = "°/tick")

    /**
     * Whether the current screen has text inputs or is null
     */
    @JvmStatic
    fun hasInputOrNull(screen: Screen?) =
        screen is ChatScreen ||
                screen is SignEditScreen ||
                screen is AnvilScreen ||
                screen is CommandBlockScreen ||
                screen is LambdaScreen ||
                screen == null

    @JvmStatic
    fun isKeyMovementRelated(key: Int): Boolean {
        val options = mc.options
        return when (key) {
            options.forwardKey.boundKey.code,
            options.backKey.boundKey.code,
            options.leftKey.boundKey.code,
            options.rightKey.boundKey.code,
            options.jumpKey.boundKey.code,
            options.sprintKey.boundKey.code,
            options.sneakKey.boundKey.code -> true
            else -> false
        }
    }
}
