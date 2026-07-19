
package com.minato.util.combat

import com.minato.context.SafeContext
import com.minato.util.combat.DamageUtils.scale
import com.minato.util.extension.fullHealth
import com.minato.util.math.dist
import com.minato.util.world.fastEntitySearch
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.decoration.EndCrystalEntity
import net.minecraft.util.math.Vec3d
import net.minecraft.world.explosion.Explosion
import net.minecraft.world.explosion.ExplosionImpl

object CombatUtils {
    /**
     * Returns whether there is a deadly end crystal in proximity of the player
     *
     * @param minHealth The minimum health (in half hearts) at which an explosion is considered deadly
     */
    fun SafeContext.hasDeadlyCrystal(minHealth: Double = 0.0) =
        fastEntitySearch<EndCrystalEntity>(12.0)
            .any { player.fullHealth - crystalDamage(it.pos, player) <= minHealth }

    /**
     * Calculates the damage dealt by an explosion to a living entity
     *
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

        val range = power * 2
        val impact = (1 - distance / range) * ExplosionImpl.calculateReceivedDamage(position, entity)
        val damage = (impact * impact + impact) / 2.0 * 7.0 * range + 1

        return Explosion.createDamageSource(world, null).scale(world, entity, damage)
    }
}
