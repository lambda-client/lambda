package com.lambda.util.combat

import com.lambda.context.SafeContext
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.fluidState
import com.lambda.util.world.WorldUtils.getFastEntities
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.explosion.Explosion
import kotlin.math.max

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
    fun SafeContext.explosionDamage(position: Vec3d, entity: LivingEntity, power: Double): Double {
        val distance = entity.pos.distanceTo(position)

        val impact = (1.0 - distance / (power * 2.0)) *
                Explosion.getExposure(position, entity) *
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
        getFastEntities(explosion.position, explosion.power * 2.0, ref)
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
        /*val vel = ProtectionEnchantment.transformExplosionKnockback(
            entity,
            (1.0 - distance / size) * Explosion.getExposure(position, entity)
        )

        val diff = entity.eyePos - position
        return diff.normalize() * vel*/
        return Vec3d.ZERO
    }

    fun SafeContext.explosionDestruction(source: Explosion): List<Vec3d> {
        val affected = mutableListOf<Vec3d>()

        repeat(16) { x ->
            repeat(16) { y ->
                repeat(16) { z ->
                    if (x == 0 || x == 15 || y == 0 || y == 15 || z == 0 || z == 15) {
                        val vec = Vec3d(x / 30.0 - 1.0, y / 30.0 - 1.0, z / 30.0 - 1.0)
                        val len = vec.length()

                        val dx = vec.x / len
                        val dy = vec.y / len
                        val dz = vec.z / len

                        var explosionX = source.position.x
                        var explosionY = source.position.y
                        var explosionZ = source.position.z

                        var intensity = source.power * (0.7 + world.random.nextDouble() * 0.6)

                        while (intensity > 0) {
                            val blockPos = BlockPos.ofFloored(explosionX, explosionY, explosionZ)
                            val block = blockPos.blockState(world)
                            val fluid = blockPos.fluidState(world)
                            if (!world.isInBuildLimit(blockPos)) {
                                break
                            }

                            val resistance = max(block.block.blastResistance, fluid.blastResistance)
                            intensity -= (resistance + 0.3) * 0.3

                            if (intensity > 0 && source.behavior.canDestroyBlock(source, world, blockPos, block, intensity.toFloat())) {
                                affected.add(Vec3d(explosionX, explosionY, explosionZ))
                            }

                            explosionX += dx * 0.3
                            explosionY += dy * 0.3
                            explosionZ += dz * 0.3

                            intensity -= 0.225
                        }
                    }
                }
            }
        }

        return affected
    }
}
