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

interface BuildConfig {
    // General
    val pathing: Boolean
    val stayInRange: Boolean
    val collectDrops: Boolean
    val interactionsPerTick: Int
    val maxPendingInteractions: Int
    val interactionTimeout: Int

    // Breaking
    val breaking: BreakSettings

    // Placing
    val placing: PlaceSettings

    // Interacting
    val interacting: InteractSettings

    enum class SwingType {
        Vanilla,
        Server,
        Client
    }
}
