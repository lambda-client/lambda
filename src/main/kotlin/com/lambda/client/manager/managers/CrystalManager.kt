package com.lambda.client.manager.managers

import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.CrystalEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.WorldEvent
import com.lambda.client.manager.Manager
import com.lambda.client.module.modules.combat.CrystalAura
import com.lambda.client.module.modules.combat.CrystalAura.explode
import com.lambda.client.module.modules.combat.CrystalAura.place
import com.lambda.client.util.combat.CombatUtils.scaledHealth
import com.lambda.client.util.combat.CrystalUtils.calcCrystalDamage
import com.lambda.client.util.combat.CrystalUtils.canExplodeCrystal
import com.lambda.client.util.combat.CrystalUtils.canPlaceCrystal
import com.lambda.client.util.combat.CrystalUtils.getBestPlace
import com.lambda.client.util.threads.safeAsyncListener
import com.lambda.client.util.threads.safeListener
import io.netty.util.internal.ConcurrentSet
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityEnderCrystal
import net.minecraft.init.SoundEvents
import net.minecraft.network.play.server.SPacketDestroyEntities
import net.minecraft.network.play.server.SPacketSoundEffect
import net.minecraft.network.play.server.SPacketSpawnObject
import net.minecraft.server.management.PlayerInteractionManager
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

object CrystalManager : Manager {
    val placedCrystals = ConcurrentSet<Crystal>()
    val toPlaceList = mutableMapOf<EntityLivingBase, CrystalPlaceInfo>()

    fun SafeClientEvent.updateCrystals() {
        synchronized(placedCrystals) {
            for (placedCrystal in placedCrystals) {
                placedCrystal.info.damage = calcCrystalDamage(placedCrystal.entity.positionVector, CombatManager.target)
            }
        }
    }

    fun SafeClientEvent.updatePlaceList() {
        synchronized(toPlaceList) {
            for (placeInfo in toPlaceList.values) {
                placeInfo.damage = calcCrystalDamage(placeInfo.position, CombatManager.target)
            }
        }
    }

    init {
        safeListener<TickEvent.ClientTickEvent>(999999999) {
            CombatManager.target?.let { target ->
                val placeInfo = getBestPlace(target) ?: return@safeListener
                toPlaceList[target] = placeInfo
                canPlaceCrystal(placeInfo) {
                        it.targetDamage >= CrystalAura.placeMinDamage
                        && it.selfDamage <= CrystalAura.placeMaxSelfDamage
                        && player.scaledHealth >= CrystalAura.noSuicide
                        && it.targetDistance >= CrystalAura.placeMinDistance
                        && it.selfDistance >= CrystalAura.placeMinSelfDistance
                        && (
                        if (CrystalAura.placeThroughWalls && it.throughWalls && it.selfDistance <= CrystalAura.placeThroughWallsRange) true
                        else !it.throughWalls
                        )
                }?.let {
                    LambdaEventBus.post(CrystalEvent.PlaceEvent(it))
                }
            }
        }

        safeListener<WorldEvent.EntityUpdate> { update ->
            CombatManager.target?.let { target ->
                if (update.entity !is EntityEnderCrystal) return@safeListener
                val crystal = Crystal(update.entity, CrystalPlaceInfo(update.entity.positionVector, calcCrystalDamage(update.entity, target)))
                placedCrystals.add(crystal)
                canExplodeCrystal(crystal) {
                    it.targetDamage >= CrystalAura.explodeMinDamage
                        && it.selfDamage <= CrystalAura.explodeMaxSelfDamage
                        && player.scaledHealth >= CrystalAura.noSuicide
                        && it.targetDistance >= CrystalAura.explodeMinDistance
                        && it.selfDistance >= CrystalAura.explodeMinSelfDistance
                }?.let { info ->
                    LambdaEventBus.post(CrystalEvent.BreakEvent(info))
                }
            }
        }

        safeListener<PacketEvent.Receive>(999999999) { receive ->
            if (receive.packet is SPacketDestroyEntities) {
                synchronized(placedCrystals) {
                    for (id in receive.packet.entityIDs) {
                        placedCrystals.removeIf { it.entity.entityId == id }
                    }
                }
            }
        }

        safeListener<ConnectionEvent.Disconnect> {
            placedCrystals.clear()
            toPlaceList.clear()
        }
    }

    class Crystal(val entity: EntityEnderCrystal, var info: CrystalPlaceInfo)

    class CrystalPlaceInfo(val position: Vec3d, var damage: CrystalDamage)

    class CrystalDamage(val targetDamage: Float, val selfDamage: Float, val targetDistance: Double, val selfDistance: Double, val throughWalls: Boolean)
}