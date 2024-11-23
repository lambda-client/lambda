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

@file:OptIn(InternalApi::class)

package com.lambda.util.combat

import com.lambda.context.SafeContext
import com.lambda.core.annotations.InternalApi
import com.lambda.util.math.VecUtils.dist
import com.lambda.util.math.VecUtils.minus
import com.lambda.util.math.VecUtils.times
import com.lambda.util.math.VecUtils.vec3d
import com.lambda.util.world.WorldUtils.internalGetFastEntities
import com.lambda.util.world.toFastVec
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import net.minecraft.world.explosion.Explosion
import net.minecraft.world.explosion.ExplosionImpl

object Explosion {
    /**
     * Calculates the damage dealt by an explosion to a living entity.
     * @param source The source of the explosion.
     * @param entity The entity to calculate the damage for.
     * @return The damage dealt by the explosion.
     */
    fun SafeContext.explosionDamage(source: Explosion, entity: LivingEntity) =
        explosionDamage(source.position, entity, source.power.toDouble())

    /**
     * Calculates the damage dealt by an explosion to a living entity.
     * @param position The position of the explosion.
     * @param entity The entity to calculate the damage for.
     * @param power The strength of the explosion above 0.
     * @return The damage dealt by the explosion.
     */
    fun SafeContext.explosionDamage(position: Vec3i, entity: LivingEntity, power: Double): Double =
        explosionDamage(position.vec3d, entity, power)

    /**
     * Calculates the damage dealt by an explosion to a living entity.
     * @param position The position of the explosion.
     * @param entity The entity to calculate the damage for.
     * @param power The strength of the explosion above 0.
     * @return The damage dealt by the explosion.
     */
    fun SafeContext.explosionDamage(position: Vec3d, entity: LivingEntity, power: Double): Double {
        val distance = entity dist position

        val impact = (1.0 - distance / (power * 2.0)) *
                ExplosionImpl.calculateReceivedDamage(position, entity) *
                0.4

        val damage = world.difficulty.id * 3 *
                power *
                (impact * impact + impact) + 1

        return Damage.mask(entity, damage, Explosion.createDamageSource(world, null))
    }

    /**
     * Calculates the velocity of entities in the explosion.
     * @param explosion The explosion to calculate the velocity for.
     * @return The velocity of the entities.
     */
    fun SafeContext.explosionVelocity(explosion: Explosion): Map<LivingEntity, Vec3d> {
        val ref = ArrayList<LivingEntity>()
        internalGetFastEntities(explosion.position.toFastVec(), explosion.power * 2.0, ref)
        return ref.associateWith { entity -> explosionVelocity(entity, explosion) }
    }

    /**
     * Calculates the velocity of a living entity affected by an explosion.
     * @param entity The entity to calculate the velocity for.
     * @param explosion The explosion to calculate the velocity for.
     * @return The velocity of the entity.
     */
    fun SafeContext.explosionVelocity(entity: LivingEntity, explosion: Explosion) =
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

        val size = power * 2.0
        val vel = ((1.0 - distance / size) *
                ExplosionImpl.calculateReceivedDamage(position, entity)) *
                entity.getAttributeValue(EntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE)

        val diff = entity.eyePos - position
        return diff.normalize() * vel
    }
}
