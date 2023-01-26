package com.lambda.client.module.modules.combat

import com.lambda.client.event.Phase
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.CrystalEvent
import com.lambda.client.event.events.PlayerEvent
import com.lambda.client.manager.managers.CombatManager
import com.lambda.client.manager.managers.CrystalManager
import com.lambda.client.manager.managers.HotbarManager
import com.lambda.client.manager.managers.HotbarManager.resetHotbar
import com.lambda.client.manager.managers.HotbarManager.serverSideItem
import com.lambda.client.manager.managers.HotbarManager.spoofHotbar
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.combat.CombatUtils.equipBestWeapon
import com.lambda.client.util.items.*
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.math.VectorUtils.toBlockPos
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getClosestVisibleSide
import net.minecraft.client.entity.EntityPlayerSP
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
    val swingMode by setting("Swing Mode", SwingMode.CLIENT, { page == Page.GENERAL }, description = "Swing mode")

    /* Place page 1 */
    val doPlace by setting("Place", true, { page == Page.PLACE_ONE }, description = "Whether or not it should place")
    val placeRotate by setting("Rotate", true, { page == Page.PLACE_ONE }, description = "Whether or not the player should rotate to place")
    val placeRange by setting("Place Range", 5.0f, 1.0f..6.0f, 0.1f, { page == Page.PLACE_ONE }, description = "The range at which it should place")
    val placeRangeOffset by setting("Place Range Offset", 0.6f, 0.6f..2.0f, 0.1f, { page == Page.PLACE_ONE }, description = "The place offset related to the player")
    val placeThroughWalls by setting("Place Through Walls", true, { page == Page.PLACE_ONE }, description = "Whether or not it should place through walls")
    val placeThroughWallsRange by setting("Place Through Walls Range", 4.0f, 0.0f..6.0f, 0.1f, { placeThroughWalls && page == Page.PLACE_ONE }, description = "The range at which it should place through walls")
    val autoSwap by setting("Auto Swap", true, { page == Page.PLACE_ONE }, description = "Automatically swap to crystals")
    val spoofHotbar by setting("Spoof Hotbar", false, { page == Page.PLACE_ONE }, description = "Spoofs your hotbar server side")

    /* Place page 2 */
    val placeMinDistance by setting("Place Min Distance", 0.0f, 0.0f..5.0f, 0.1f, { page == Page.PLACE_TWO }, description = "Minimum distance to place")
    val placeMinSelfDistance by setting("Place Min Self Distance", 0.0f, 0.0f..5.0f, 0.1f, { page == Page.PLACE_TWO }, description = "Minimum distance to place from self")
    val placeMinDamage by setting("Place Min Damage", 4.25f, 0.0f..20f, 0.1f, { page == Page.PLACE_TWO }, description = "Minimum damage to place")
    val placeMaxSelfDamage by setting("Place Max Self Damage", 4.25f, 0.0f..20f, 0.1f, { page == Page.PLACE_TWO }, description = "Maximum self damage to place")

    /* Explode page 1 */
    val antiWeakness by setting("Anti Weakness", true, { page == Page.EXPLODE_ONE }, description = "Uses a tool for exploding")

    /* Explode page 2 */
    val doExplode by setting("Explode", true, { page == Page.EXPLODE_TWO }, description = "Whether or not it should explode")
    val explodeRotate by setting("Rotate", true, { page == Page.EXPLODE_TWO }, description = "Whether or not the player should rotate to explode")
    val explodeRange by setting("Explode Range", 5.0f, 1.0f..6.0f, 0.1f, { page == Page.EXPLODE_TWO }, description = "The range at which it should explode")
    val explodeMinDistance by setting("Explode Min Distance", 0f, 0.0f..5.0f, 0.1f, { page == Page.EXPLODE_TWO }, description = "Minimum distance to explode")
    val explodeMinSelfDistance by setting("Explode Min Self Distance", 0f, 0.0f..5.0f, 0.1f, { page == Page.EXPLODE_TWO }, description = "Minimum distance to explode from self")
    val explodeMinDamage by setting("Explode Min Damage", 4.25f, 0.0f..20f, 0.1f, { page == Page.EXPLODE_TWO }, description = "Minimum damage to explode")
    val explodeMaxSelfDamage by setting("Explode Max Self Damage", 4.25f, 0.0f..20f, 0.1f, { page == Page.EXPLODE_TWO }, description = "Maximum self damage to explode")

    private var timer = TickTimer(TimeUnit.TICKS)
    private var lastLookAt: Vec3d? = null

    var inactiveTicks = 20; private set

    init {
        onEnable {
            timer.reset()
        }

        onDisable {
            timer.reset(-69420L)
            inactiveTicks = 10
            lastLookAt = null
            resetHotbar()
        }

        safeListener<CrystalEvent.PlaceEvent> {
            place(it.destination.position)
        }

        safeListener<CrystalEvent.BreakEvent> {
            explode(it.target)
        }

        safeListener<PlayerEvent.UpdateWalking> {
            if (!CombatManager.isOnTopPriority(CrystalAura) || CombatSetting.pause || it.phase != Phase.PRE) return@safeListener

            if (inactiveTicks <= 20 && lastLookAt != null && explodeRotate) {
                sendPlayerPacket {
                    rotate(getLastRotation())
                }
            }
        }

        safeListener<TickEvent.ClientTickEvent>(3000) {
            if (it.phase == TickEvent.Phase.START) {
                inactiveTicks++
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
    }


    /* Placing */
    fun SafeClientEvent.place(destination: Vec3d) {
        if (!hasCrystalInHand()) swapToCrystal()
        placeCrystal(destination, getHand())
    }

    private fun SafeClientEvent.placeCrystal(position: Vec3d, hand: EnumHand) {
        if (placeRotate) {
            sendPlayerPacket {
                rotate(getRotationTo(position))
            }
        }
        sendPacketDirect(getPlacePacket(position, hand))
        sendPacketDirect(CPacketAnimation(hand))
    }

    private fun SafeClientEvent.getPlacePacket(pos: Vec3d, hand: EnumHand): CPacketPlayerTryUseItemOnBlock {
        val side = getClosestVisibleSide(pos) ?: EnumFacing.UP
        return CPacketPlayerTryUseItemOnBlock(pos.toBlockPos(), side, hand, 0.5f, 0.5f, 0.5f)
    }

    /* End of placing */


    /* Exploding */

    fun SafeClientEvent.explode(crystal: CrystalManager.Crystal) {
        if (!preExplode()) return
        explodeDirect(crystal, CPacketUseEntity(crystal.entity))
    }


    private fun SafeClientEvent.preExplode(): Boolean {
        if (antiWeakness && player.isPotionActive(MobEffects.WEAKNESS) && !isHoldingTool()) {
            equipBestWeapon(allowTool = true)
            resetHotbar()
            return false
        }

        return true
    }

    private fun SafeClientEvent.explodeDirect(crystal: CrystalManager.Crystal, packet: CPacketUseEntity) {
        inactiveTicks = 0
        lastLookAt = crystal.entity.positionVector
        sendPacketDirect(packet)
        sendPacketDirect(CPacketAnimation(getHand()))
    }

    /* End of exploding */


    /* Rotation */

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