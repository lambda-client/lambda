package com.lambda.util.combat

import com.lambda.context.SafeContext
import com.lambda.util.combat.DamageUtils.applyProtection
import com.lambda.util.math.VecUtils.minus
import com.lambda.util.math.VecUtils.times
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.enchantment.ProtectionEnchantment
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.registry.tag.DamageTypeTags
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.stat.Stats
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.MathHelper.clamp
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World.ExplosionSourceType
import net.minecraft.world.explosion.Explosion
import kotlin.math.max

/**
 * Utility functions related to explosion effects and calculations.
 */
object CrystalUtils {
    /**
     * Calculates the radius of an explosion based on its power.
     * @param power The strength of the explosion.
     * @return The radius of the explosion.
     */
    fun SafeContext.explosionRadius(power: Double): Double =
        1.3 * (power / 0.225) * 0.3

    /**
     * Calculates the damage dealt by an explosion to a living entity.
     * @param source The source of the explosion.
     * @param entity The entity to calculate the damage for.
     * @return The damage dealt by the explosion.
     */
    fun SafeContext.explosionDamage(source: Explosion, entity: LivingEntity): Double =
        explosionDamage(source.position, entity, source.power.toDouble())

    /**
     * Calculates the damage dealt by an explosion to a living entity.
     * @param position The position of the explosion.
     * @param entity The entity to calculate the damage for.
     * @param power The strength of the explosion.
     * @return The damage dealt by the explosion.
     */
    fun SafeContext.explosionDamage(position: Vec3d, entity: LivingEntity, power: Double): Double {
        val distance = entity.pos.distanceTo(position)
        if (explosionRadius(power) < distance) return 0.0 // Avoid unnecessary calculations
        val size = power * 2.0
        val impact = (1.0 - distance / size) * Explosion.getExposure(position, entity)
        val damage = (impact * impact + impact) / 2.0 * 7.0 * size + 1
        return applyProtection(entity, damage, Explosion.createDamageSource(world, null))
    }

    /**
     * Calculates the velocity of a living entity affected by an explosion.
     * @param entity The entity to calculate the velocity for.
     * @param explosion The explosion to calculate the velocity for.
     * @return The velocity of the entity.
     */
    fun SafeContext.explosionVelocity(entity: LivingEntity, explosion: Explosion): Vec3d =
        explosionVelocity(entity, explosion.position, explosion.power.toDouble())

    /**
     * Calculates the velocity of a living entity affected by an explosion.
     * @param entity The entity to calculate the velocity for.
     * @param position The position of the explosion.
     * @param power The strength of the explosion.
     * @return The velocity of the entity.
     */
    fun SafeContext.explosionVelocity(entity: LivingEntity, position: Vec3d, power: Double): Vec3d {
        val distance = entity.pos.distanceTo(position)
        if (explosionRadius(power) < distance) return Vec3d.ZERO // Avoid unnecessary calculations
        val size = power * 2.0
        val vel = ProtectionEnchantment.transformExplosionKnockback(
            entity,
            (1.0 - distance / size) * Explosion.getExposure(position, entity)
        )

        val diff = entity.eyePos - position
        return diff.normalize() * vel
    }
}
