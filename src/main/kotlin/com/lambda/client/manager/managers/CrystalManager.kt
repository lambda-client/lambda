package com.lambda.client.manager.managers

import com.google.common.collect.ConcurrentHashMultiset
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.WorldEvent
import com.lambda.client.manager.Manager
import com.lambda.client.module.modules.combat.CrystalAura
import com.lambda.client.util.combat.CrystalUtils.calcCrystalDamage
import com.lambda.client.util.combat.CrystalUtils.getBestPlace
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityEnderCrystal
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.concurrent.ConcurrentHashMap

object CrystalManager : Manager {
    val placedCrystals: ConcurrentHashMultiset<Crystal> = ConcurrentHashMultiset.create()
    val toPlaceList = ConcurrentHashMap<EntityLivingBase, CrystalPlaceInfo>()

    init {
        safeListener<WorldEvent.EntityCreate> { update ->
            if (update.entity !is EntityEnderCrystal || update.entity.distanceTo(player.positionVector) > 12) return@safeListener
            CombatManager.target?.let { target ->
                placedCrystals.add(Crystal(update.entity, CrystalPlaceInfo(update.entity.positionVector, calcCrystalDamage(update.entity, target))))
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            CombatManager.target?.let { entity ->
                toPlaceList[entity] = getBestPlace(entity, CrystalAura.placeDistance) ?: return@safeListener
                placedCrystals.forEach { crystal ->
                    crystal.info.damage = calcCrystalDamage(crystal.entity, entity)
                }
                toPlaceList.forEach { (entity, crystal) ->
                    crystal.damage = calcCrystalDamage(crystal.position, entity)
                }
            } ?: toPlaceList.clear()

            placedCrystals.removeAll { it.entity.isDead }
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