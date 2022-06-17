package com.lambda.client.module.modules.combat

import com.lambda.client.commons.interfaces.DisplayEnum
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.CombatManager
import com.lambda.client.manager.managers.HotbarManager
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.TpsCalculator
import com.lambda.client.util.combat.CombatUtils
import com.lambda.client.util.combat.CombatUtils.equipBestWeapon
import com.lambda.client.util.combat.CombatUtils.scaledHealth
import com.lambda.client.util.items.isWeapon
import com.lambda.client.util.math.RotationUtils.faceEntityClosest
import com.lambda.client.util.math.RotationUtils.getRotationToEntityClosest
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.Entity
import net.minecraft.entity.projectile.EntityLargeFireball
import net.minecraft.util.EnumHand
import net.minecraftforge.fml.common.gameevent.TickEvent

@CombatManager.CombatModule
object KillAura : Module(
    name = "KillAura",
    description = "Hits entities around you",
    category = Category.COMBAT,
    alias = arrayOf("KA", "Aura", "TriggerBot"),
    modulePriority = 50
) {
    private val mode by setting("Mode", Mode.COOLDOWN)
    private val rotationMode by setting("Rotation Mode", RotationMode.SPOOF)
    private val attackDelay by setting("Attack Delay", 5, 1..40, 1, { mode == Mode.TICKS }, unit = " ticks")
    private val disableOnDeath by setting("Disable On Death", false)
    private val tpsSync by setting("TPS Sync", false)
    private val weaponOnly by setting("Weapon Only", false)
    private val autoWeapon by setting("Auto Weapon", true)
    private val prefer by setting("Prefer", CombatUtils.PreferWeapon.SWORD, { autoWeapon })
    private val minSwapHealth by setting("Min Swap Health", 5.0f, 1.0f..20.0f, 0.5f)
    private val swapDelay by setting("Swap Delay", 10, 0..50, 1, unit = " ticks")
    private val fireballs by setting("Target Fireballs", false)
    private val fireballForceViewLock by setting("Force ViewLock", true, { fireballs }, description = "Only applies to fireballs")
    val range by setting("Range", 4.0f, 0.0f..6.0f, 0.1f)

    private val timer = TickTimer(TimeUnit.TICKS)
    private var inactiveTicks = 0

    private enum class Mode(override val displayName: String) : DisplayEnum {
        COOLDOWN("Cooldown"),
        TICKS("Ticks")
    }

    @Suppress("UNUSED")
    private enum class RotationMode(override val displayName: String) : DisplayEnum {
        OFF("Off"),
        SPOOF("Spoof"),
        VIEW_LOCK("View Lock")
    }

    override fun isActive(): Boolean {
        return isEnabled && inactiveTicks <= 5
    }

    override fun getHudInfo(): String {
        return mode.displayName
    }

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            inactiveTicks++

            if (!player.isEntityAlive) {
                if (disableOnDeath) disable()
                return@safeListener
            }
            val target = getFireball() ?: CombatManager.target ?: return@safeListener

            if (!CombatManager.isOnTopPriority(KillAura) || CombatSetting.pause) return@safeListener

            if (target !is EntityLargeFireball) {
                if (player.getDistance(target) >= range) return@safeListener
                if (player.scaledHealth > minSwapHealth && autoWeapon) equipBestWeapon(prefer)
                if (weaponOnly && !player.heldItemMainhand.item.isWeapon) return@safeListener
                if (swapDelay > 0 && System.currentTimeMillis() - HotbarManager.swapTime < swapDelay * 50L) return@safeListener
            }

            inactiveTicks = 0
            rotate(target)
            if (canAttack()) attack(target)
        }
    }

    private fun SafeClientEvent.getFireball(): EntityLargeFireball? {
        if (!fireballs) return null
        return world.loadedEntityList.firstOrNull { it is EntityLargeFireball && player.getDistance(it) < range } as EntityLargeFireball?
    }

    private fun SafeClientEvent.rotate(target: Entity) {
        if (fireballForceViewLock && target is EntityLargeFireball) {
            faceEntityClosest(target)
            return
        }

        when (rotationMode) {
            RotationMode.SPOOF -> {
                sendPlayerPacket {
                    rotate(getRotationToEntityClosest(target))
                }
            }
            RotationMode.VIEW_LOCK -> {
                faceEntityClosest(target)
            }
            else -> {
                // Rotation off
            }
        }
    }

    private fun SafeClientEvent.canAttack() =
        when (mode) {
            Mode.COOLDOWN -> {
                val adjustTicks = if (!tpsSync) 0.0f
                else TpsCalculator.adjustTicks
                player.getCooledAttackStrength(adjustTicks) > 0.9f
            }
            Mode.TICKS -> {
                timer.tick(attackDelay)
            }
        }

    private fun SafeClientEvent.attack(entity: Entity) {
        playerController.attackEntity(player, entity)
        player.swingArm(EnumHand.MAIN_HAND)
    }
}