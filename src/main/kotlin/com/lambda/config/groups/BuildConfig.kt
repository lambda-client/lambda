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

import com.lambda.interaction.request.rotating.visibilty.PointSelection
import com.lambda.util.Describable
import com.lambda.util.NamedEnum

interface BuildConfig {
    // General
    val pathing: Boolean
    val stayInRange: Boolean
    val collectDrops: Boolean
    val spleefEntities: Boolean
    val interactionsPerTick: Int
    val maxPendingInteractions: Int
    val interactionTimeout: Int
    val maxBuildDependencies: Int

    val useDefaultReach: Boolean
    val attackReach: Double
    val interactReach: Double
    val scanReach: Double

    val strictRayCast: Boolean
    val checkSideVisibility: Boolean
    val resolution: Int
    val pointSelection: PointSelection

    enum class SwingType(
        override val displayName: String,
        override val description: String
    ) : NamedEnum, Describable {
        Vanilla("Vanilla", "Play the hand swing locally and also notify the server (default, looks and works as expected)."),
        Server("Server", "Only notify the server to swing; local animation may not play unless the server echoes it."),
        Client("Client", "Only play the local swing animation; does not notify the server (purely visual).")
    }
}
