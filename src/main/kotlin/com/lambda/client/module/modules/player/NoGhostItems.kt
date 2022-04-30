package com.lambda.client.module.modules.player

import com.lambda.client.LambdaMod
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.runSafe
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.ClickType
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.CPacketClickWindow
import net.minecraft.network.play.server.SPacketConfirmTransaction
import java.util.concurrent.ConcurrentHashMap

object NoGhostItems : Module(
    name = "NoGhostItems",
    description = "Syncs inventory transactions for strict environments",
    category = Category.PLAYER
) {
    private val pendingTransactions = ConcurrentHashMap<Short, InventoryTransaction>()

    init {
        listener<PacketEvent.Receive> { event ->
            if (event.packet is SPacketConfirmTransaction) {
                pendingTransactions[event.packet.actionNumber]?.let {
                    it.player.openContainer.slotClick(it.slotId, it.mouseButton, it.type, it.player)
                    pendingTransactions.remove(event.packet.actionNumber)
                    LambdaMod.LOG.info("Accepted transaction: ${event.packet.actionNumber}")
                }
            }
        }
    }

    fun handleWindowClick(windowId: Int, slotId: Int, mouseButton: Int, type: ClickType, player: EntityPlayer) {
        val transaction = InventoryTransaction(windowId, slotId, mouseButton, type, player)

        if (!pendingTransactions.values.contains(transaction)) {
            val transactionID = transaction.player.openContainer.getNextTransactionID(transaction.player.inventory)
            pendingTransactions[transactionID] = transaction

            LambdaMod.LOG.info("Started transaction: transactionID: $transactionID transaction: $transaction")
            runSafe {
                connection.sendPacket(CPacketClickWindow(transaction.windowId, transaction.slotId, transaction.mouseButton, transaction.type, ItemStack.EMPTY, transactionID))
            }
        }
    }

    data class InventoryTransaction(val windowId: Int, val slotId: Int, val mouseButton: Int, val type: ClickType, val player: EntityPlayer) {
        override fun toString(): String {
            return "windowId: $windowId slotId: $slotId mouseButton: $mouseButton type: ${type.name} player: ${player.name}"
        }
    }
}