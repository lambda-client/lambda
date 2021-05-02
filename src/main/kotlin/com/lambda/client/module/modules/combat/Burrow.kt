package com.lambda.client.module.modules.combat

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.combat.BurrowUtils
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.block.BlockObsidian
import net.minecraft.entity.item.EntityItem
import net.minecraft.init.Blocks
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.util.EnumHand
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent


internal object Burrow : Module(
    name = "Burrow",
    category = Category.COMBAT,
    description = "Glitch yourself into a block"
) {

    private val rotate by setting("Rotate", false)
    private val offset by setting("Offset", 2.0f, -20.0f..20.0f, 1.0f)
    private val sneak by setting("Sneak", false)

    private var originalPos: BlockPos? = null
    private var oldSlot = -1

    init {
        onEnable {
            originalPos = BlockPos(mc.player.posX, mc.player.posY, mc.player.posZ)

            // If we can't place in our actual post then toggle and return
            if (mc.world.getBlockState(BlockPos(mc.player.posX, mc.player.posY, mc.player.posZ)).block == Blocks.OBSIDIAN ||
                intersectsWithEntity(originalPos!!)) {
                disable()
            }

            // Save our item slot
            oldSlot = mc.player.inventory.currentItem
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (BurrowUtils.findHotbarBlock(BlockObsidian::class.java) == -1) {
                MessageSendHelper.sendChatMessage("Can't find obsidian in hotbar!")
                toggle()
            }

            // Change to obsidian slot
            BurrowUtils.switchToSlot(BurrowUtils.findHotbarBlock(BlockObsidian::class.java))

            // Fake jump
            mc.player.connection.sendPacket(CPacketPlayer.Position(mc.player.posX, mc.player.posY + 0.41999998688698, mc.player.posZ, true))
            mc.player.connection.sendPacket(CPacketPlayer.Position(mc.player.posX, mc.player.posY + 0.7531999805211997, mc.player.posZ, true))
            mc.player.connection.sendPacket(CPacketPlayer.Position(mc.player.posX, mc.player.posY + 1.00133597911214, mc.player.posZ, true))
            mc.player.connection.sendPacket(CPacketPlayer.Position(mc.player.posX, mc.player.posY + 1.16610926093821, mc.player.posZ, true))

            // Sneak option.
            val sneaking = mc.player.isSneaking
            if (sneak && sneaking) {
                    mc.player.connection.sendPacket(CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_SNEAKING))
            }

            // Place block
            originalPos?.let { it1 -> BurrowUtils.placeBlock(it1, EnumHand.MAIN_HAND, rotate, packet = true, isSneaking = false) }

            // Rubberband
            mc.player.connection.sendPacket(CPacketPlayer.Position(mc.player.posX, mc.player.posY + offset, mc.player.posZ, false))

            // SwitchBack
            BurrowUtils.switchToSlot(oldSlot)

            // AutoDisable
            disable()
        }
    }

    private fun intersectsWithEntity(pos: BlockPos): Boolean {
        for (entity in mc.world.loadedEntityList) {
            if (entity.equals(mc.player)) continue
            if (entity is EntityItem) continue
            if (AxisAlignedBB(pos).intersects(entity.entityBoundingBox)) return true
        }
        return false
    }
}