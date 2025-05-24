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

package com.lambda.util.combat

import com.lambda.context.SafeContext
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.extension.fullHealth
import com.lambda.util.math.flooredBlockPos
import com.lambda.util.math.minus
import com.lambda.util.player.prediction.buildPlayerPrediction
import net.minecraft.block.BedBlock
import net.minecraft.block.CobwebBlock
import net.minecraft.block.HayBlock
import net.minecraft.block.HoneyBlock
import net.minecraft.block.PointedDripstoneBlock
import net.minecraft.block.PointedDripstoneBlock.THICKNESS
import net.minecraft.block.PointedDripstoneBlock.VERTICAL_DIRECTION
import net.minecraft.block.PowderSnowBlock
import net.minecraft.block.SlimeBlock
import net.minecraft.block.enums.Thickness
import net.minecraft.client.world.ClientWorld
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE
import net.minecraft.entity.effect.StatusEffects.JUMP_BOOST
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.registry.tag.DamageTypeTags.DAMAGES_HELMET
import net.minecraft.registry.tag.DamageTypeTags.IS_FIRE
import net.minecraft.registry.tag.DamageTypeTags.IS_FREEZING
import net.minecraft.registry.tag.EntityTypeTags.FALL_DAMAGE_IMMUNE
import net.minecraft.registry.tag.EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES
import net.minecraft.util.math.Direction
import net.minecraft.world.Difficulty
import net.minecraft.world.World
import kotlin.jvm.optionals.getOrDefault
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object DamageUtils {
    /**
     * Returns whether the player will fall from high enough for the impact to be deadly
     *
     * @param minHealth The minimum health (in half hearts) at which the fall is considered deadly
     */
    fun SafeContext.isFallDeadly(minHealth: Double = 0.0) =
        player.fullHealth - fallDamage() <= minHealth

    /**
     * Calculates the fall damage for the player at the predicted position
     */
    fun SafeContext.fallDamage(): Double {
        val prediction = buildPlayerPrediction()
            .skipUntil(60) { it.onGround }

        val predictedPos = prediction.position
        val fallDistance = player.y - predictedPos.y + player.fallDistance

        val state = blockState(predictedPos.flooredBlockPos)
        val block = state.block

        val distance = fallDistance +
                when (block) {
                    is BedBlock -> 0.5
                    is PointedDripstoneBlock -> if (state.get(VERTICAL_DIRECTION) == Direction.UP && state.get(THICKNESS) == Thickness.TIP) 2.0 else 0.0
                    else -> 0.0
                }

        val multiplier = when (block) {
            is HayBlock, is HoneyBlock -> 0.2
            is PointedDripstoneBlock -> if (state.get(VERTICAL_DIRECTION) == Direction.UP && state.get(THICKNESS) == Thickness.TIP) 2.0 else 1.0
            is PowderSnowBlock, is SlimeBlock, is CobwebBlock -> 0.0
            else -> 1.0
        }

        val source = player.damageSources.fall()
        return source.scale(world, player, player.fallDamage(distance, multiplier))
    }

    fun LivingEntity.fallDamage(distance: Double, multiplier: Double): Double {
        if (type.isIn(FALL_DAMAGE_IMMUNE)) return 0.0

        val modifier = getStatusEffect(JUMP_BOOST)?.amplifier?.plus(1.0) ?: 0.0
        return max(0.0, ceil((distance - 3.0 - modifier) * multiplier))
    }
    /**
     * Scales damage up or down based on the player resistances and other variables
     *
     * @param entity The entity to calculate the damage for
     * @param damage The damage to apply
     */
    fun DamageSource.scale(world: ClientWorld, entity: LivingEntity, damage: Double): Double {
        val blockingItem = entity.blockingItem
        val itemComponent = blockingItem?.get(DataComponentTypes.BLOCKS_ATTACKS)

        val source = source
        val position = position

        val blockingReduction = itemComponent
            ?.bypassedBy
            ?.filter { !isIn(it) }
            ?.map {
                if (source is PersistentProjectileEntity
                    && source.pierceLevel > 0) return@map 0.0

                val horizontalAngle = if (position != null) {
                    acos((entity.pos - position).horizontal.normalize()
                        .dotProduct(entity.getRotationVector(0f, entity.headYaw)))
                } else Math.PI

                return@map itemComponent.getDamageReductionAmount(this, damage.toFloat(), horizontalAngle).toDouble()
            }
            ?.getOrDefault(0.0)
            ?: 0.0

        val amount = damage - blockingReduction

        if (entity.isAlwaysInvulnerableTo(this) ||
            entity.isDead ||
            isIn(IS_FIRE) && entity.hasStatusEffect(FIRE_RESISTANCE)) return 0.0

        if (isIn(IS_FREEZING) && entity.type.isIn(FREEZE_HURTS_EXTRA_TYPES))
            return amount * 5.0

        if (isIn(DAMAGES_HELMET) && !entity.getEquippedStack(EquipmentSlot.HEAD).isEmpty)
            return amount * 0.75

        val appliedDamage = entity.applyArmorToDamage(this,
            entity.modifyAppliedDamage(this, amount.toFloat())).toDouble()

        return if (entity is PlayerEntity && isScaledWithDifficulty)
            world.scaleDamage(appliedDamage) else appliedDamage
    }

    /**
     * Scales the damage depending on the world difficulty
     *
     * Note: before using this function make sure to check if the damage scales with the given source
     */
    fun World.scaleDamage(damage: Double): Double =
        when (difficulty) {
            Difficulty.PEACEFUL -> 0.0
            Difficulty.EASY -> min(damage / 2 + 1, damage)
            Difficulty.HARD -> damage * 3 / 2
            else -> damage
        }
}
