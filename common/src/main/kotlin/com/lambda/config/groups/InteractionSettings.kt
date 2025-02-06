/*
 * Copyright 2024 Lambda
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

import com.lambda.config.Configurable
import com.lambda.interaction.request.rotation.visibilty.PointSelection
import com.lambda.util.world.raycast.InteractionMask
import kotlin.math.max

class InteractionSettings(
    c: Configurable,
    private val usage: InteractionMask,
    vis: () -> Boolean = { true },
) : InteractionConfig {
    // Reach
    private val useDefaultReach by c.setting("Default Reach", true, "Whether to use vanilla interaction ranges", vis)
    private val attackReachSetting = if (usage.entity) c.setting("Attack Reach", DEFAULT_ATTACK_REACH, 1.0..10.0, 0.01, "Maximum entity interaction distance") { vis() && !useDefaultReach } else null
    private val interactReachSetting = if (usage.block) c.setting("Interact Reach", DEFAULT_INTERACT_REACH, 1.0..10.0, 0.01, "Maximum block interaction distance") { vis() && !useDefaultReach } else null

    override val attackReach: Double get() {
        check(usage.entity) {
            "Given interaction config has no attack reach implementation"
        }

        return if (useDefaultReach) DEFAULT_ATTACK_REACH else attackReachSetting!!.value
    }

    override val interactReach: Double get()  {
        check(usage.block) {
            "Given interaction config has no place reach implementation"
        }

        return if (useDefaultReach) DEFAULT_INTERACT_REACH else interactReachSetting!!.value
    }

    override val scanReach: Double get() = when (usage) {
        InteractionMask.ENTITY -> attackReach
        InteractionMask.BLOCK -> interactReach
        InteractionMask.BOTH -> max(attackReach, interactReach)
    }

    // Point scan
    override val strictRayCast by c.setting("Strict Raycast", true, "Whether to include the environment to the ray cast context", vis)
    override val checkSideVisibility by c.setting("Visibility Check", true, "Whether to check if an AABB side is visible", vis)
    override val resolution by c.setting("Resolution", 5, 1..20, 1, "The amount of grid divisions per surface of the hit box", "", vis)
    override val pointSelection by c.setting("Point Selection", PointSelection.Optimum, "The way to select the best point", vis)

    // Swing
    override val swingHand by c.setting("Swing Hand", true, "Whether to swing hand on interactions", vis)

    companion object {
        const val DEFAULT_ATTACK_REACH = 3.0
        const val DEFAULT_INTERACT_REACH = 4.5
    }
}
