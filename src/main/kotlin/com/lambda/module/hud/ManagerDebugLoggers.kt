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

import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.interaction.request.DebugLogger
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag
import com.lambda.util.NamedEnum

@Suppress("Unused")
object ManagerDebugLoggers : HudModule(
    "Manager Debug Loggers",
    "debug loggers for all action managers in lambda",
    ModuleTag.HUD,
    customWindow = true
) {
    enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Break("Break"),
        Place("Place"),
        Interact("Interact"),
        Rotation("Rotation"),
        Hotbar("Hotbar"),
        Inventory("Inventory")
    }

    private val loggers = mutableMapOf<() -> Boolean, DebugLogger>()

    val maxLogEntries by setting("Max Log Entries", 100, 1..1000, 1, "Maximum amount of entries in the log").group(Group.General)
        .onValueChange { from, to ->
            if (to < from) {
                loggers.values.forEach { logger ->
                    while(logger.logs.size > to) {
                        logger.logs.removeFirst()
                    }
                }
            }
        }

    private val showBreakManager by setting("Show Break Manager Logger", false).group(Group.Break)
    val breakManagerLogger = DebugLogger("Break Manager Logger").store { showBreakManager }

    private val showPlaceManager by setting("Show Place Manager Logger", false).group(Group.Place)
    val placeManagerLogger = DebugLogger("Place Manager Logger").store { showPlaceManager }

    private val showInteractionManager by setting("Show Interaction Manager Logger", false).group(Group.Interact)
    val interactionManagerLogger = DebugLogger("Interaction Manager Logger").store { showInteractionManager }

    private val showRotationManager by setting("Show Rotation Manager Logger", false).group(Group.Rotation)
    val rotationManagerLogger = DebugLogger("Rotation Manager Logger").store { showRotationManager }

    private val showHotbarManager by setting("Show Hotbar Manager Logger", false).group(Group.Hotbar)
    val hotbarManagerLogger = DebugLogger("Hotbar Manager Logger").store { showHotbarManager }

    private val showInventoryManager by setting("Show Inventory Manager Logger", false).group(Group.Inventory)
    val inventoryManagerLogger = DebugLogger("Inventory Manager Logger").store { showInventoryManager }

    private fun DebugLogger.store(show: () -> Boolean) =
        also { loggers.put(show, this) }

    override fun ImGuiBuilder.buildLayout() {
        loggers.entries.forEach { entry ->
            if (entry.key()) with(entry.value) {
                buildLayout()
            }
        }
    }
}