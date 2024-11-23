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

package com.lambda.util.combat

import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.registry.tag.DamageTypeTags
import kotlin.math.max
import kotlin.math.min

object Damage {
    /**
     * @param entity The entity to calculate the damage for
     * @param damage The damage to apply
     * @return The damage dealt by the explosion
     */
    fun mask(entity: LivingEntity, damage: Double, source: DamageSource): Double {
        val resistanceAmplifier = entity.getStatusEffect(StatusEffects.RESISTANCE)?.amplifier ?: -1

        if (source.isIn(DamageTypeTags.BYPASSES_EFFECTS)) return damage

        if (entity.hasStatusEffect(StatusEffects.RESISTANCE) && !source.isIn(DamageTypeTags.BYPASSES_RESISTANCE))
            return (damage - max(damage * (25 - (resistanceAmplifier + 1) * 5) / 25.0, 0.0)).coerceAtLeast(0.0)

        if (source.isIn(DamageTypeTags.BYPASSES_ENCHANTMENTS)) return damage

        // TODO: Fix this
        //val protectionAmount = EnchantmentHelper.getProtectionAmount(entity.armorItems, source)

        //if (protectionAmount > 0) return damage * (1.0 - min(protectionAmount, 20) / 25.0)

        return damage
    }
}
