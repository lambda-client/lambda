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

interface InteractionConfig {
    /**
     * Maximum entity interaction distance
     */
    val attackReach: Double

    /**
     * Maximum block interaction distance
     */
    val interactReach: Double

    /**
     * Maximum possible interaction distance
     *
     * Equals to `max(attackReach, placeReach)` if both are present. Equals to one of them otherwise
     */
    val scanReach: Double

    /**
     * Whether to include the environment to the ray cast context.
     *
     * if false: skips walls for entities, skips entities for blocks.
     */
    val strictRayCast: Boolean

    /**
     * Whether to check if an AABB side is visible.
     */
    val checkSideVisibility: Boolean

    /**
     * Grid divisions count per surface of the hit box.
     */
    val resolution: Int

    /**
     * The way to select the best point.
     */
    val pointSelection: PointSelection

    enum class InteractConfirmationMode {
        None,
        InteractThenAwait,
        AwaitThenInteract
    }
}
