package com.lambda.client.activity.activities.inventory

import com.lambda.client.LambdaMod
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.AttemptActivity
import com.lambda.client.activity.activities.types.TimeoutActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.util.threads.safeListener
import net.minecraft.inventory.ClickType
import net.minecraft.inventory.Container
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.CPacketClickWindow
import net.minecraft.network.play.server.SPacketConfirmTransaction

class InventoryTransaction(
    val windowId: Int = 0,
    val slot: Int,
    private val mouseButton: Int = 0,
    val type: ClickType,
    override val timeout: Long = 500L,
    override val maxAttempts: Int = 5,
    override var usedAttempts: Int = 0
) : TimeoutActivity, AttemptActivity, Activity() {
    private var transactionId: Short = -1

    override fun SafeClientEvent.onInitialize() {
        getContainerOrNull(windowId)?.let { activeContainer ->
            transactionId = activeContainer.getNextTransactionID(player.inventory)

            val itemStack = if (type == ClickType.PICKUP && slot != -999) {
                activeContainer.inventorySlots?.getOrNull(slot)?.stack ?: ItemStack.EMPTY
            } else {
                ItemStack.EMPTY
            }

            val packet = CPacketClickWindow(
                windowId,
                slot,
                mouseButton,
                type,
                itemStack,
                transactionId
            )

            connection.sendPacket(packet)

            playerController.updateController()

//            LambdaMod.LOG.info("Sent packet: ${packet.javaClass.simpleName}")
        } ?: run {
            failedWith(ContainerOutdatedException(windowId))
        }
    }

    init {
        safeListener<PacketEvent.PostReceive> {
            val packet = it.packet

            if (packet !is SPacketConfirmTransaction
                || packet.windowId != windowId
                || packet.actionNumber != transactionId
            ) return@safeListener

            if (!packet.wasAccepted()) {
                failedWith(DeniedException(packet.actionNumber))
                return@safeListener
            }

            getContainerOrNull(packet.windowId)?.let { container ->
                container.slotClick(slot, mouseButton, type, player)
                success()
//                LambdaMod.LOG.info("Accepted packet: ${it.packet.javaClass.simpleName} $transactionId")
            } ?: run {
                failedWith(ContainerOutdatedException(packet.windowId))
            }
        }
    }

    private fun SafeClientEvent.getContainerOrNull(windowId: Int): Container? =
        if (windowId == player.openContainer.windowId) {
            player.openContainer
        } else {
            null
        }

    class ContainerOutdatedException(windowId: Int) : Exception("WindowID $windowId of container outdated")
    class DeniedException(transactionId: Short) : Exception("InventoryTransaction $transactionId was denied")
}