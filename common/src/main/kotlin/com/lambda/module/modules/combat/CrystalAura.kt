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

package com.lambda.module.modules.combat

import com.lambda.config.groups.InteractionSettings
import com.lambda.config.groups.RotationSettings
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listenConcurrently
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import net.minecraft.util.Hand

object CrystalAura : Module(
    name = "CrystalAura",
    description = "Automatically attacks entities with crystals",
    defaultTags = setOf(ModuleTag.COMBAT),
) {
    private val page by setting("Page", Page.General)

    /* Rotation */
    private val rotation = RotationSettings(this) { page == Page.Targeting }

    /* Placing */
    private val swap by setting("Swap", Hand.MAIN_HAND, "Automatically swap to crystals") { page == Page.Placing }
    private val multiPlace by setting("Multi Place", true, "Place multiple crystals") { page == Page.Placing }
    private val placeDelay by setting("Place Delay", 0, 0..20, 1, "Delay between crystal placements", unit = "ticks", visibility = { page == Page.Placing })
    private val placeRange by setting("Place Range", 5.0, 0.1..7.0, 0.1, "Range to place crystals from the player eyes", visibility = { page == Page.Placing })
    private val placeRangeWalls by setting("Place Range Walls", 3.5, 0.1..7.0, 0.1, "Range to place crystals through walls", visibility = { page == Page.Placing })
    private val placeMinHealth by setting("Place Min Health", 10.0, 0.0..20.0, 0.5, "Minimum health to place a crystal", visibility = { page == Page.Placing })
    private val placeMaxSelfDamage by setting("Place Max Self Damage", 8.0, 0.0..20.0, 0.5, "Maximum self damage to place a crystal", visibility = { page == Page.Placing })
    private val placeMinDamage by setting("Place Min Damage", 6.0, 0.0..20.0, 0.5, "Minimum damage to place a crystal", visibility = { page == Page.Placing })

    /* Exploding */
    private val explode by setting("Explode", true, "Explode crystals") { page == Page.Exploding }
    private val explodeDelay by setting("Explode Delay", 0, 0..20, 1, "Delay between crystal explosions", unit = "ticks", visibility = { page == Page.Exploding })
    private val explodeRange by setting("Explode Range", 5.0, 0.1..7.0, 0.1, "Range to explode crystals", visibility = { page == Page.Exploding })
    private val explodeRangeWalls by setting("Explode Range Walls", 3.5, 0.1..7.0, 0.1, "Range to explode crystals through walls", visibility = { page == Page.Exploding })
    private val preventDeath by setting("Prevent Death", true, "Prevent death from crystal explosions", visibility = { page == Page.Exploding })
    private val explodeMinDamage by setting("Explode Min Damage", 6.0, 0.0..20.0, 0.5, "Minimum damage to explode a crystal", visibility = { page == Page.Exploding })
    private val noWeakness by setting("No Weakness", true, "Switch to a weapon when you have a weakness effect", visibility = { page == Page.Exploding })

    /* Rendering */


    /* Interaction */
    private val interac = InteractionSettings(this) // Canadian interbank meme

    private enum class Page {
        General, Targeting, Placing, Exploding, Rendering
    }

    init {
        listenConcurrently<TickEvent.Pre> {}

        /*listener<RotationEvent.Pre> { event ->
            event.lookAtEntity(rotation, interac, getClosestEntity<LivingEntity>(player.eyePos, placeRange) ?: return@listener)
        }*/
    }
}
