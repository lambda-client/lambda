package com.lambda.client.module.modules.combat

import com.lambda.client.mixin.extension.isInWeb
import com.lambda.client.mixin.extension.syncCurrentPlayItem
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.items.*
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.init.Blocks
import net.minecraft.item.ItemBlock
import net.minecraft.network.play.client.CPacketAnimation
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import kotlin.math.floor
import kotlin.math.round

object Burrow : Module(
    name = "Burrow",
    description = "Place block into self",
    category = Category.COMBAT
){
    /*
    Burrow made by Blade

    TODO: center the player before burrowing
    TODO: NoBlockPush for full blocks like obsidian
    */
    private val page by setting("Page", Page.Main)

    //Main Page
    private val blockToPlace by setting("Block", BlockToPlace.OBSIDIAN, {page == Page.Main})
    private val delay by setting("Place Delay", 5, 0..20, 1, {page == Page.Main}, description = "Delay in ticks, for burrowing again")
    private val onMove by setting("Place On Move", false, {page == Page.Main})
    private val packetRotate by setting("Packet Rotate", true, {page == Page.Main})
    private val packetSwing by setting("Packet Swing", false, {page == Page.Main})
    private val sneakOnPlace by setting("Sneak On Place", true, {page == Page.Main})
    private val autoDisable by setting("Auto Disable", false, {page == Page.Main})

    //Messages Page
    private val showHudInfo by setting("Show Hud Info", true, {page == Page.Messages})
    private val enableMessages by setting("Enable Messages", true, {page == Page.Messages})
    private val toggleMessages by setting("Toggle Messages", true, {page == Page.Messages && enableMessages})
    private val warningMessages by setting("Warnings", true, {page == Page.Messages && enableMessages})

    //enums
    private enum class Page{
        Main, Messages
    }
    private enum class BlockToPlace{
        OBSIDIAN, ENDERCHEST, ANY
    }

    private var placePos = BlockPos(0, 0, 0)
    private var firstPitch = 0f
    private var firstSlot = 0
    private var ticksAfterLastBurrow = 0

    override fun getHudInfo(): String {
        return if(showHudInfo){
            if(haveAnyBlock()){
                if ( isBurrowed() ) getBurrowBlock() else ""
            }else{
                "No blocks!"
            }
        }else{ "" }
    }
    init{
        safeListener<ClientTickEvent> {
            ticksAfterLastBurrow++
            if(autoDisable && warningMessages){
                when (blockToPlace){
                    BlockToPlace.OBSIDIAN ->{
                        if(!haveObsidianBlock()) sendMessageClientSide("[Burrow] No obsidian in hotbar")
                    }
                    BlockToPlace.ENDERCHEST ->{
                        if(!haveECBlock()) sendMessageClientSide("[Burrow] No enderchest in hotbar")
                    }
                    BlockToPlace.ANY ->{
                        if(!haveAnyBlock()) sendMessageClientSide("[Burrow] No blocks in hotbar")
                    }
                }
            }
            if (canBurrow()) {
                doBurrow()
            }
        }

        onEnable {
            if(toggleMessages){
                if(!autoDisable){
                    sendMessageClientSide("[Burrow] Enabled!")
                }else{
                    sendMessageClientSide("[Burrow] Burrowed!")
                }

            }

        }
        onDisable {
            if(toggleMessages && !autoDisable) sendMessageClientSide("[Burrow] Disabled!")
        }

    }
    private fun doBurrow(){
        ticksAfterLastBurrow = 0
        firstSlot = mc.player.inventory.currentItem
        placePos = BlockPos(floor(mc.player.positionVector.x), mc.player.positionVector.y - 1, floor(mc.player.positionVector.z))
        firstPitch = mc.player.rotationPitch
        if (packetRotate) mc.player.connection.sendPacket(CPacketPlayer.Rotation(mc.player.rotationYaw, 90f, true))

        when (blockToPlace){
            BlockToPlace.OBSIDIAN ->{
                swapTo(getObsidianBlockSlot())
            }
            BlockToPlace.ENDERCHEST ->{
                swapTo(getECBlockSlot())
            }
            BlockToPlace.ANY ->{
                swapTo(getAnyBlockSlot())
            }
        }

        moveUp()
        place()
        rubberBand()
    }


    //tasks
    private fun moveUp(){
        //update pos before rubberband, for make it some smothly
        mc.player.connection.sendPacket(CPacketPlayer.Position(mc.player.posX, mc.player.posY, mc.player.posZ, true))

        //ServerSide Jump
        mc.player.connection.sendPacket(CPacketPlayer.Position(mc.player.posX, mc.player.posY + 0.41999998688698, mc.player.posZ, false))
        mc.player.connection.sendPacket(CPacketPlayer.Position(mc.player.posX, mc.player.posY + 0.7531999805211997, mc.player.posZ, false))
        mc.player.connection.sendPacket(CPacketPlayer.Position(mc.player.posX, mc.player.posY + 1.00133597911214, mc.player.posZ, false))
        mc.player.connection.sendPacket(CPacketPlayer.Position(mc.player.posX, mc.player.posY + 1.16610926093821, mc.player.posZ, false))
    }
    private fun place(){
        if (sneakOnPlace) mc.player.connection.sendPacket(CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_SNEAKING))

        val vec = Vec3d(placePos).add(0.0, -0.25, 0.0)
        val f: Float = (vec.x - placePos.x.toDouble()).toFloat()
        val f1: Float = (vec.y - placePos.y.toDouble()).toFloat()
        val f2: Float = (vec.z - placePos.z.toDouble()).toFloat()
        mc.player.connection.sendPacket(CPacketPlayerTryUseItemOnBlock(placePos, EnumFacing.UP, EnumHand.MAIN_HAND, f, f1, f2))
      
        if(packetSwing) mc.player.connection.sendPacket(CPacketAnimation(EnumHand.MAIN_HAND)) else mc.player.swingArm(EnumHand.MAIN_HAND)

        if (sneakOnPlace) mc.player.connection.sendPacket(CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SNEAKING))

        if (packetRotate) mc.player.connection.sendPacket(CPacketPlayer.Rotation(mc.player.rotationYaw, firstPitch, false))
    }
    private fun rubberBand(){
        mc.player.connection.sendPacket(CPacketPlayer.Position(mc.player.posX, mc.player.posY + 2, mc.player.posZ, false))

        mc.player.inventory.currentItem = firstSlot
        if(autoDisable) disable()
    }

    //utils
    private fun swapTo(slot: HotbarSlot?){
        val slotNum = slot?.hotbarSlot
        if (slotNum !in 0..8) return
        if (slot != null){
            mc.player.inventory.currentItem = slot.hotbarSlot
        }
        mc.playerController.updateController()
    }

    private fun canBurrow(): Boolean{
        return (
            ticksAfterLastBurrow >= delay &&
                mc.player.onGround &&
                !mc.player.isInWater &&
                !mc.player.isInLava &&
                !mc.player.isOnLadder &&
                !mc.player.isInWeb &&
                !isBurrowed() && (
                (haveObsidianBlock() && blockToPlace == BlockToPlace.OBSIDIAN) ||
                    (haveECBlock() && blockToPlace == BlockToPlace.ENDERCHEST) ||
                    (haveAnyBlock() && blockToPlace == BlockToPlace.ANY)
                ) && (!isMoving() || onMove)

            )//yes)
    }
    private fun isBurrowed(): Boolean {
        return try {
            mc.world.getBlockState(getPlayerPosRoundedY()).material.isSolid
        } catch (e: NullPointerException) {
            false
        }
    }
    private fun getBurrowBlock(): String {
        return if( isBurrowed() ) mc.world.getBlockState(getPlayerPos()).block.localizedName else ""
    }

    private fun getPlayerPos(): BlockPos {
        return BlockPos(mc.player.posX, mc.player.posY, mc.player.posZ)
    }
    private fun getPlayerPosRoundedY(): BlockPos {
        return BlockPos(mc.player.posX, round(mc.player.posY), mc.player.posZ)
    }

    private fun getAnyBlockSlot(): HotbarSlot? {
        mc.playerController.syncCurrentPlayItem()
        return mc.player.hotbarSlots.firstItem<ItemBlock, HotbarSlot>()
    }
    private fun getECBlockSlot(): HotbarSlot? {
        mc.playerController.syncCurrentPlayItem()
        return mc.player.hotbarSlots.firstBlock(Blocks.ENDER_CHEST)
    }
    private fun getObsidianBlockSlot(): HotbarSlot? {
        mc.playerController.syncCurrentPlayItem()
        return mc.player.hotbarSlots.firstBlock(Blocks.OBSIDIAN)
    }

    private fun haveAnyBlock(): Boolean{
        return mc.player.hotbarSlots.firstItem<ItemBlock, HotbarSlot>() != null
    }
    private fun haveECBlock(): Boolean{
        return  mc.player.hotbarSlots.firstBlock(Blocks.ENDER_CHEST) != null
    }
    private fun haveObsidianBlock(): Boolean{
        return mc.player.hotbarSlots.firstBlock(Blocks.OBSIDIAN) != null
    }

    private fun isMoving():Boolean {
        val s = mc.gameSettings
        return (
            s.keyBindForward.isKeyDown ||
                s.keyBindBack.isKeyDown ||
                s.keyBindLeft.isKeyDown ||
                s.keyBindRight.isKeyDown ||
                s.keyBindJump.isKeyDown
            )
    }
    private fun sendMessageClientSide(text: String) {
        if(enableMessages) MessageSendHelper.sendChatMessage(text)
    }
}
