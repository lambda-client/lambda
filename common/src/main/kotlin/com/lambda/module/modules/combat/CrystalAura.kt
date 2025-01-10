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
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.renderer.esp.builders.ofBox
import com.lambda.graphics.renderer.esp.builders.ofShape
import com.lambda.interaction.RotationManager.rotate
import com.lambda.interaction.rotation.RotationContext
import com.lambda.interaction.visibilty.VisibilityChecker.lookAtBlock
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.combat.CombatUtils.explosionDamage
import com.lambda.util.math.MathUtils.ceilToInt
import com.lambda.util.math.VecUtils.dist
import com.lambda.util.math.VecUtils.vec3d
import com.lambda.util.math.transform
import com.lambda.util.world.fastEntitySearch
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.block.Blocks
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.decoration.EndCrystalEntity
import net.minecraft.item.Items
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
    private val page by setting("Page", Page.General)

    /* General */
    private val strategy by setting("Strategy", Strategy.ExplodeBeforePlace) { page == Page.General }

    /* Targeting */
    // ToDo: Targeting Range should be reach + crystal range (also based on min damage)
    private val targeting = Targeting.Combat(this, 10.0) { page == Page.Targeting }

    /* Placing */
    private val place by setting("Place Crystals", true) { page == Page.Placing }
    private val swap by setting("Swap", Hand.MAIN_HAND, description = "Automatically swap to place crystals") { page == Page.Placing && place }
    private val placeMethod by setting("Place Sort", DamageSort.Deadly) { page == Page.Placing && place }
    //private val multiPlace by setting("Multi Place", true, description = "Place crystals") { page == Page.Placing && place }
    private val minSeparation by setting("Minimum Crystal Separation", 1, 1..3, 1, "The minimum space between crystals", " blocks") { page == Page.Placing && place }
    private val placeDelay by setting("Place Delay", 0L, 0L..1000L, 10L, "Delay between crystal placements", "ms") { page == Page.Placing && place }
    private val placeMinHealth by setting("Place Min Health", 10.0, 0.0..36.0, 0.5, "Minimum health to place a crystal") { page == Page.Placing && place }
    private val placeMinDamage by setting("Place Min Damage", 6.0, 0.0..20.0, 0.5, "Minimum damage to place a crystal") { page == Page.Placing && place }
    private val placeMaxSelfDamage by setting("Place Max Self Damage", 8.0, 0.0..20.0, 0.5, "Maximum self damage to place a crystal") { page == Page.Placing && place }

    /* Exploding */
    private val explode by setting("Explode Crystals", true) { page == Page.Exploding }
    private val explodeDelay by setting("Explode Delay", 0, 0..20, 1, "Delay between crystal explosions", " ticks") { page == Page.Exploding && explode }
    private val explodeRange by setting("Explode Range", 5.0, 0.1..7.0, 0.1, "Range to explode crystals", " blocks") { page == Page.Exploding && explode }
    private val explodeMethod by setting("Explode Sort", DamageSort.Deadly) { page == Page.Exploding && explode }
    private val explodeMinDamage by setting("Explode Min Damage", 6.0, 0.0..20.0, 0.5, "Minimum damage to explode a crystal") { page == Page.Exploding && explode }

    /* Rotation */
    private val rotation = RotationSettings(this) { page == Page.Rotation }

    /* Interaction */
    private val interact = InteractionSettings(this) { page == Page.Interaction }

    /* Rendering */
    private val renderCrystals by setting("Render Crystal", true) { page == Page.Rendering }
    private val crystalColor by setting("Color Comparator", ColorMode.TargetDamage) { page == Page.Rendering && renderCrystals }
    private val crystalAlpha by setting("Color Alpha", 128, 0..255) { page == Page.Rendering && renderCrystals }

    private enum class Page {
        General,
        Targeting,
        Placing,
        Exploding,
        Rotation,
        Interaction,
        Rendering
    }

    private val placements = LimitedDecayQueue<BlockPos>(64, 1000L)
    private val placementTargets = mutableListOf<PlacementTarget>()
    private val target: LivingEntity? get() = targeting.target()

    private var currentRotation: RotationContext? = null

    init {
        listen<TickEvent.Pre> {
            currentRotation?.let { rotate ->
                if (!place) return@let
                if (!rotate.isValid) return@let
                val blockHit = rotate.hitResult?.blockResult ?: return@let
                val inMainHand = player.mainHandStack.item == Items.END_CRYSTAL
                val inOffHand = player.offHandStack.item == Items.END_CRYSTAL
                if (!inMainHand && !inOffHand) return@let
                val hand = if (inMainHand) Hand.MAIN_HAND else Hand.OFF_HAND
                interaction.interactBlock(player, hand, blockHit)
                placements.add(blockHit.blockPos)
                currentRotation = null
            }

            target?.let { tar ->
                val validPositions = findPlacementTargets(tar).filter { it.blockPos !in placements }
                placementTargets.addAll(validPositions)
                currentRotation = validPositions.firstNotNullOfOrNull {
                    lookAtBlock(it.blockPos.toImmutable(), rotation, interact)
                }
            }
        }

        rotate {
            request {
                if (!place) return@request null
                currentRotation
            }
        }

        listen<RenderEvent.StaticESP> {
            findExplosionTargets(target ?: return@listen).forEach { crystalExplosion ->
                val color = crystalExplosion.color
                it.renderer.ofBox(crystalExplosion.crystal.boundingBox, color, color)
            }

            placementTargets.forEach { target ->
                val color = target.color
                it.renderer.ofShape(target.blockPos, color, color)
            }

            placementTargets.clear()
        }
    }

    private fun SafeContext.findExplosionTargets(target: LivingEntity): List<ExplosionTarget> {
        val maximumRange = (1 - (explodeMinDamage / 12.0)) * 12.0
        return fastEntitySearch<EndCrystalEntity>(maximumRange, target.blockPos)
            .filter { it.pos.distanceTo(target.pos) <= maximumRange }
            .map { crystal ->
                val targetDamage = crystalDamage(crystal.pos, target)
                val selfDamage = crystalDamage(crystal.pos, player)
                val distance = target.dist(crystal.pos)
                ExplosionTarget(crystal, targetDamage, selfDamage, distance)
            }
    }

    private fun SafeContext.findPlacementTargets(target: LivingEntity): List<PlacementTarget> {
        // This formula is derived from the explosion damage scaling logic. The damage decreases linearly
        // with distance, modeled as `damage = (1 - (distance / (power * 2))) * exposure`, where power is the
        // explosion's strength (6.0) and exposure defines how much of the explosion affects the target.
        val maximumRange = ((1 - (placeMinDamage / 12.0)) * 12.0).ceilToInt()

        return BlockPos.iterateOutwards(target.blockPos, maximumRange, maximumRange, maximumRange).mapNotNull { pos ->
            targetData(pos, target)
        }.sortedWith(placeMethod.comparator)
    }

    private fun SafeContext.targetData(pos: BlockPos, target: LivingEntity): PlacementTarget? {
        val inRange = pos.dist(player.eyePos) < interact.reach + 1
        if (!inRange) return null
        val state = pos.blockState(world)
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
        val distance = target.dist(crystalPos)
        return PlacementTarget(pos.toImmutable(), targetDamage, selfDamage, distance)
    }

    /**
     * @property blockPos The block position associated with this placement target.
     * @property targetDamage The damage inflicted on the target.
     * @property selfDamage The damage inflicted on the self due to some actions or interactions.
     * @property distanceToTarget The distance to the target from the player or reference point.
     */
    data class PlacementTarget(
        val blockPos: BlockPos,
        override val targetDamage: Double,
        override val selfDamage: Double,
        override val distanceToTarget: Double,
    ) : Target()

    /**
     * @property crystal The targeted `EndCrystalEntity` in the context of the explosion.
     * @property targetDamage The amount of damage inflicted on the target as part of the explosion.
     * @property selfDamage The amount of damage inflicted on the self due to the explosion.
     * @property distanceToTarget The distance from the origin point (e.g., player) to the target.
     */
    data class ExplosionTarget(
        val crystal: EndCrystalEntity,
        override val targetDamage: Double,
        override val selfDamage: Double,
        override val distanceToTarget: Double
    ) : Target()

    /**
     * Represents a target in a system where damage, distance, and other attributes are evaluated.
     *
     * @property targetDamage The damage inflicted on the target.
     * @property selfDamage The damage inflicted on the self due to some actions or interactions.
     * @property distanceToTarget The distance to the target from the player or reference point.
     * @property color The dynamically calculated color based on the color mode and associated parameters.
     */
    abstract class Target {
        abstract val targetDamage: Double
        abstract val selfDamage: Double
        abstract val distanceToTarget: Double

        val color: Color by lazy {
            when (crystalColor) {
                ColorMode.TargetDamage -> {
                    val targetHealth = target?.health?.toDouble() ?: 0.0
                    val damage = targetDamage.coerceAtMost(targetHealth)
                    val red = transform(damage, 0.0, targetHealth, 0.0, 255.0).toInt()
                    Color(red, 255 - red, 0, crystalAlpha)
                }
                ColorMode.SelfDamage -> {
                    runSafe {
                        val selfHealth = player.health.toDouble()
                        val damage = selfDamage.coerceAtMost(selfHealth)
                        val red = transform(damage, 0.0, selfHealth, 0.0, 255.0).toInt()
                        Color(red, 255 - red, 0, crystalAlpha)
                    } ?: Color.WHITE
                }
                ColorMode.Distance -> {
                    val distance = distanceToTarget.coerceAtMost(explodeRange)
                    val red = transform(distance, 0.0, explodeRange, 0.0, 255.0).toInt()
                    Color(red, 0, 255 - red, crystalAlpha)
                }
            }
        }
    }

    /**
     * Damage sorter parameter
     *
     * deadly -> always prioritize enemy damage no matter what
     * balanced -> sort by the highest ratio of enemy damage to self-damage
     * safe -> always prioritize the least amount of self-damage
     */
    private enum class DamageSort(val comparator: Comparator<PlacementTarget>) {
        Deadly(compareByDescending<PlacementTarget> { it.targetDamage }.thenBy { it.selfDamage }),
        Balanced(compareByDescending<PlacementTarget> { it.targetDamage / it.selfDamage }.thenBy { it.selfDamage }),
        Safe(compareBy<PlacementTarget> { it.selfDamage }.thenByDescending { it.targetDamage });
    }

    private enum class Strategy {
        ExplodeBeforePlace,
        PlaceBeforeExplode,
    }

    /**
     * Represents the different modes of color categorization or operations.
     *
     * Enum values:
     * - TargetDamage: Indicates color mode based on damage to a target.
     * - SelfDamage: Indicates color mode based on damage to oneself.
     * - Distance: Indicates color mode based on distance criteria.
     */
    private enum class ColorMode {
        TargetDamage,
        SelfDamage,
        Distance
    }

    private fun SafeContext.crystalDamage(vec3d: Vec3d, target: LivingEntity) =
        explosionDamage(vec3d, target, 6.0)
}
