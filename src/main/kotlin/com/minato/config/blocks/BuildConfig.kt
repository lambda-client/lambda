
package com.minato.config.blocks

import com.minato.interaction.managers.rotating.Rotation.Companion.dist
import com.minato.interaction.managers.rotating.RotationManager
import com.minato.util.Describable
import com.minato.util.NamedEnum
import com.minato.util.math.distSq
import com.minato.util.math.times
import com.minato.util.player.CheckedHit

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
