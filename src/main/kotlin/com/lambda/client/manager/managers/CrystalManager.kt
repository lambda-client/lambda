package com.lambda.client.manager.managers

import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.WorldEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.Manager
import com.lambda.client.module.modules.combat.CrystalAura
import com.lambda.client.util.combat.CombatUtils
import com.lambda.client.util.combat.CombatUtils.getExplosionAffectedEntities
import com.lambda.client.util.combat.CrystalUtils.calcCrystalDamage
import com.lambda.client.util.combat.CrystalUtils.getBestPlace
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.threads.onMainThreadSafe
import com.lambda.client.util.threads.safeListener
import io.netty.util.internal.ConcurrentSet
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityEnderCrystal
import net.minecraft.util.math.Vec3d
import net.minecraftforge.event.world.ExplosionEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.concurrent.ConcurrentHashMap

object CrystalManager : Manager {
    val placedCrystals = ConcurrentSet<Crystal>()
    val toPlaceList = ConcurrentHashMap<EntityLivingBase, CrystalPlaceInfo>()

    init {
        safeListener<net.minecraftforge.event.world.WorldEvent.Load> {
            world.loadedEntityList.filterIsInstance<EntityEnderCrystal>().forEach { crystal ->
                CombatManager.target
            }
        }

        safeListener<WorldEvent.EntityCreate> { update ->
            if (update.entity !is EntityEnderCrystal) return@safeListener
            val crystal = Crystal(update.entity, CrystalPlaceInfo(update.entity.positionVector, calcCrystalDamage(update.entity, CombatManager.target)))
            placedCrystals.add(crystal)
        }

        safeListener<TickEvent.ClientTickEvent> {
            CombatManager.target?.let { entity ->
                toPlaceList[entity] = getBestPlace(entity, CrystalAura.placeDistance) ?: return@safeListener
                placedCrystals.forEach { it.info.damage = calcCrystalDamage(entity.positionVector, CombatManager.target) }
                toPlaceList.forEach { (entity, crystal) ->
                    if (
                        !world.loadedEntityList.contains(entity)
                        || entity.distanceTo(player.positionVector) > 10
                        || placedCrystals.any { placed -> placed.info == crystal }
                    ) toPlaceList.remove(entity)
                    else crystal.damage = calcCrystalDamage(crystal.position, entity)
                }
            }
        }

        safeListener<ExplosionEvent.Detonate> {
            // For some reason, even if one of the condition is true, it will NOT be removed from the list
            /*val affectedEntities = getExplosionAffectedEntities(EntityEnderCrystal::class.java, it.explosion.position, CombatUtils.ExplosionStrength.EndCrystal)
            placedCrystals.removeIf { crystal -> affectedEntities.contains(crystal.entity) || crystal.entity.isDead }*/
            placedCrystals.clear()
        }

        safeListener<ConnectionEvent.Disconnect> {
            placedCrystals.clear()
            toPlaceList.clear()
        }
    }

    data class Crystal(val entity: EntityEnderCrystal, var info: CrystalPlaceInfo)
    data class CrystalPlaceInfo(val position: Vec3d, var damage: CrystalDamage)
    data class CrystalDamage(val targetDamage: Float, val selfDamage: Float, val targetDistance: Double, val selfDistance: Double, val throughWalls: Boolean)
}