package com.lambda.client.module.modules.misc

import net.minecraft.init.Items
import net.minecraft.inventory.ClickType
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayerTryUseItem
import net.minecraft.util.EnumHand
import net.minecraftforge.fml.common.gameevent.TickEvent

import com.lambda.client.mixin.extension.tickLength
import com.lambda.client.mixin.extension.timer
import com.lambda.client.module.Category
import com.lambda.client.module.Module

import com.lambda.client.util.items.allSlots
import com.lambda.client.util.items.firstItem
import com.lambda.client.util.items.hotbarSlots
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import java.lang.Integer.parseInt
internal object VannilaTakeoff : Module(
    name = "VannilaTakeoff",
    category = Category.MISC,
    description = "Automatically sends player up via elytra and fireworks",

    ) {
    private val playerPitch = setting("Pitch Angle", -45, -90..90, 1)
    private val timerVal = setting("timerVal", 333, 1..1000, 2)

    private val delay = setting("Delay", 2, 1..20, 2)
    private val pitchControl = setting("pitchControl", true)

    private fun fireRocket(){
        safeListener<TickEvent.ClientTickEvent> {
            connection.sendPacket(CPacketPlayerTryUseItem(EnumHand.MAIN_HAND))
        }
    }
    init {
        onEnable {



        }
        onDisable {
            MessageSendHelper.sendChatMessage("Disabling $chatName")
            mc.timer.tickLength = 50f
        }

        safeListener<TickEvent.ClientTickEvent> {
            val dropFlyYet = false
            val chestArmourVal = Companion.mc.player.inventory.armorInventory[2].item
            val slotWithFireworksInv = Companion.mc.player.allSlots.firstItem(Items.FIREWORKS)
            val slotWithElytra = Companion.mc.player.allSlots.firstItem(Items.ELYTRA)

            val slotWithFireworksHotbar = Companion.mc.player.hotbarSlots.firstItem(Items.FIREWORKS)
            if (slotWithElytra == null){
                //    MessageSendHelper.sendChatMessage("No elytra")
                //disable()
            }
            if (slotWithFireworksHotbar == null && slotWithFireworksInv == null){
                //  MessageSendHelper.sendChatMessage("No fireworks")
                // disable()

            }
            val needsDelay = false
            if (slotWithFireworksHotbar == null && !Companion.mc.player.isElytraFlying&& Companion.mc.player.onGround) {
                if (slotWithFireworksInv != null) {
                    needsDelay == true
                    //  MessageSendHelper.sendChatMessage("putting fireworks in hotbar")
                    Companion.mc.playerController.windowClick(Companion.mc.player.inventoryContainer.windowId, slotWithFireworksInv.slotIndex, 0, ClickType.PICKUP, Companion.mc.player)
                    Companion.mc.playerController.windowClick(Companion.mc.player.inventoryContainer.windowId, mc.player.inventory.currentItem+36,0, ClickType.PICKUP, Companion.mc.player)

                    Companion.mc.playerController.windowClick(Companion.mc.player.inventoryContainer.windowId, slotWithFireworksInv.slotIndex, 0, ClickType.PICKUP, Companion.mc.player)

                    Companion.mc.playerController.updateController()


                }
            }
            if (chestArmourVal == Items.DIAMOND_CHESTPLATE&& !Companion.mc.player.isElytraFlying && Companion.mc.player.onGround) {
                //MessageSendHelper.sendChatMessage(slotWithElytra.toString())
                if (slotWithElytra != null) {

                    needsDelay == true
                    // MessageSendHelper.sendChatMessage("swapping elytra on")

                    Companion.mc.playerController.windowClick(Companion.mc.player.inventoryContainer.windowId, slotWithElytra.slotIndex, 0, ClickType.PICKUP, Companion.mc.player)
                    Companion.mc.playerController.windowClick(Companion.mc.player.inventoryContainer.windowId, 6, 0, ClickType.PICKUP, Companion.mc.player)
                    Companion.mc.playerController.windowClick(Companion.mc.player.inventoryContainer.windowId, slotWithElytra.slotIndex, 0, ClickType.PICKUP, Companion.mc.player)
                    Companion.mc.playerController.updateController()

                    //mc.player.rotationYaw = 55.toFloat()
                    //  mc.player.motionY = .4
                    //disable()
                }
            }
            val slotWithFireworksHotbarLate = mc.player.hotbarSlots.firstItem(Items.FIREWORKS)
            if (mc.player.onGround && needsDelay == false    && mc.player.inventory.armorInventory[2].item == Items.ELYTRA) {

                if (slotWithFireworksHotbarLate != null) {
                    //   MessageSendHelper.sendChatMessage(slotWithFireworksHotbarLate.slotIndex.toString() + " slot of fireworks")
                    //   MessageSendHelper.sendChatMessage("swapping fireworks from first hotbar slot to held item slot")
                    if (mc.player.inventory.getCurrentItem().item != Items.FIREWORKS) {
                        mc.player.inventory.currentItem = slotWithFireworksHotbarLate.slotIndex
                    }
                    if (mc.player.ticksExisted % 4 ==0) {
                        mc.player.jump()
                        //mc.player.motionY = 4.toDouble()
                    }
                }
            }
            if (mc.player.ticksExisted % Integer.parseInt(delay.toString()) == 0  && !mc.player.onGround && needsDelay == false&& mc.player.inventory.armorInventory[2].item == Items.ELYTRA){
                // MessageSendHelper.sendChatMessage("setting pitch to " +playerPitch.value.toFloat().toString())
                if (pitchControl.value){
                    mc.player.rotationPitch = playerPitch.value.toFloat()
                }

                //val fuckKotlinPlusOperator = 1



                if (!mc.player.isElytraFlying ) {

                    //MessageSendHelper.sendChatMessage("setting tick lenth to 250")
                    mc.timer.tickLength = timerVal.value.toFloat()
                    //MessageSendHelper.sendChatMessage("Player was not elytra flying and off ground sending elytrafall packet")
                    connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_FALL_FLYING))
                }
                if (mc.player.isElytraFlying){
                    //   mc.timer.tickLength = 200f


                    mc.player.motionY = 0.toDouble()
                    if (slotWithFireworksHotbarLate != null){
                        // MessageSendHelper.sendChatMessage("going to right click mainhand for firework")
                        // mc.playerController.windowClick(this.mc.player.inventoryContainer.windowId, slotWithFireworksHotbarLate.slotIndex+36,mc.player.inventory.currentItem,ClickType.SWAP, this.mc.player)
                        //fireRocket()
                        connection.sendPacket(CPacketPlayerTryUseItem(EnumHand.MAIN_HAND))

                        //mc.playerController.processRightClick(mc.player,mc.world,EnumHand.MAIN_HAND)
                        //mc.playerController.updateController()
                    }


                    disable()
                    //connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, player.horizontalFacing))
                    // connection.sendPacket(CPacketPlayerTryUseItem(player.activeHand))
                }
            }
            if (!mc.player.isElytraFlying){
                //   MessageSendHelper.sendChatMessage("return to da safe listener")
                return@safeListener
            }

            //  MessageSendHelper.sendChatMessage("not as we thot")


        }


    }



}