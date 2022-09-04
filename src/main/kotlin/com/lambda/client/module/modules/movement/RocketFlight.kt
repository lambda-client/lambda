package com.lambda.client.module.modules.movement
import net.minecraft.init.Items
import net.minecraft.inventory.ClickType
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayerTryUseItem
import net.minecraft.util.EnumHand
import net.minecraftforge.fml.common.gameevent.TickEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module


import com.lambda.client.util.items.allSlots
import com.lambda.client.util.items.firstItem
import com.lambda.client.util.items.hotbarSlots
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener

import net.minecraft.network.play.server.SPacketPlayerPosLook


import com.lambda.client.event.events.PacketEvent
import com.lambda.client.module.modules.misc.VannilaTakeoff
import com.lambda.client.module.modules.misc.StashLogger
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit

import com.lambda.event.listener.listener
import net.minecraft.network.play.client.CPacketPlayer

internal object RocketFlight : Module(
    name = "RocketFlight",
    category = Category.MOVEMENT,
    description = "Trys to fly laterally with rockets"
) {
    private val page = setting("Page", Page.GENERALFLIGHT)
    private val ionarPatch = setting("ionarPatch", true)

    private val highWayRefresh = setting("Set2HighWayOnEnable", false)
    private val stashfinderRefresh = setting("Set2StashfinderOnEnable", false)

    private val ItemSwitchDelay = setting("ItemSwitchDelay", 5, 0..20, 1, page.atValue(Page.DELAYS))
    private val yLevelOg = setting("Ylevel2maintain", 121, 1..1000, 1, page.atValue(Page.GENERALFLIGHT))
    private val elytraDropFlyDelay = setting("elytraDropFlyDelay(justkeep@1)", 1, 0..200, 1, page.atValue(Page.DONTTOUCH))
    private val reliefPitch = setting("pitchWhenRocketing", 0.0f, -50.0f..50.0f, 0.25f, page.atValue(Page.GENERALFLIGHT))
    private val aboveOgYPitch = setting("pitchWhenFlyingStraight", 0.0f, -50.0f..50.0f, 0.25f, page.atValue(Page.GENERALFLIGHT))
    private val aboveYSnapDelay = setting("fixPitchDelay", 100, 1..15000, 1,page.atValue(Page.GENERALFLIGHT))
    private val aboveYSnapLevel = setting("aboveMainTainLevelSnap", 50, 1..150, 1, page.atValue(Page.GENERALFLIGHT))
    private val betweenFireworkMsDelay = setting("betweenFirworks", 500, 1..9000, 1, page.atValue(Page.DELAYS))
    private val fireworkAfterRubberBandMs = setting("fireworkAfterRubberBandDelay", 500, 1..9000, 1, page.atValue(Page.DELAYS))
    private val willRetryDelay = setting("willRetryDelay", 100, 0..50000, 1, page.atValue(Page.RETRY))

    private val willAboveYSnapLevel = setting("willAboveYSnapLevel", false, page.atValue(Page.GENERALFLIGHT))
    private val willRetry = setting("willRetry", true,page.atValue(Page.RETRY))

    private val willRocketPointer = setting("dontTouchPointer", false,page.atValue(Page.DONTTOUCH))
    private val enableYOgLvlOnEnable = setting("enableYOgLvlOnEnable", false,page.atValue(Page.DONTTOUCH))


    private var rubberBanding = false
    private var shotRocket = false
    private val soundTimer = TickTimer(TimeUnit.MILLISECONDS)
    private val sinceShotFireWork = TickTimer(TimeUnit.MILLISECONDS)
    private var sinceYLevel = TickTimer(TimeUnit.MILLISECONDS)
    private enum class Page {
        GENERALFLIGHT, DELAYS,RETRY, DONTTOUCH
    }
    init {

        onEnable {


            MessageSendHelper.sendChatMessage("Enabaling $chatName")
            if (enableYOgLvlOnEnable.value) {
                yLevelOg.setValue(mc.player.posY)
                MessageSendHelper.sendChatMessage(yLevelOg.value.toString() + " set og y to ")
                soundTimer.reset(0L)
            }
            if (stashfinderRefresh.value) {
                ItemSwitchDelay.setValue(1.00)
                yLevelOg.setValue(700.00)
                elytraDropFlyDelay.setValue(1.00)
                reliefPitch.setValue(-30.00)
                aboveOgYPitch.setValue(0.00)
                aboveYSnapDelay.setValue(2500.00)
                aboveYSnapLevel.setValue(1.00)
                betweenFireworkMsDelay.setValue(4000.00)
                fireworkAfterRubberBandMs.setValue(1000.00)
                willAboveYSnapLevel.value = false


            }
            if (highWayRefresh.value) {
                ItemSwitchDelay.setValue(1.00)
                yLevelOg.setValue(121.00)
                elytraDropFlyDelay.setValue(1.00)
                reliefPitch.setValue(-4.00)
                aboveOgYPitch.setValue(0.00)
                aboveYSnapDelay.setValue(1.00)
                aboveYSnapLevel.setValue(1.00)
                betweenFireworkMsDelay.setValue(2222.00)
                fireworkAfterRubberBandMs.setValue(310.00)
                willAboveYSnapLevel.value = true


            }

        }


        onDisable {
            MessageSendHelper.sendChatMessage("Disabling $chatName")
            if (enableYOgLvlOnEnable.value) {
                yLevelOg.resetValue()
            }
        }
        listener<PacketEvent.Receive> {
            if (it.packet is SPacketPlayerPosLook) {

                //   MessageSendHelper.sendChatMessage(rubberBanding.toString() + " rubeber banding")
                if (mc.player != null && ionarPatch.value) {
                    mc.player.setVelocity(0.0, 0.0, 0.0)
                    if (mc.player.isElytraFlying) {
                        MessageSendHelper.sendChatMessage("IONAR PATCH!")
                        mc.connection!!.sendPacket(CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_FALL_FLYING))
                        mc.connection!!.sendPacket(CPacketPlayer.Position(mc.player.posX, mc.player.posY - 69, mc.player.posZ, true))

                    }
                }
                rubberBanding = true
                soundTimer.reset(0L)
            }
        }
        if (!StashLogger.isEnabled) {
            StashLogger.enable()
        }
        safeListener<TickEvent.ClientTickEvent> {

            //MessageSendHelper.sendChatMessage("in safe listen")
            willRocketPointer.value = false
            //if player is below y level on enabled
            if (mc.player.posY < yLevelOg.value.toDouble() && it.phase == TickEvent.Phase.START) {


                val slotWithFireworksInv = mc.player.allSlots.firstItem(Items.FIREWORKS)
                val slotWithFireworksHotbar = mc.player.hotbarSlots.firstItem(Items.FIREWORKS)
                //   val chestArmourVal = Companion.mc.player.inventory.armorInventory[2].item


                //MessageSendHelper.sendChatMessage(currentElytraDmg.toString())
                // MessageSendHelper.sendChatMessage(maxElytraDmg.toString())
                // MessageSendHelper.sendChatMessage(currentElytraPercent.toString()+" current elytra dmg val")
                //val currentDamage =
                //  val attemptedFlightRelief = false
                val pitchCorrect = true
                //switch to fireworks in hotbar if applicable
                if (slotWithFireworksHotbar != null && mc.player.getHeldItem(EnumHand.MAIN_HAND).item != Items.FIREWORKS && mc.player.ticksExisted % ItemSwitchDelay.value == 0) {

                    //   MessageSendHelper.sendChatMessage("no fireworks in hand but in hotbar if")
                    mc.player.inventory.currentItem = slotWithFireworksHotbar.slotIndex
                }
                //get fireworks from inv if none in hotbar
                if (slotWithFireworksHotbar == null && mc.player.getHeldItem(EnumHand.MAIN_HAND).item != Items.FIREWORKS && slotWithFireworksInv != null && mc.player.ticksExisted % ItemSwitchDelay.value == 0) {

                    //MessageSendHelper.sendChatMessage("in the no fireworks in hotbar but in inv if")
                    mc.playerController.windowClick(mc.player.inventoryContainer.windowId, slotWithFireworksInv.slotIndex, 0, ClickType.PICKUP, mc.player)
                    mc.playerController.windowClick(mc.player.inventoryContainer.windowId, mc.player.inventory.currentItem + 36, 0, ClickType.PICKUP, mc.player)
                    mc.playerController.windowClick(mc.player.inventoryContainer.windowId, slotWithFireworksInv.slotIndex, 0, ClickType.PICKUP, mc.player)

                }
                //check pitch
                if (reliefPitch.value.toFloat() - 1 < mc.player.rotationPitch && mc.player.rotationPitch < reliefPitch.value.toFloat() + 1) {
                    //  MessageSendHelper.sendChatMessage("correct pitch for flight relief")
                    pitchCorrect == true


                } else {
                    if (mc.player.ticksExisted % ItemSwitchDelay.value == 0) {
                        mc.player.rotationPitch = reliefPitch.value.toFloat()
                        //MessageSendHelper.sendChatMessage("NOT correct pitch for flight relief")
                        pitchCorrect == false
                    }
                }
                ///MessageSendHelper.sendChatMessage(pitchCorrect.toString())
                val willFirework = mc.player.getHeldItem(EnumHand.MAIN_HAND).item == Items.FIREWORKS && mc.player.ticksExisted % elytraDropFlyDelay.value == 0 && pitchCorrect == true && mc.player.isElytraFlying && !rubberBanding
                willRocketPointer.value = willFirework
                if (mc.player.getHeldItem(EnumHand.MAIN_HAND).item == Items.FIREWORKS && mc.player.ticksExisted % elytraDropFlyDelay.value == 0 && pitchCorrect == true && mc.player.isElytraFlying && !rubberBanding && shotRocket == false && soundTimer.tick(fireworkAfterRubberBandMs.value.toLong(), false) && sinceShotFireWork.tick(betweenFireworkMsDelay.value.toLong(), false)) {
                    willRocketPointer.value = false
                    shotRocket = true

                    sinceShotFireWork.reset()
                    //mc.playerController.processRightClick(mc.player, mc.world, EnumHand.MAIN_HAND)
                    //mc.playerController.updateController()
                    //mc.player.inventory.currentItem = 1
                    //send rocket method
                    connection.sendPacket(CPacketPlayerTryUseItem(EnumHand.MAIN_HAND))
                    //  MessageSendHelper.sendChatMessage("will rocket pointer tru")
                    //  MessageSendHelper.sendChatMessage(soundTimer.time.toString())
                }

            }
            shotRocket = false
            rubberBanding = false
            if (!mc.player.isElytraFlying && mc.player.ticksExisted % 100 == 0 && willRetry.value) {
                VannilaTakeoff.enable()
                sinceShotFireWork.reset()

            }
        }
        shotRocket = false
        rubberBanding = false
        // if pitch is correct and has fireworks in hand shoot rocket
//mc.player.posY> (yLevelOg.value+1).toDouble())
        //if player is not below y level on enabled
        safeListener<TickEvent.ClientTickEvent> {
            if (mc.player.posY > yLevelOg.value.toDouble()) {
                //MessageSendHelper.sendChatMessage(" above original set y level")
                //soundTimer.reset()
                if (sinceYLevel.tick(aboveYSnapDelay.value) && !willAboveYSnapLevel.value) {
                    //sinceYLevel
                    //   MessageSendHelper.sendChatMessage(" snap player head towards horizon0")
                    mc.player.rotationPitch = aboveOgYPitch.value.toFloat()
                }
                if (willAboveYSnapLevel.value) {
                    if (mc.player.posY > (yLevelOg.value + aboveYSnapLevel.value).toDouble()) {
                        //MessageSendHelper.sendChatMessage(" snap player head towards horizon1" +mc.player.posY.toString())
                        //mc.player.rotationPitch = aboveOgYPitch.value.toFloat()
                        mc.player.rotationPitch = aboveOgYPitch.value.toFloat()
                    }

                }
            } else {
                sinceYLevel.reset(0L)
            }
            val currentElytraDmg = mc.player.inventory.armorInventory[2].itemDamage.toFloat()
            val maxElytraDmg = mc.player.inventory.armorInventory[2].maxDamage.toFloat()
            val currentElytraPercent = 100 - ((currentElytraDmg / maxElytraDmg) * 100)
            if (mc.player.posY > yLevelOg.value.toDouble() + 20 && it.phase == TickEvent.Phase.START && currentElytraPercent < 3 && aboveOgYPitch.value.toFloat() - 10 < mc.player.rotationPitch && mc.player.rotationPitch < aboveOgYPitch.value.toFloat() + 10 && mc.player.ticksExisted % 20 == 0 && it.phase == TickEvent.Phase.START) {
                //MessageSendHelper.sendChatMessage(" elytra needs switch")
                for (xx in mc.player.allSlots) {
                    //MessageSendHelper.sendChatMessage(" elytra needs switch1")
                    if (xx.stack.item == Items.ELYTRA) {
                        //MessageSendHelper.sendChatMessage(" elytra needs switch2")
                        val heldElytraDmg = xx.stack.itemDamage.toFloat()
                        val maxheldElytraDmg = xx.stack.maxDamage.toFloat()
                        val currentHeldElytraPercent = 100 - ((heldElytraDmg / maxheldElytraDmg) * 100)
                        if (currentHeldElytraPercent > 10) {
                            // MessageSendHelper.sendChatMessage(" elytra needs switch3")
                            mc.playerController.windowClick(mc.player.inventoryContainer.windowId, xx.slotIndex, 0, ClickType.PICKUP, mc.player)
                            mc.playerController.windowClick(mc.player.inventoryContainer.windowId, 6, 0, ClickType.PICKUP, mc.player)
                            mc.playerController.windowClick(mc.player.inventoryContainer.windowId, xx.slotIndex, 0, ClickType.PICKUP, mc.player)
                            break
                        }

                    }
                }

            }
        }

    }

}
