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

        val protectionAmount = EnchantmentHelper.getProtectionAmount(entity.armorItems, source)

        if (protectionAmount > 0) return damage * (1.0 - min(protectionAmount, 20) / 25.0)

        return damage
    }
}
