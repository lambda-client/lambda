package com.lambda.client.module.modules.player

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.mixin.extension.rightClickDelayTimer
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import net.minecraft.init.Items
import net.minecraft.item.*
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.network.play.client.CPacketPlayerTryUseItem
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent

/**
 * Bowspam code from https://github.com/seppukudevelopment/seppuku/blob/5586365/src/main/java/me/rigamortis/seppuku/impl/module/combat/FastBowModule.java
 */
object FastUse : Module(
    name = "FastUse",
    description = "Use items faster",
    category = Category.PLAYER
) {
    private val delay by setting("Delay", 0, 0..10, 1)
    private val blocks by setting("Blocks", false)
    private val allItems by setting("All Items", false)
    private val expBottles by setting("Exp Bottles", true, { !allItems })
    private val endCrystals by setting("End Crystals", true, { !allItems })
    private val fireworks by setting("Fireworks", false, { !allItems })
    private val bow by setting("Bow", true, { !allItems })
    private val chargeSetting by setting("Bow Charge", 3, 0..20, 1, { allItems || bow })
    private val chargeVariation by setting("Charge Variation", 5, 0..20, 1, { allItems || bow })

    private var lastUsedHand = EnumHand.MAIN_HAND
    private var randomVariation = 0
    private var tickCount = 0

    val bowCharge get() = if (isEnabled && (allItems || bow)) 72000.0 - (chargeSetting.toDouble() + chargeVariation / 2.0) else null

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.END || player.isSpectator) return@safeListener

            if ((allItems || bow) && player.isHandActive && (player.activeItemStack.item == Items.BOW) && player.itemInUseMaxCount >= getBowCharge()) {
                randomVariation = 0
                connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, player.horizontalFacing))
                connection.sendPacket(CPacketPlayerTryUseItem(player.activeHand))
                player.stopActiveHand()
            }

            if (delay > 0) {
                if (tickCount <= 0) {
                    tickCount = delay
                } else {
                    tickCount--
                    return@safeListener
                }
            }

            if (passItemCheck(player.getHeldItem(lastUsedHand).item)) {
                mc.rightClickDelayTimer = 0
            }
        }

        listener<PacketEvent.PostSend> {
            if (it.packet is CPacketPlayerTryUseItem) lastUsedHand = it.packet.hand
            if (it.packet is CPacketPlayerTryUseItemOnBlock) lastUsedHand = it.packet.hand
        }
    }

    private fun getBowCharge(): Int {
        if (randomVariation == 0) {
            randomVariation = if (chargeVariation == 0) 0 else (0..chargeVariation).random()
        }
        return chargeSetting + randomVariation
    }

    private fun passItemCheck(item: Item): Boolean {
        return item !is ItemAir
            && (allItems && item !is ItemBlock
            || blocks && item is ItemBlock
            || expBottles && item is ItemExpBottle
            || endCrystals && item is ItemEndCrystal
            || fireworks && item is ItemFirework)
    }
}