package com.lambda.client.module.modules.combat

import com.lambda.client.event.Phase
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PlayerEvent
import com.lambda.client.manager.managers.CombatManager
import com.lambda.client.manager.managers.CrystalManager
import com.lambda.client.manager.managers.HotbarManager
import com.lambda.client.manager.managers.HotbarManager.resetHotbar
import com.lambda.client.manager.managers.HotbarManager.spoofHotbar
import com.lambda.client.manager.managers.PlayerPacketManager
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.combat.CombatUtils.equipBestWeapon
import com.lambda.client.util.combat.CombatUtils.scaledHealth
import com.lambda.client.util.items.*
import com.lambda.client.util.math.RotationUtils
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.math.VectorUtils.toBlockPos
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getClosestVisibleSide
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.entity.EntityLivingBase
import net.minecraft.init.Items
import net.minecraft.init.MobEffects
import net.minecraft.item.ItemSword
import net.minecraft.item.ItemTool
import net.minecraft.network.Packet
import net.minecraft.network.play.client.CPacketAnimation
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.network.play.client.CPacketUseEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.abs

@CombatManager.CombatModule
object CrystalAura : Module(
    name = "CrystalAura",
    description = "Places End Crystals to kill enemies",
    category = Category.COMBAT,
    alias = arrayOf("CA", "AC", "AutoCrystal"),
    modulePriority = 80
) {

    private val page by setting("Page", Page.GENERAL)

    /* General */
    val noSuicide by setting("No Suicide", 6.0f, 0.0f..20.0f, 0.1f, { page == Page.GENERAL }, description = "Minimum health threshold to stop")
    val maxYawSpeed by setting("Max YawSpeed", 80, 10..100, 5, { page == Page.GENERAL }, description = "The maximum rotation allowed")
    val swingMode by setting("Swing Mode", SwingMode.CLIENT, { page == Page.GENERAL }, description = "Swing mode")

    /* Place page 1 */
    val doPlace by setting("Place", true, { page == Page.PLACE_ONE }, description = "Whether or not it should place")
    val placeRotate by setting("Place Rotate", false, { page == Page.PLACE_ONE }, description = "Whether or not the player should rotate to place")
    val autoSwap by setting("Auto Swap", true, { page == Page.PLACE_ONE }, description = "Automatically swap to crystals")
    val spoofHotbar by setting("Spoof Hotbar", false, { page == Page.PLACE_ONE }, description = "Spoofs your hotbar server side")
    val placeDistance by setting("Place Distance", 1.5f, 0.5f..6.0f, 0.1f, { page == Page.PLACE_ONE }, description = "The distance at which it should place")
    val placeThroughWalls by setting("Place Through Walls", true, { page == Page.PLACE_ONE }, description = "Whether or not it should place through walls")
    val placeThroughWallsRange by setting("Place Through Walls Range", 4.0f, 0.0f..6.0f, 0.1f, { placeThroughWalls && page == Page.PLACE_ONE }, description = "The range at which it should place through walls")

    /* Place page 2 */
    val placeMinDamage by setting("Place Min Damage", 4.25f, 0.0f..20f, 0.1f, { page == Page.PLACE_TWO }, description = "Minimum damage to place")
    val placeMaxSelfDamage by setting("Place Max Self Damage", 4.25f, 0.0f..20f, 0.1f, { page == Page.PLACE_TWO }, description = "Maximum self damage to place")
    val placeDelay by setting("Place Delay", 1, 0..10, 1, { page == Page.PLACE_TWO }, description = "Delay between each place in ticks")
    val placeDistanceBetween by setting("Place Distance Between Crystals", 2.0f, 0.0f..6.0f, 1.0f, { page == Page.PLACE_TWO })

    /* Explode page 1 */
    val doExplode by setting("Explode", true, { page == Page.EXPLODE_ONE }, description = "Whether or not it should explode")
    val explodeRotate by setting("Explode Rotate", true, { page == Page.EXPLODE_ONE }, description = "Whether or not the player should rotate to explode")
    val antiWeakness by setting("Anti Weakness", true, { page == Page.EXPLODE_ONE }, description = "Uses a tool for exploding")

    /* Explode page 2 */
    val explodeMinDamage by setting("Explode Min Damage", 4.25f, 0.0f..20f, 0.1f, { page == Page.EXPLODE_TWO }, description = "Minimum damage to explode")
    val explodeMaxSelfDamage by setting("Explode Max Self Damage", 4.25f, 0.0f..20f, 0.1f, { page == Page.EXPLODE_TWO }, description = "Maximum self damage to explode")
    val explodeRange by setting("Explode Range", 5.0f, 1.0f..6.0f, 0.1f, { page == Page.EXPLODE_TWO }, description = "The range at which it should explode")
    val swapDelay by setting("Swap Delay", 10, 0..50, 1, { page == Page.EXPLODE_TWO }, description = "The delay before swapping")
    val explodeDelay by setting("Explode Delay", 1, 0..10, 1, { page == Page.EXPLODE_TWO }, description = "The delay before exploding")
    val explodeThroughWalls by setting("Place Through Walls", true, { page == Page.PLACE_TWO }, description = "Whether or not it should explode through walls")
    val explodeThroughWallsRange by setting("Place Through Walls Range", 4.0f, 0.0f..6.0f, 0.1f, { placeThroughWalls && page == Page.PLACE_TWO }, description = "The range at which it should explode through walls")

    var lastLookAt: Vec3d? = null; private set
    val yawDiffList = FloatArray(20)
    var placeTimer = 0; private set
    var hitTimer = 0; private set
    var inactiveTicks = 20; private set
    var yawDiffIndex = 0; private set


    init {
        onDisable {
            lastLookAt = null
            placeTimer = 0
            hitTimer = 0
            inactiveTicks = 10
            resetHotbar()
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase == TickEvent.Phase.START) {
                inactiveTicks++
                placeTimer++
                hitTimer++
                updateYawSpeed()
            }

            CombatManager.target?.let { entity ->
                if (doPlace && placeTimer >= placeDelay) getPlacingCrystal(entity)?.let { crystal -> place(crystal) }
                if (doExplode && hitTimer >= explodeDelay) getExplodingCrystal()?.let { crystal -> explode(crystal) }
            }

            if (it.phase == TickEvent.Phase.END) {
                if (getHand() == EnumHand.OFF_HAND) {
                    resetHotbar()
                }
                if (inactiveTicks > 20) {
                    resetHotbar()
                    resetRotation()
                }
            }
        }

        safeListener<PlayerEvent.UpdateWalking> {
            if (!CombatManager.isOnTopPriority(CrystalAura) || CombatSetting.pause || it.phase != Phase.PRE) return@safeListener

            if (inactiveTicks <= 20 && lastLookAt != null) {
                sendPlayerPacket {
                    rotate(getLastRotation())
                }
            }
        }
    }

    private fun SafeClientEvent.getPlacingCrystal(entity: EntityLivingBase): CrystalManager.CrystalPlaceInfo? {
        return CrystalManager.toPlaceList[entity]?.let { crystal ->
            return if (player.allSlots.countItem(Items.END_CRYSTAL) >= 0
                && crystal.damage.targetDamage >= placeMinDamage
                && crystal.damage.selfDamage <= placeMaxSelfDamage
                && player.scaledHealth >= noSuicide
                && checkYawSpeed(getRotationTo(crystal.position).x)
                && CrystalManager.placedCrystals.all { placed -> placed.entity.distanceTo(crystal.position) >= placeDistanceBetween }
                && if (placeThroughWalls && crystal.damage.throughWalls && crystal.damage.selfDistance <= placeThroughWallsRange) true
                else !crystal.damage.throughWalls
            ) crystal
            else null
        }
    }

    private fun SafeClientEvent.getExplodingCrystal(): CrystalManager.Crystal? {
        return CrystalManager.placedCrystals
            .maxByOrNull { it.info.damage.targetDamage - it.info.damage.selfDamage }
            ?.let { crystal ->
                return if (crystal.entity.distanceTo(player.positionVector) <= explodeRange
                    && crystal.info.damage.targetDamage >= explodeMinDamage
                    && crystal.info.damage.selfDamage <= explodeMaxSelfDamage
                    && player.scaledHealth >= noSuicide
                    && checkYawSpeed(getRotationTo(crystal.info.position).x)
                    && if (explodeThroughWalls && crystal.info.damage.throughWalls && crystal.info.damage.selfDistance <= explodeThroughWallsRange) true
                    else !crystal.info.damage.throughWalls
                ) crystal
                else null
            }
    }


    /* Placing */
    fun SafeClientEvent.place(destination: CrystalManager.CrystalPlaceInfo) {
        if (!hasCrystalInHand()) swapToCrystal()
        placeCrystal(destination.position, getHand())
    }

    private fun SafeClientEvent.placeCrystal(position: Vec3d, hand: EnumHand) {
        inactiveTicks = 0
        if (placeRotate) lastLookAt = position
        placeTimer = 0
        sendPacketDirect(getPlacePacket(position, hand))
        sendPacketDirect(CPacketAnimation(hand))
    }

    private fun SafeClientEvent.getPlacePacket(pos: Vec3d, hand: EnumHand): CPacketPlayerTryUseItemOnBlock {
        val side = getClosestVisibleSide(pos) ?: EnumFacing.UP
        return CPacketPlayerTryUseItemOnBlock(pos.toBlockPos(), side, hand, 0.5f, 0.5f, 0.5f)
    }

    /* End of placing */


    /* Exploding */

    private fun SafeClientEvent.explode(crystal: CrystalManager.Crystal) {
        if (!preExplode()) return

        CombatManager.target?.let { target -> player.setLastAttackedEntity(target) }

        explodeDirect(crystal, CPacketUseEntity(crystal.entity))
    }


    private fun SafeClientEvent.preExplode(): Boolean {
        if (antiWeakness && player.isPotionActive(MobEffects.WEAKNESS) && !isHoldingTool()) {
            equipBestWeapon(allowTool = true)
            resetHotbar()
            return false
        }

        if (System.currentTimeMillis() - HotbarManager.swapTime < swapDelay * 50L) {
            return false
        }

        return true
    }

    private fun SafeClientEvent.explodeDirect(crystal: CrystalManager.Crystal, packet: CPacketUseEntity) {
        inactiveTicks = 0
        if (explodeRotate) lastLookAt = crystal.entity.positionVector
        hitTimer = 0
        sendPacketDirect(packet)
        sendPacketDirect(CPacketAnimation(getHand()))
    }

    /* End of exploding */


    /* Rotation */

    private fun updateYawSpeed() {
        val yawDiff = abs(RotationUtils.normalizeAngle(PlayerPacketManager.prevServerSideRotation.x - PlayerPacketManager.serverSideRotation.x))
        yawDiffList[yawDiffIndex] = yawDiff
        yawDiffIndex = (yawDiffIndex + 1) % 20
    }

    private fun checkYawSpeed(yaw: Float): Boolean {
        val yawDiff = abs(RotationUtils.normalizeAngle(yaw - PlayerPacketManager.serverSideRotation.x))
        return yawDiffList.sum() + yawDiff <= maxYawSpeed
    }

    private fun SafeClientEvent.getLastRotation() =
        lastLookAt?.let {
            getRotationTo(it)
        } ?: Vec2f.ZERO

    private fun resetRotation() {
        lastLookAt = null
    }

    private fun SafeClientEvent.getHand(): EnumHand {
        return if (player.heldItemOffhand.item == Items.END_CRYSTAL) EnumHand.OFF_HAND
        else EnumHand.MAIN_HAND
    }

    private fun EntityPlayerSP.getCrystalSlot() =
        this.hotbarSlots.firstItem(Items.END_CRYSTAL)

    private fun SafeClientEvent.isHoldingTool(): Boolean {
        val item = player.heldItemMainhand.item
        return item is ItemTool || item is ItemSword
    }

    private fun SafeClientEvent.hasCrystalInHand(): Boolean {
        return player.heldItemMainhand.item == Items.END_CRYSTAL || player.heldItemOffhand.item == Items.END_CRYSTAL
    }

    private fun SafeClientEvent.swapToCrystal() {
        if (autoSwap) {
            val slot = player.getCrystalSlot()?.hotbarSlot ?: return
            if (spoofHotbar) spoofHotbar(slot)
            else swapToSlot(slot)
        }
    }

    private fun SafeClientEvent.sendPacketDirect(packet: Packet<*>) {
        if (packet is CPacketAnimation && swingMode == SwingMode.CLIENT) player.swingArm(packet.hand) else connection.sendPacket(packet)
    }



    enum class SwingMode {
        CLIENT, SERVER
    }

    private enum class Page {
        GENERAL, PLACE_ONE, PLACE_TWO, EXPLODE_ONE, EXPLODE_TWO
    }
}