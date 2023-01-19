package com.lambda.client.manager.managers

import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.*
import com.lambda.client.manager.Manager
import com.lambda.client.module.AbstractModule
import com.lambda.client.module.Category
import com.lambda.client.module.ModuleManager
import com.lambda.client.module.modules.combat.CrystalAura
import com.lambda.client.util.MotionTracker
import com.lambda.client.util.combat.CombatUtils.scaledHealth
import com.lambda.client.util.combat.CrystalUtils.calcCrystalDamage
import com.lambda.client.util.combat.CrystalUtils.canExplodeCrystal
import com.lambda.client.util.combat.CrystalUtils.canPlaceCrystal
import com.lambda.client.util.combat.CrystalUtils.getBestPlace
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.threads.safeListener
import io.netty.util.internal.ConcurrentSet
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityEnderCrystal
import net.minecraft.init.SoundEvents
import net.minecraft.network.play.server.SPacketSoundEffect
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent


object CombatManager : Manager {
    private val combatModules: List<AbstractModule>


    var target: EntityLivingBase? = null
        set(value) {
            motionTracker.target = value
            field = value
        }
    val motionTracker = MotionTracker(null)
    val placedCrystals = ConcurrentSet<Crystal>()
    val toPlaceList = mutableMapOf<EntityLivingBase, CrystalPlaceInfo>()


    fun clear() {
        target = null
        placedCrystals.clear()
        toPlaceList.clear()
        motionTracker.reset()
    }

    fun isActiveAndTopPriority(module: AbstractModule) = module.isActive() && isOnTopPriority(module)

    fun isOnTopPriority(module: AbstractModule): Boolean {
        return getTopPriority() <= module.modulePriority
    }

    private fun getTopPriority(): Int {
        return getTopModule()?.modulePriority ?: -1
    }

    fun getTopModule(): AbstractModule? {
        var topModule: AbstractModule? = null
        for (module in combatModules) {
            if (!module.isActive()) continue
            if (module.modulePriority < (topModule?.modulePriority ?: 0)) continue
            topModule = module
        }
        return topModule
    }

    fun SafeClientEvent.updateCrystalDamage() {
        synchronized(placedCrystals) {
            for (placedCrystal in placedCrystals) {
                placedCrystal.damage = calcCrystalDamage(placedCrystal.entity.positionVector, target)
            }
        }
    }

    /** Use to mark a module that should be added to [combatModules] */
    annotation class CombatModule

    init {
        val cacheList = ArrayList<AbstractModule>()
        val annotationClass = CombatModule::class.java
        for (module in ModuleManager.modules) {
            if (module.category != Category.COMBAT) continue
            if (!module.javaClass.isAnnotationPresent(annotationClass)) continue
            cacheList.add(module)
        }
        combatModules = cacheList

        /* Reset on disconnect */
        safeListener<ConnectionEvent.Disconnect> {
            clear()
        }

        safeListener<TickEvent.ClientTickEvent> {
            target?.let { target ->
                getBestPlace(target)?.let {
                    toPlaceList[target] = it
                }
            }

            canPlaceCrystal {
                    it.targetDamage >= CrystalAura.placeMinDamage
                    && player.scaledHealth >= CrystalAura.noSuicide
                    && player.scaledHealth - it.selfDamage/2 >= CrystalAura.placeMaxSelfDamage
                    && it.targetDistance >= CrystalAura.placeMinDistance
                    && it.selfDistance >= CrystalAura.placeMinSelfDistance
                    && (
                    if (CrystalAura.placeThroughWalls && it.throughWalls && it.selfDistance <= CrystalAura.placeThroughWallsRange) true
                    else !it.throughWalls
                    )
            }?.let {
                LambdaEventBus.post(CrystalEvent.PlaceEvent(it))
            }

            canExplodeCrystal()?.let {
                LambdaEventBus.post(CrystalEvent.BreakEvent(it))
            }
        }

        safeListener<WorldEvent.EntityCreate> {
            if (it.entity is EntityEnderCrystal) {
                placedCrystals.add(Crystal(it.entity, calcCrystalDamage(it.entity)))
            }
        }

        safeListener<PacketEvent.Receive> {
            if (it.packet is SPacketSoundEffect) {
                val packet = it.packet
                if (packet.category != SoundCategory.BLOCKS || packet.sound !== SoundEvents.ENTITY_GENERIC_EXPLODE) return@safeListener
                val crystals = mc.world.getEntitiesWithinAABB(EntityEnderCrystal::class.java, AxisAlignedBB(BlockPos(packet.x, packet.y, packet.z))
                    .expand(12.0, 12.0, 12.0))
                if (crystals.isEmpty()) return@safeListener
                placedCrystals.removeIf { crystal ->
                    crystals.contains(crystal.entity)
                }
            }
        }
    }


    class Crystal(val entity: EntityEnderCrystal, var damage: CrystalDamage)

    class CrystalPlaceInfo(val position: Vec3d, val info: CrystalDamage)
    class CrystalDamage(val targetDamage: Float, val selfDamage: Float, val targetDistance: Double, val selfDistance: Double, val throughWalls: Boolean) {
        companion object {
            val NULL = CrystalDamage(0.0f, 0.0f, 0.0, 0.0, false)
        }
    }
}