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
import com.lambda.core.annotations.InternalApi
import com.lambda.util.math.distSq
import com.lambda.util.math.minus
import com.lambda.util.math.times
import com.lambda.util.world.WorldUtils.internalGetFastEntities
import com.lambda.util.world.fastEntitySearch
import com.lambda.util.world.toFastVec
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.decoration.EndCrystalEntity
import net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE
import net.minecraft.registry.tag.DamageTypeTags.DAMAGES_HELMET
import net.minecraft.registry.tag.DamageTypeTags.IS_FIRE
import net.minecraft.registry.tag.DamageTypeTags.IS_FREEZING
import net.minecraft.registry.tag.EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES
import net.minecraft.util.math.Vec3d
import net.minecraft.world.explosion.Explosion
import net.minecraft.world.explosion.ExplosionImpl

object CombatUtils {
    /**
     * Scales damage up or down based on the player resistances and other variables
     *
     * @param entity The entity to calculate the damage for
     * @param damage The damage to apply
     */
    fun DamageSource.scale(entity: LivingEntity, damage: Double): Double {
        if (damage.isNaN() || damage.isInfinite())
            return Double.MAX_VALUE

        if (entity.isAlwaysInvulnerableTo(this) ||
            entity.isDead ||
            entity.blockedByShield(this) ||
            isIn(IS_FIRE) && entity.hasStatusEffect(FIRE_RESISTANCE)) return 0.0

        if (isIn(IS_FREEZING) && entity.type.isIn(FREEZE_HURTS_EXTRA_TYPES))
            return damage * 5

        if (isIn(DAMAGES_HELMET) && !entity.getEquippedStack(EquipmentSlot.HEAD).isEmpty)
            return damage * 0.75

        return entity.applyArmorToDamage(this,
            entity.modifyAppliedDamage(this, damage.toFloat())).toDouble()
    }

    /**
     * Returns whether there is a deadly end crystal in proximity of the player
     *
     * @param minHealth The minimum health (in half hearts) at which an explosion is considered deadly
     */
    fun SafeContext.hasDeadlyCrystal(minHealth: Double) =
        fastEntitySearch<EndCrystalEntity>(12.0)
            .any { player.health - crystalDamage(it.pos, player) <= minHealth }

    /**
     * Calculates the damage dealt by an explosion to a living entity
     * @param position The position of the explosion
     * @param entity The entity to calculate the damage for
     */
    fun SafeContext.crystalDamage(position: Vec3d, entity: LivingEntity) =
        explosionDamage(position, entity, 6.0)

    /**
     * Calculates the damage dealt by an explosion to a living entity
     *
     * @param source The source of the explosion
     * @param entity The entity to calculate the damage for
     */
    fun SafeContext.explosionDamage(source: Explosion, entity: LivingEntity) =
        explosionDamage(source.position, entity, source.power.toDouble())

    /**
     * Calculates the damage dealt by an explosion to a living entity.
     *
     * @param position The position of the explosion
     * @param entity The entity to calculate the damage for
     * @param power The [power of the explosion](https://minecraft.wiki/w/Explosion#Damage)
     */
    fun SafeContext.explosionDamage(position: Vec3d, entity: LivingEntity, power: Double): Double {
        val distance = entity distSq position

        val range = power * 2
        val impact = (1 - distance / range) * ExplosionImpl.calculateReceivedDamage(position, entity) * 0.4
        val damage = (impact * impact + impact) / 2.0 * 7.0 * range + 1

        return Explosion.createDamageSource(world, null).scale(entity, damage)
    }
}
