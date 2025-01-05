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
import com.lambda.context.SafeContext
import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.renderer.esp.builders.build
import com.lambda.interaction.RotationManager.rotate
import com.lambda.interaction.visibilty.VisibilityChecker.lookAtBlock
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.combat.CombatUtils.explosionDamage
import com.lambda.util.math.VecUtils.dist
import com.lambda.util.math.transform
import com.lambda.util.world.blockSearch
import com.lambda.util.world.fastEntitySearch
import net.minecraft.block.Blocks
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.decoration.EndCrystalEntity
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import java.awt.Color
import java.time.Instant

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
    private val placeMethod by setting("Place Sort", DamageSort.Deadly)
    //private val multiPlace by setting("Multi Place", true, description = "Place crystals") { page == Page.Placing }
    private val minSeparation by setting("Minimum Crystal Separation", 1, 1..3, 1, description = "The minimum space between crystals", unit = "blocks") { page == Page.Placing }
    private val placeDelay by setting("Place Delay", 0L, 0L..1000L, 10L, description = "Delay between crystal placements", unit = "ms") { page == Page.Placing }
    private val placeMinHealth by setting("Place Min Health", 10.0, 0.0..36.0, 0.5, description = "Minimum health to place a crystal") { page == Page.Placing }
    private val placeMinDamage by setting("Place Min Damage", 6.0, 0.0..20.0, 0.5, description = "Minimum damage to place a crystal") { page == Page.Placing }
    private val placeMaxSelfDamage by setting("Place Max Self Damage", 8.0, 0.0..20.0, 0.5, description = "Maximum self damage to place a crystal") { page == Page.Placing }

    /* Exploding */
    private val doExplode by setting("Do Explode", true) { page == Page.Exploding }
    private val explodeDelay by setting("Explode Delay", 0, 0..20, 1, description = "Delay between crystal explosions", unit = "ticks") { page == Page.Exploding }
    private val explodeRange by setting("Explode Range", 5.0, 0.1..7.0, 0.1, description = "Range to explode crystals") { page == Page.Exploding }
    private val explodeMethod by setting("Explode Sort", DamageSort.Deadly)
    private val explodeMinDamage by setting("Explode Min Damage", 6.0, 0.0..20.0, 0.5, description = "Minimum damage to explode a crystal") { page == Page.Exploding }

    /* Rendering */
    private val renderCrystals by setting("Render Crystal", true) { page == Page.Rendering }
    private val crystalColor by setting("Color Comparator", ColorComparator.Damage) { page == Page.Rendering && renderCrystals }
    private val crystalAlpha by setting("Color Alpha", 128, 0..255)

    private val placedCrystal = LimitedDecayQueue<BlockPos>(64, 1000L)
    private val target: LivingEntity? get() = targeting.target()

    private fun SafeContext.worldCrystals(target: LivingEntity): List<EndCrystalEntity> {
        return fastEntitySearch<EndCrystalEntity>(explodeRange, pos = target.blockPos)
            .sortedByDescending { explodeMethod.sorted(this, target, it.blockPos) }
    }

    private fun SafeContext.validPositions(target: LivingEntity): Sequence<BlockPos> {
        return blockSearch(
            range = placing.reach.toInt(),
            step = minSeparation, // Need to change this behavior
            pos = player.blockPos,
        ) { pos, _ -> canPlace(pos, target) }
            .keys.asSequence()
            .sortedByDescending { placeMethod.sorted(this, target, it.up()) } // The explosion source of the crystal is not at its base
    }

    private fun SafeContext.canPlace(pos: BlockPos, target: LivingEntity): Boolean {
        // Checks if the distance between the player and the target
        // and the player and the place position is <= to the placing range
        return player dist target <= placing.reach &&
                player dist pos <= placing.reach &&
                // Checks if the support block is either obsidian or bedrock
                (pos.blockState(world).isOf(Blocks.OBSIDIAN)
                        || pos.blockState(world).isOf(Blocks.BEDROCK)) &&
                // Checks if the crystal can be placed on top of a block
                // It checks if there's another entity in the air block
                // Or if there's another crystal 2 blocks around the air block
                world.isAir(pos.up()) &&
                fastEntitySearch<Entity>(0.5, pos.up()).isEmpty() &&
                fastEntitySearch<EndCrystalEntity>(1.5, pos.up()).isEmpty() && // Doesn't handle the edge case where there is a crystal floating

                player.health + player.absorptionAmount >= placeMinHealth &&
                explosionDamage(pos, target, 6.0) >= placeMinDamage &&
                explosionDamage(pos, player, 6.0) <= placeMaxSelfDamage &&
                // Checks if the last crystal was set more than [placeDelay] ms ago
                placedCrystal.peek()?.second?.plusMillis(placeDelay)?.isBefore(Instant.now()) ?: true
        //if (multiPlace) !placedCrystal.any { (crystalPos, _) -> pos.up() == crystalPos } else true
    }


    init {
        rotate(
            priority = 100,
        ) {
           onUpdate {
               if (!rotate) return@onUpdate null

               val blockpos = validPositions(
                   target ?: return@onUpdate null
               ).firstOrNull() ?: return@onUpdate null

               // Taskflow interact block
               // placedCrystal.add()
               lookAtBlock(blockpos, rotation, placing, sides = setOf(Direction.UP))
           }
        }

        listen<RenderEvent.StaticESP> {
            worldCrystals(target ?: return@listen).forEach { crystal ->
                it.renderer.build(
                    Box.of(crystal.pos, 1.0, 1.0, 1.0),
                    crystalColor.compared(this, target ?: return@forEach, crystal.blockPos),
                    crystalColor.compared(this, target ?: return@forEach, crystal.blockPos)
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

    private enum class DamageSort(val sorted: SafeContext.(LivingEntity, BlockPos) -> Double) {
        Deadly({ target, source -> explosionDamage(source, target, 6.0) }),
        Balanced({ target, source -> explosionDamage(source, player, 6.0) - explosionDamage(source, target, 6.0) }),
        Safe({ target, source -> explosionDamage(source, target, 6.0) - explosionDamage(source, player, 6.0) });
    }

    private enum class ColorComparator(val compared: SafeContext.(LivingEntity, BlockPos) -> Color) {
        Distance({ target, dest ->
            val red = transform(target dist dest, 0.0, explodeRange, 255.0, 0.0).toInt()
            Color(red, 255-red, 0, crystalAlpha)
        }),

        Damage({ target, dest ->
            val red = transform(explosionDamage(dest, target, 6.0), 0.0, target.health.toDouble(), 0.0, 255.0).toInt()
            Color(red, 255-red, 0, crystalAlpha)
        })
    }
}
