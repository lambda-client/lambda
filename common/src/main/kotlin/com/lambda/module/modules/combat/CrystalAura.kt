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
import com.lambda.graphics.renderer.esp.builders.ofShape
import com.lambda.interaction.RotationManager.rotate
import com.lambda.interaction.visibilty.VisibilityChecker.lookAtBlock
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.combat.CombatUtils.explosionDamage
import com.lambda.util.math.VecUtils.dist
import com.lambda.util.math.VecUtils.vec3d
import com.lambda.util.math.transform
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.entity.LivingEntity
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.awt.Color

object CrystalAura : Module(
    name = "CrystalAura",
    description = "Automatically attacks entities with crystals",
    defaultTags = setOf(ModuleTag.COMBAT),
) {
    private val page by setting("Page", Page.Targeting)

    /* Targeting */
    // ToDo: Targeting Range should be reach + crystal range (also based on min damage)
    private val targeting = Targeting.Combat(this, 10.0) { page == Page.Targeting }

    /* Placing */
    private val doPlace by setting("Do Place", true) { page == Page.Placing }
    private val swap by setting("Swap", Hand.MAIN_HAND, description = "Automatically swap to place crystals") { page == Page.Placing }
    private val placeMethod by setting("Place Sort", DamageSort.Deadly) { page == Page.Placing }
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
    private val explodeMethod by setting("Explode Sort", DamageSort.Deadly) { page == Page.Exploding }
    private val explodeMinDamage by setting("Explode Min Damage", 6.0, 0.0..20.0, 0.5, description = "Minimum damage to explode a crystal") { page == Page.Exploding }

    /* Rotation */
    private val rotation = RotationSettings(this) { page == Page.Rotation }

    /* Interaction */
    private val interact = InteractionSettings(this) { page == Page.Interaction }

    /* Rendering */
    private val renderCrystals by setting("Render Crystal", true) { page == Page.Rendering }
    private val crystalColor by setting("Color Comparator", ColorComparator.Damage) { page == Page.Rendering && renderCrystals }
    private val crystalAlpha by setting("Color Alpha", 128, 0..255) { page == Page.Rendering && renderCrystals }

    private val placements = LimitedDecayQueue<BlockPos>(64, 1000L)
    private val target: LivingEntity? get() = targeting.target()

    init {
        rotate {
            onUpdate {
                val targetEntity = target ?: return@onUpdate null
                val validPositions = findTargetPositions(targetEntity)

                testRender.addAll(validPositions.map { it.blockPos })

                validPositions.firstNotNullOfOrNull {
                    lookAtBlock(it.blockPos, rotation, interact)
                }
            }
        }

        listen<RenderEvent.StaticESP> {
//            worldCrystals(target ?: return@listen).forEach { crystal ->
//                it.renderer.build(
//                    Box.of(crystal.blockPos.toCenterPos(), 1.0, 1.0, 1.0),
//                    crystalColor.compared(this, target ?: return@forEach, crystal.pos),
//                    crystalColor.compared(this, target ?: return@forEach, crystal.pos)
//                )
//            }

            val blurple = Color(85, 57, 204, 50)
            testRender.forEach { pos ->
                it.renderer.ofShape(pos, blurple, blurple)
            }

            testRender.clear()
        }
    }

//    private fun SafeContext.worldCrystals(target: LivingEntity) =
//        fastEntitySearch<EndCrystalEntity>(explodeRange, pos = target.blockPos)
//            .sortedByDescending { explodeMethod.sorted(this, target, it.blockPos) }

    private fun SafeContext.findTargetPositions(target: LivingEntity): List<TargetPosition> {
        // This formula is derived from the explosion damage scaling logic. The damage decreases linearly
        // with distance, modeled as `damage = (1 - (distance / (power * 2))) * exposure`, where power is the
        // explosion's strength (6.0) and exposure defines how much of the explosion affects the target.
        val maximumRange = ((1 - (placeMinDamage / 12.0)) * 12.0).toInt()

        return BlockPos.iterateOutwards(target.blockPos, maximumRange, maximumRange, maximumRange).mapNotNull { pos ->
            targetData(pos, pos.blockState(world), target)
        }.sortedWith(placeMethod.comparator)
    }

    private fun SafeContext.targetData(pos: BlockPos, state: BlockState, target: LivingEntity): TargetPosition? {
        val inRange = pos.dist(player.eyePos) < interact.reach + 1
        if (!inRange) return null
        val isOfBlock = state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK)
        if (!isOfBlock) return null
        if (pos in placements) return null
        val above = pos.up()
        val isAirAbove = world.isAir(above)
        if (!isAirAbove) return null
        val checkBox = Box(above).withMaxY(above.y + 2.0)
        val entitiesAbove = world.getOtherEntities(null, checkBox)
        if (entitiesAbove.isNotEmpty()) return null
        val crystalPos = above.vec3d.add(0.5, 0.0, 0.5)
        val targetDamage = explosionDamage(crystalPos, target, 6.0)
        if (targetDamage <= placeMinDamage) return null
        val selfDamage = explosionDamage(crystalPos, player, 6.0)
        if (selfDamage > placeMaxSelfDamage) return null
        return TargetPosition(pos.toImmutable(), targetDamage, selfDamage)
    }

    private val testRender = mutableListOf<BlockPos>()

    data class TargetPosition(
        val blockPos: BlockPos,
        val targetDamage: Double,
        val selfDamage: Double,
    )

    /**
     * Damage sorter parameter
     *
     * deadly -> always prioritize enemy damage no matter what
     * balanced -> sort by the highest ratio of enemy damage to self-damage
     * safe -> always prioritize the least amount of self-damage
     */
    private enum class DamageSort(val comparator: Comparator<TargetPosition>) {
        Deadly(compareByDescending<TargetPosition> { it.targetDamage }.thenBy { it.selfDamage }),
        Balanced(compareByDescending<TargetPosition> { it.targetDamage / it.selfDamage }.thenBy { it.selfDamage }),
        Safe(compareBy<TargetPosition> { it.selfDamage }.thenByDescending { it.targetDamage });
    }

    /**
     * Comparator for different render modes
     *
     * @param compared Lambda that takes in a living target and crystal position and then returns a color
     */
    private enum class ColorComparator(val compared: SafeContext.(LivingEntity, Vec3d) -> Color) {
        Distance({ target, dest ->
            val red = transform(target dist dest, 0.0, explodeRange, 255.0, 0.0).toInt()
            Color(red, 255-red, 0, crystalAlpha)
        }),
        Damage({ target, dest ->
            val damage = explosionDamage(dest, target, 6.0)
                .coerceIn(0.0, target.health.toDouble())

            val red = transform(damage, 0.0, target.health.toDouble(), 0.0, 255.0).toInt()
            Color(red, 255-red, 0, crystalAlpha)
        })
    }

    private enum class Page {
        Targeting,
        Placing,
        Exploding,
        Rotation,
        Interaction,
        Rendering
    }
}
