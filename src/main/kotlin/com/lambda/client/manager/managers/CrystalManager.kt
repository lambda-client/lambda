package com.lambda.client.manager.managers

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.manager.Manager
import com.lambda.client.util.combat.CrystalUtils.getCrystals
import com.lambda.client.util.combat.CrystalUtils.getPlaces
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityEnderCrystal
import net.minecraft.util.math.Vec3d

object CrystalManager : Manager {
    var placedCrystals = mutableListOf<Crystal>(); private set
    var toPlaceList = mutableMapOf<EntityLivingBase, List<CrystalPlaceInfo>>(); private set

    fun SafeClientEvent.updatePlaceList(range: Float, repartitionDistance: Float, rayTrace: Boolean, ticks: Int) {
        CombatManager.target?.let { entity ->
            placedCrystals = getCrystals(player, entity, range).toMutableList()
            toPlaceList = getPlaces(CombatManager.motionTracker.getFutureEntity(ticks)!!, range).toMutableMap()
        }
    }

    init {
        safeListener<ConnectionEvent.Disconnect> {
            placedCrystals.clear()
            toPlaceList.clear()
        }
    }

    data class Crystal(val entity: EntityEnderCrystal, var info: CrystalPlaceInfo)
    data class CrystalPlaceInfo(val position: Vec3d, var damage: CrystalDamage)
    data class CrystalDamage(val targetDamage: Float, val selfDamage: Float, val targetDistance: Double, val selfDistance: Double, val throughWalls: Boolean)
}