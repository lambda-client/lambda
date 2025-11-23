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

import com.lambda.util.Describable
import com.lambda.util.NamedEnum

interface ActionConfig {
    val sorter: SortMode

    enum class SortMode(
        override val displayName: String,
        override val description: String
    ) : NamedEnum, Describable {
        Closest("Closest", "Breaks blocks closest to the player eye position"),
        Farthest("Farthest", "Breaks blocks farthest from the player eye position"),
        Tool("Tool", "Breaks blocks with priority given to those with tools matching the current selected"),
        Rotation("Rotation", "Breaks blocks closest to the player rotation"),
        Random("Random", "Breaks blocks in a random order")
    }
}