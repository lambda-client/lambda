package com.lambda.client.module.modules.player

import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.runSafe
import com.lambda.mixin.player.MixinPlayerControllerMP
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.ClickType
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.CPacketClickWindow
import net.minecraft.network.play.server.SPacketConfirmTransaction

/**
 * @see MixinPlayerControllerMP.onWindowClick
 */
object NoGhostItems : Module(
    name = "NoGhostItems",
    description = "Syncs inventory transactions for strict environments",
    category = Category.PLAYER
) {
    private val timeout by setting("Timeout in ticks", 5, 1..50, 1)

    private var pendingTransaction: InventoryTransaction? = null
    private var lastPending = System.currentTimeMillis()

    init {
        listener<PacketEvent.Receive> { event ->
            if (event.packet is SPacketConfirmTransaction) {
                pendingTransaction?.let {
                    it.player.openContainer.slotClick(it.slotId, it.mouseButton, it.type, it.player)
                    pendingTransaction = null
                }
            }
        }
    }

    fun handleWindowClick(windowId: Int, slotId: Int, mouseButton: Int, type: ClickType, player: EntityPlayer) {
        val transaction = InventoryTransaction(windowId, slotId, mouseButton, type, player)

        if (pendingTransaction == null || System.currentTimeMillis() - lastPending > timeout * 50L) {
            val transactionID = transaction.player.openContainer.getNextTransactionID(transaction.player.inventory)
            pendingTransaction = transaction
            lastPending = System.currentTimeMillis()

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