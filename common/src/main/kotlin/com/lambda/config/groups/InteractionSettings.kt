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
import com.lambda.threading.runSafe
import net.minecraft.entity.player.PlayerEntity

// TODO: Rewrite the group settings plz
class InteractionSettings(
    c: Configurable,
    useDefaultReach: Boolean = true,
    cReach: Double = 3.0,
    vis: () -> Boolean = { true },
) : InteractionConfig {
    override val defaultReach by c.setting("Default Reach", useDefaultReach)
    private val customReach by c.setting("Reach", if (!defaultReach) cReach else 4.5, 0.1..10.0, 0.1, "Players reach / range", " blocks") { vis() && !defaultReach }
    override val reach: Double
        get() = if (defaultReach) runSafe { PlayerEntity.getReachDistance(interaction.currentGameMode.isCreative).toDouble() } ?: 4.5 else customReach
    override val useRayCast by c.setting("Raycast", true, "Verify hit vector with ray casting (for very strict ACs)", vis)
    override val visibilityCheck by c.setting("Visibility Check", true, "Check if target is visible", vis)
    override val resolution by c.setting("Resolution", 4, 1..40, 1, "How many raycast checks per surface (will be squared)") { vis() && useRayCast }
    override val swingHand by c.setting("Swing Hand", true, "Swing hand on interactions", vis)
    override val pingTimeout by c.setting("Ping Timeout", false, "Timeout on high ping", vis)
    override val inScopeThreshold by c.setting("Constant Timeout", 1, 0..20, 1, "How many ticks to wait after target box is in rotation scope"," ticks") {
        vis() && !pingTimeout
    }
}
