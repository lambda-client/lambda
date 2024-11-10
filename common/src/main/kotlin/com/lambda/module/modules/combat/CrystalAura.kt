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
import com.lambda.config.groups.Targeting
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.RotationManager.requestRotation
import com.lambda.interaction.visibilty.VisibilityChecker.lookAtBlock
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.tasks.BreakBlock
import com.lambda.util.combat.Explosion.explosionDamage
import com.lambda.util.math.VecUtils.dist
import com.lambda.util.world.blockSearch
import net.minecraft.block.Blocks
import net.minecraft.block.SideShapeType
import net.minecraft.entity.LivingEntity
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object CrystalAura : Module(
    name = "CrystalAura",
    description = "Automatically attacks entities with crystals",
    defaultTags = setOf(ModuleTag.COMBAT),
) {
    private val page by setting("Page", Page.Targeting)

    /* Targeting */
    private val targeting = Targeting.Combat(this) { page == Page.Targeting }

    /* Rotation */
    private val rotate by setting("Rotate", true) { page == Page.Rotation }
    private val rotation = RotationSettings(this) { page == Page.Rotation && rotate }

    /* Placing */
    private val doPlace by setting("Do Place", true) { page == Page.Placing }
    private val placing = InteractionSettings(this) { page == Page.Placing }
    private val swap by setting("Swap", Hand.MAIN_HAND, description = "Automatically swap to place crystals") { page == Page.Placing }

    //private val multiPlace by setting("Multi Place", true, description = "Place crystals") { page == Page.Placing }
    private val minSeparation by setting("Minimum Crystal Separation", 2, 1..3, 1, description = "The minimum space between crystals", unit = "blocks") { page == Page.Placing }


    private val placeDelay by setting("Place Delay", 0, 0..20, 1, description = "Delay between crystal placements", unit = "ticks") { page == Page.Placing }
    private val placeMinHealth by setting("Place Min Health", 10.0, 0.0..20.0, 0.5, description = "Minimum health to place a crystal") { page == Page.Placing }
    private val placeMaxSelfDamage by setting("Place Max Self Damage", 8.0, 0.0..20.0, 0.5, description = "Maximum self damage to place a crystal") { page == Page.Placing }
    private val placeMinDamage by setting("Place Min Damage", 6.0, 0.0..20.0, 0.5, description = "Minimum damage to place a crystal") { page == Page.Placing }

    /* Exploding */
    private val doExplode by setting("Do Explode", true) { page == Page.Exploding }
    private val explodeDelay by setting("Explode Delay", 0, 0..20, 1, description = "Delay between crystal explosions", unit = "ticks") { page == Page.Exploding }
    private val explodeRange by setting("Explode Range", 5.0, 0.1..7.0, 0.1, description = "Range to explode crystals") { page == Page.Exploding }
    private val preventDeath by setting("Prevent Death", true, description = "Prevent death from crystal explosions") { page == Page.Exploding }
    private val explodeMinDamage by setting("Explode Min Damage", 6.0, 0.0..20.0, 0.5, description = "Minimum damage to explode a crystal") { page == Page.Exploding }
    private val noWeakness by setting("No Weakness", true, description = "Switch to a weapon when you have a weakness effect") { page == Page.Exploding }

    /* Rendering */
    private val rendering = Targeting.ESP(this) { page == Page.Rendering }

    val targetEntity: LivingEntity? get() = targeting.target()
    val validPositions = mutableListOf<BlockPos>()

    init {
        BreakBlock
        requestRotation(
            onUpdate = {
                val blockpos =
                    validPositions.removeFirstOrNull() ?: return@requestRotation null
                if (!rotate) return@requestRotation null

                lookAtBlock(blockpos, rotation, placing)
            },
        )

        listener<TickEvent.Pre> {
            targetEntity?.let { target ->
                // TODO: If we could provide our own selector to [PlaceFinder] we could use it instead of this
                // Or maybe not since we want a fast way of finding the best positions
                validPositions.clear() // not good
                validPositions.addAll(
                    blockSearch(
                        range = placing.reach.toInt(),
                        step = minSeparation,
                        pos = target.blockPos, // Search around the target and not the player
                    ) { pos, state ->
                        !state.isSideSolid(world, pos, Direction.UP, SideShapeType.FULL) &&
                                (state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK)) &&
                                // If the distance between the player and the place position for the
                                // target is greater than the explosion range, we cannot use said
                                // position as it is impossible to reach
                                player dist pos <= explodeRange
                    }.keys
                        .asSequence()
                        // https://minecraft.wiki/w/Explosion#Damage
                        .sortedByDescending { explosionDamage(it, target, 6.0) }
                )
            }
        }
    }

    private enum class Page {
        Targeting,
        Rotation,
        Placing,
        Exploding,
        Rendering
    }
}
