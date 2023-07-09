package com.lambda.client.module.modules.combat

import com.lambda.client.commons.interfaces.DisplayEnum
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.TimerManager.modifyTimer
import com.lambda.client.mixin.extension.isInWeb
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.items.block
import com.lambda.client.util.items.firstItem
import com.lambda.client.util.items.hotbarSlots
import com.lambda.client.util.math.RotationUtils
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.EntityLivingBase
import net.minecraft.init.Blocks
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemBlock
import net.minecraft.network.play.client.CPacketAnimation
import net.minecraft.network.play.client.CPacketHeldItemChange
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.abs
import kotlin.math.hypot

object Burrow : Module(
    name = "Burrow",
    alias = arrayOf("SelfFill", "SelfBlock", "RubberFill", "RubberBlock", "FootConcrete", "BlockLag"),
    description = "Automatically burrows you in a hole",
    category = Category.COMBAT
) {

    // behaviour
    private val nearby by setting("Nearby", true,
        description = "Burrows only if a player is nearby")
    private val range by setting("Range", 4.25f, 0.25f..6f, 0.25f,
        description = "Range to search for a player", visibility = { nearby })
    private val cancel by setting("Cancel On ", Cancel.NONE,
        description = "When to disable burrow", visibility = { nearby })
    private val autoDisable by setting("Auto Disable", true,
        description = "Automatically disables when burrowed")

    // bypass
    private val packetMode by setting("Rubberband Mode", PacketMode.SMART,
        description = "The method to use to rubberband")
    private val offset by setting("Offset", 2.0, -25.0..25.0, 1.0,
        description = "Offset to send to the server", visibility = { packetMode == PacketMode.DUMB })
    private val strength by setting("Strength", 20, 1..50, 1,
        description = "Strength of the rubberband, higher meaning more likely to work but flags more afterwards",
        visibility = { packetMode == PacketMode.ALTERNATIVE || packetMode == PacketMode.SPIN })
    private val bladeFunny by setting("Dip Bypass", false,
        description = "Bypasses with a dip", visibility = { packetMode != PacketMode.NONE })

    // QOL
    private val smooth by setting("Smooth", true,
        description = "Smooths out the rubberbanding", visibility = { packetMode != PacketMode.NONE })
    private val noTimerFlag by setting("No Timer Flag", true,
        description = "Prevents flagging for timer",
        visibility = { packetMode != PacketMode.ALTERNATIVE })

    // generic
    private val rotate by setting("Rotate", true,
        description = "Rotates towards the block",)
    private val noPeerRotate by setting("No Peer Rotate", true,
        description = "Prevents peers from seeing our rotation",
        visibility = { rotate })

    private var lastBurrow = 0L

    enum class Cancel(override val displayName: String, val should: () -> Boolean) : DisplayEnum {
        NONE("None", { false }),
        // this returns true when moved up by knockback or a piston
        JUMP("Jump", { mc.player.motionY > 0 }),
        // this is a larger distance than a block
        MOVE("Move", { hypot(mc.player.posX - mc.player.prevPosX,
            mc.player.posZ - mc.player.prevPosZ) > .2 || JUMP.should.invoke() }),
    }

    enum class PacketMode(override val displayName: String, val rubberband: Runnable) : DisplayEnum {
        NONE("None", {
            // we just want to jump up and place to surprise the enemy gamer, this shouldn't flag at all
        }),

        DUMB("Dumb", {
            // offset by a given amount
            sendPos(offset + 1)
        }),

        WEIRD("Weird", {
            // this moves us back into the block we placed
            sendPos(0.42)
            sendPos(0.75)
        }),

        SMART("Smart", {
            // offset an amount that doesn't collide with a block and isn't our position
            for (i in -5..5) {

                // offset too close won't rubberband
                if (abs(i) <= 1)
                    continue

                if (!mc.world.collidesWithAnyBlock(mc.player.entityBoundingBox
                        .offset(0.0, 1 + i.toDouble(), 0.0))) {
                    sendPos(1 + i.toDouble())
                    break
                }

            }
        }),

        HORIZONTAL("Horizontal", {
            // we want to rubberband horizontally, so we check X and Z
            for (x in -5..5)
                for (z in -5..5)
                    if (!mc.world.collidesWithAnyBlock(mc.player.entityBoundingBox
                            .offset(x.toDouble(), 1.0, z.toDouble()))) {
                        mc.player.connection.sendPacket(
                            CPacketPlayer.Position(
                                mc.player.posX + x,
                                mc.player.posY + 1.0,
                                mc.player.posZ + z,
                                mc.player.onGround)
                        )
                        break
                    }
        }),

        ALTERNATIVE("Alternative", {
            // this flags for timer, which used to bypass 2b2tpvp and .cc and likely bypasses other rubbish patches
            for (i in 0..strength / 2) {
                sendPos(1.0 + Math.random() * 0.1)
                sendPos(1.0)
            }
        }),

        SPIN("Rotations", {
            // this flags for timer, which used to bypass 2b2tpvp and .cc and likely bypasses other rubbish patches
            for (i in 0..strength) {
                mc.player.connection.sendPacket(
                    CPacketPlayer.Rotation(
                        RotationUtils.normalizeAngle(
                            mc.player.rotationYaw + Math.random().toFloat() * 360f
                        ), mc.player.rotationPitch, mc.player.onGround)
                )
            }
        }),
    }

    var packets = 0

    init {

        safeListener<TickEvent.ClientTickEvent> { event ->

            if (event.phase.equals(TickEvent.Phase.START))
                return@safeListener

            // we don't want to burrow more than once a second, to give time to catch up with the server. Re-burrowing
            // within 1 second is always either instant mine or we never burrowed in the first place. Allowing NONE
            // means it can be used as a tower and without the timer setting also bypassed 2b2tpvp at one point.
            if (System.currentTimeMillis() - lastBurrow < 1000 && packetMode != PacketMode.NONE)
                return@safeListener

            // if we should disable due to motion or anything
            if (cancel.should.invoke()) {
                disable()
                return@safeListener
            }

            if (!player.onGround)
                return@safeListener

            // check we aren't in an invalid position to burrow
            if (mc.player.isInWeb || mc.player.isInLava || mc.player.isInWater || mc.player.isInLava
                || world.collidesWithAnyBlock(player.entityBoundingBox)) {
                if (autoDisable)
                    disable()
                return@safeListener
            }

            // check that we are near players or don't care
            if (nearby && world.playerEntities.none {
                    Vec3d(BlockPos(player.positionVector)).add(.0, 1.0, .0)
                        .squareDistanceTo(it.posX, it.posY, it.posZ) <= range * range && it != player
                })
                return@safeListener

            // check any colliding entities
            if (world.getEntitiesWithinAABB(EntityLivingBase::class.java,
                    AxisAlignedBB(BlockPos(player.posX, player.posY, player.posZ))).any { it != player })
                return@safeListener

            // get slot
            val slot = player.hotbarSlots.firstItem<ItemBlock, Slot> {
                it.item is ItemBlock && it.item.block.let { b -> b == Blocks.OBSIDIAN || b == Blocks.ENDER_CHEST }
            } ?: return@safeListener

            burrow(slot.slotIndex)

        }

        onDisable {
            packets = 0
        }

    }

    private fun SafeClientEvent.burrow(slot: Int) {

        // basically No003
        if (smooth)
            sendPos(0.0)

        // start of a jump
        sendPos(0.42)
        sendPos(0.75)

        // Vanilla would have us go to ~1.01 but if we aren't rubberbanding we want 1.0 so that we don't have to fall
        if (packetMode != PacketMode.NONE) {
            sendPos(1.01)
            sendPosRot(1.16)
        } else {
            sendPosRot(1.0)
        }

        // swap to block
        if (player.inventory.currentItem != slot)
            connection.sendPacket(CPacketHeldItemChange(slot))

        // swing arm to place
        connection.sendPacket(CPacketAnimation(EnumHand.MAIN_HAND))

        // place block, we don't need to use the util since we don't need to check for collision with ourself
        connection.sendPacket(CPacketPlayerTryUseItemOnBlock(
            BlockPos(mc.player.posX, mc.player.posY, mc.player.posZ).offset(EnumFacing.DOWN),
            EnumFacing.UP, EnumHand.MAIN_HAND, 0.5f, 1f, 0.5f
        ))

        // we want to return our rotation so other players can't see we rotated.
        // this is kinda useless but means its slightly less obvious that we burrowed
        // slightly.
        // todo: if we are rotating from another module we don't need to do this
        if (noPeerRotate)
            connection.sendPacket(CPacketPlayer.Rotation(mc.player.rotationYaw, mc.player.rotationPitch, mc.player.onGround))

        // set last burrow
        lastBurrow = System.currentTimeMillis()

        // swap back
        if (player.inventory.currentItem != slot)
            connection.sendPacket(CPacketHeldItemChange(player.inventory.currentItem))

        // rubberband
        packetMode.rubberband.run()

        // we don't do this in the rubberband since we need the slot
        if (packetMode == PacketMode.NONE) {
            world.setBlockState(BlockPos(player.posX, player.posY, player.posZ),
                (player.hotbarSlots[slot].stack.item as ItemBlock).block.defaultState)
            player.setPosition(player.posX, player.posY + 1, player.posZ)
        } else if (bladeFunny)
            sendPos(0.99)

        // account for timer, alternative needs us to flag timer so we don't care about it
        if (noTimerFlag && packetMode != PacketMode.ALTERNATIVE) {
            modifyTimer(50f * packets)
            packets = 0
        }

        // we might not want to disable
        if (autoDisable) disable()

    }

    private fun sendPos(y: Double) {
        mc.player.connection.sendPacket(CPacketPlayer.Position(mc.player.posX, mc.player.posY + y, mc.player.posZ,
            false))
        packets++
    }

    private fun sendPosRot(y: Double) {
        if (rotate)
            mc.player.connection.sendPacket(CPacketPlayer.PositionRotation(mc.player.posX, mc.player.posY + y, mc.player.posZ,
                mc.player.rotationYaw, 90f, false))
        else
            sendPos(y)
        packets++
    }

}