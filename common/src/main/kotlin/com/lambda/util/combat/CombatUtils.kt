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
import com.lambda.util.math.dist
import com.lambda.util.math.minus
import com.lambda.util.math.times
import com.lambda.util.world.SearchUtils.internalGetFastEntities
import com.lambda.util.world.fastEntitySearch
import com.lambda.util.world.toFastVec
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.ProtectionEnchantment
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.decoration.EndCrystalEntity
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.registry.tag.DamageTypeTags
import net.minecraft.util.math.Vec3d
import net.minecraft.world.explosion.Explosion
import kotlin.math.max
import kotlin.math.min

object CombatUtils {
    /**
     * Scales damage up or down based on the player resistances and other variables
     *
     * @param entity The entity to calculate the damage for
     * @param damage The damage to apply
     */
    fun DamageSource.scale(entity: LivingEntity, damage: Double): Double {
        if (entity.blockedByShield(this)) return 0.0
        val resistanceAmplifier = entity.getStatusEffect(StatusEffects.RESISTANCE)?.amplifier ?: -1

        if (isIn(DamageTypeTags.BYPASSES_EFFECTS)) return damage

        if (entity.hasStatusEffect(StatusEffects.RESISTANCE) && !isIn(DamageTypeTags.BYPASSES_RESISTANCE))
            return (damage - max(damage * (25 - (resistanceAmplifier + 1) * 5) / 25.0, 0.0)).coerceAtLeast(0.0)

        if (isIn(DamageTypeTags.BYPASSES_ENCHANTMENTS)) return damage

        val protectionAmount = EnchantmentHelper.getProtectionAmount(entity.armorItems, this)
        if (protectionAmount > 0) return damage * (1.0 - min(protectionAmount, 20) / 25.0)

        return damage
    }

    /**
     * Returns whether there is a deadly end crystal in proximity of the player
     *
     * @param minHealth The minimum health (in half hearts) at which an explosion is considered deadly
     */
    fun SafeContext.hasDeadlyCrystal(minHealth: Double) =
        fastEntitySearch<EndCrystalEntity>(12.0)
            .any { player.health - explosionDamage(it.pos, player, 6.0) <= minHealth}

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
        val distance = entity dist position

        val impact = (1.0 - distance / (power * 2.0)) * Explosion.getExposure(position, entity) * 0.4
        val damage = world.difficulty.id * 3 * power * (impact * impact + impact) + 1

        return Explosion.createDamageSource(world, null).scale(entity, damage)
    }

    /**
     * Calculates the velocity of entities in the explosion
     *
     * @param explosion The explosion to calculate the velocity for
     */
    @OptIn(InternalApi::class)
    fun SafeContext.explosionVelocity(explosion: Explosion): Map<LivingEntity, Vec3d> {
        val ref = ArrayList<LivingEntity>()
        internalGetFastEntities(explosion.position.toFastVec(), explosion.power * 2.0, ref)
        return ref.associateWith { entity -> explosionVelocity(entity, explosion) }
    }

    /**
     * Calculates the velocity of a living entity affected by an explosion
     *
     * @param entity The entity to calculate the velocity for
     * @param explosion The explosion to calculate the velocity for
     */
    fun explosionVelocity(entity: LivingEntity, explosion: Explosion) =
        explosionVelocity(entity, explosion.position, explosion.power.toDouble())

    /**
     * Calculates the velocity of a living entity affected by an explosion
     *
     * @param entity The entity to calculate the velocity for
     * @param position The position of the explosion
     * @param power The strength of the explosion
     */
    fun explosionVelocity(entity: LivingEntity, position: Vec3d, power: Double): Vec3d {
        val distance = entity.pos.distanceTo(position)

        val size = power * 2.0
        val vel = ProtectionEnchantment.transformExplosionKnockback(
            entity,
            (1.0 - distance / size) * Explosion.getExposure(position, entity)
        )

        val diff = entity.eyePos - position
        return diff.normalize() * vel
    }
}
