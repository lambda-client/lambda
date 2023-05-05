package com.lambda.client.activity.activities.inventory.core

import com.lambda.client.activity.Activity
import com.lambda.client.activity.types.TimeoutActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.onMainThreadSafe
import com.lambda.client.util.threads.safeListener
import kotlinx.coroutines.launch
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.CPacketCreativeInventoryAction
import net.minecraft.network.play.server.SPacketSetSlot
import net.minecraft.util.EnumHand

class CreativeInventoryAction(
    private val stack: ItemStack,
    override val timeout: Long = 600L
) : TimeoutActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        player.setHeldItem(EnumHand.MAIN_HAND, stack)
        connection.sendPacket(CPacketCreativeInventoryAction(36 + player.inventory.currentItem, stack))
    }

    init {
        safeListener<PacketEvent.PostReceive> {
            if (it.packet !is SPacketSetSlot) return@safeListener

            defaultScope.launch {
                onMainThreadSafe {
                    success()
                }
            }
        }
    }
}