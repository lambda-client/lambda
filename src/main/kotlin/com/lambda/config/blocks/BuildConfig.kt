/*
 * Copyright 2026 Lambda
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

package com.lambda.config.blocks

import com.lambda.interaction.manager.managers.rotating.Rotation.Companion.dist
import com.lambda.interaction.manager.managers.rotating.RotationManager
import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import com.lambda.util.math.distSq
import com.lambda.util.math.times
import com.lambda.util.player.CheckedHit

interface BuildConfig {
    val breakBlocks: Boolean
    val placeBlocks: Boolean
    val interactBlocks: Boolean

    val pathing: Boolean
    val collectDrops: Boolean
    val spleefEntities: Boolean
    val cautionDoubleBlocks: Boolean
    val maxPendingActions: Int
    val actionTimeout: Int
    val maxBuildDependencies: Int

    val limitTimeframe: Int
    val actionLimit: Int
    val interactionLimit: Int
    val inventoryLimit: Int

    val blockReach: Double
    val entityReach: Double
    val scanReach: Double

    val checkSideVisibility: Boolean
    val strictRayCast: Boolean
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

    @Suppress("unused")
    enum class PointSelection(
        override val displayName: String,
        override val description: String,
        val select: (Collection<CheckedHit>) -> CheckedHit?
    ) : NamedEnum, Describable {
        ByRotation(
            "By Rotation",
            "Choose the point that needs the least rotation from your current view (minimal camera turn).",
            select = { hits ->
                hits.minByOrNull { RotationManager.activeRotation dist it.rotation }
            }
        ),
        Optimum(
            "Optimum",
            "Choose the point closest to the average of all candidates (balanced and stable aim).",
            select = { hits ->
                val optimum = hits
                    .mapNotNull { it.hit.pos }
                    .reduceOrNull { acc, pos -> acc.add(pos) }
                    ?.times(1 / hits.size.toDouble())

                optimum?.let { center ->
                    hits.minByOrNull { it.hit.pos?.distSq(center) ?: 0.0 }
                }
            }
        )
    }
}
