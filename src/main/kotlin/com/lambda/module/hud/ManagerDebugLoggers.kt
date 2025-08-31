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

import com.lambda.interaction.request.DebugLogger

object BreakManagerDebug : DebugLogger(
    "Break Manager Logger",
    "Logs actions performed in the break manager to aid in debugging"
)

object PlaceManagerDebug : DebugLogger(
    "Place Manager Logger",
    "Logs actions performed in the place manager to aid in debugging"
)

object InteractManagerDebug : DebugLogger(
    "Interact Manager Logger",
    "Logs actions performed in the interact manager to aid in debugging"
)

object RotationManagerDebug : DebugLogger(
    "Rotation Manager Logger",
    "Logs actions performed in the rotation manager to aid in debugging"
)

object HotbarManagerDebug : DebugLogger(
    "Hotbar Manager Logger",
    "Logs actions performed in the hotbar manager to aid in debugging"
)

object InventoryManagerDebug : DebugLogger(
    "Inventory Manager Logger",
    "Logs actions performed in the inventory manager to aid in debugging"
)