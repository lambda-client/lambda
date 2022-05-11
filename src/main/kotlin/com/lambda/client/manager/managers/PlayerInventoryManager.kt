package com.lambda.client.manager.managers

import com.lambda.client.LambdaMod
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.RenderOverlayEvent
import com.lambda.client.manager.Manager
import com.lambda.client.module.AbstractModule
import com.lambda.client.util.TaskState
import com.lambda.client.util.TickTimer
import com.lambda.client.util.items.removeHoldingItem
import com.lambda.client.util.threads.safeListener
import com.lambda.client.event.listener.listener
import com.lambda.client.module.modules.player.NoGhostItems
import com.lambda.client.util.threads.onMainThreadSafe
import kotlinx.coroutines.runBlocking
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.inventory.ClickType
import net.minecraft.inventory.Container
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.CPacketClickWindow
import net.minecraft.network.play.server.SPacketConfirmTransaction
import java.util.*
import java.util.concurrent.ConcurrentSkipListSet

object PlayerInventoryManager : Manager {
    private val timer = TickTimer()
    private val transactionQueue = ConcurrentSkipListSet<InventoryTask>(Comparator.reverseOrder())

    private var currentId = 0

    init {
        safeListener<PacketEvent.PostReceive> { event ->
            val packet = event.packet
            if (packet is SPacketConfirmTransaction && transactionQueue.isNotEmpty()) {
                val currentTask = transactionQueue.first()
                val clickInfo = currentTask.currentInfo() ?: return@safeListener

                if (packet.actionNumber == clickInfo.transactionId
                    && packet.windowId == clickInfo.windowId
                ) {
                    if (packet.wasAccepted()) {
                        LambdaMod.LOG.info("Transaction id: ${packet.actionNumber} window: ${packet.windowId} accepted.")
                        getContainerOrNull(packet.windowId)?.let { container ->
                            container.slotClick(clickInfo.slot, clickInfo.mouseButton, clickInfo.type, player)
                            LambdaMod.LOG.info("Transaction id: ${clickInfo.transactionId} client sided executed.")

                            currentTask.nextInfo()
                            if (currentTask.isDone) transactionQueue.pollFirst()
                        } ?: run {
                            LambdaMod.LOG.info("Container outdated: packet id: ${packet.windowId} current id: ${player.openContainer}")
                            transactionQueue.pollFirst()
                        }
                    } else {
                        LambdaMod.LOG.info("############################# Transaction id: ${clickInfo.transactionId} was not accepted. (Packet transactionId: ${packet.actionNumber} windowId: ${packet.windowId})")
                        transactionQueue.pollFirst()
                    }
                }
            }
        }

        safeListener<RenderOverlayEvent>(0) {
//            if (!timer.tick((1000 / TpsCalculator.tickRate).toLong())) return@safeListener

//            if (!player.inventory.itemStack.isEmpty) {
//                if (mc.currentScreen is GuiContainer) timer.reset(250L) // Wait for 5 extra ticks if player is moving item
//                else removeHoldingItem()
//                return@safeListener
//            }

            if (transactionQueue.isEmpty()) {
                currentId = 0
                return@safeListener
            }

            transactionQueue.firstOrNull()?.let { currentTask ->
                currentTask.currentInfo()?.let { currentInfo ->
//                    if (currentInfo.transactionId < 0 || timer.tick(NoGhostItems.timeout)) {
                    if (currentInfo.transactionId < 0) {
                        deployWindowClick(currentInfo)
                    }
                }
            }
        }

        listener<ConnectionEvent.Disconnect> {
            reset()
        }
    }

    private fun SafeClientEvent.deployWindowClick(currentInfo: ClickInfo) {
        val transactionId = clickSlotServerSide(currentInfo.windowId, currentInfo.slot, currentInfo.mouseButton, currentInfo.type)
        if (transactionId > -1) {
            currentInfo.transactionId = transactionId
            LambdaMod.LOG.info("Transaction $transactionId successfully initiated.")
            LambdaMod.LOG.info(transactionQueue.joinToString("\n"))
//            timer.reset()
        } else {
            LambdaMod.LOG.info("Container outdated.\n")
            transactionQueue.pollFirst()
        }
    }

    /**
     * Sends transaction of inventory clicking in specific window, slot, mouseButton, and click type without performing client side changes.
     *
     * @return Transaction id
     */
    private fun SafeClientEvent.clickSlotServerSide(windowId: Int = 0, slot: Int, mouseButton: Int = 0, type: ClickType): Short {
        var transactionID: Short = -1

        getContainerOrNull(windowId)?.let { activeContainer ->
            player.inventory?.let { inventory ->
                transactionID = activeContainer.getNextTransactionID(inventory)

                val itemStack = if (type == ClickType.PICKUP && slot != -999) {
                    getContainerOrNull(windowId)?.inventorySlots?.getOrNull(slot)?.stack ?: ItemStack.EMPTY
                } else {
                    ItemStack.EMPTY
                }

                connection.sendPacket(CPacketClickWindow(windowId, slot, mouseButton, type, itemStack, transactionID))

                runBlocking {
                    onMainThreadSafe { playerController.updateController() }
                }
            }
        }

        return transactionID
    }

    private fun SafeClientEvent.getContainerOrNull(windowId: Int): Container? =
        when (windowId) {
            player.inventoryContainer.windowId -> {
                player.inventoryContainer
            }
            player.openContainer.windowId -> {
                player.openContainer
            }
            else -> {
                null
            }
        }

    fun isDone() = transactionQueue.isEmpty()

    fun reset() {
        transactionQueue.clear()
        currentId = 0
    }

    /**
     * Adds a new task to the inventory manager
     *
     * @param clickInfo group of the click info in this task
     *
     * @return [TaskState] representing the state of this task
     */
    fun AbstractModule.addInventoryTask(vararg clickInfo: ClickInfo) =
        InventoryTask(currentId++, modulePriority, clickInfo).let {
            transactionQueue.add(it)
            LambdaMod.LOG.info("PlayerInventoryManager: $it")
            it.taskState
        }

    private data class InventoryTask(
        private val id: Int,
        private val priority: Int,
        private val infoArray: Array<out ClickInfo>,
        val taskState: TaskState = TaskState(),
        private var index: Int = 0
    ) : Comparable<InventoryTask> {
        val isDone get() = taskState.done

        fun currentInfo() = infoArray.getOrNull(index)

        fun nextInfo() {
            index++
            if (currentInfo() == null) taskState.done = true
        }

        override fun compareTo(other: InventoryTask): Int {
            val result = priority - other.priority
            return if (result != 0) result
            else other.id - id
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is InventoryTask) return false

            if (!infoArray.contentEquals(other.infoArray)) return false
            if (index != other.index) return false

            return true
        }

        override fun toString(): String {
            return "id: $id priority: $priority taskState: ${taskState.done} index: $index \n${infoArray.joinToString("\n")}"
        }

        override fun hashCode() = 31 * infoArray.contentHashCode() + index

    }

    data class ClickInfo(val windowId: Int = 0, val slot: Int, val mouseButton: Int = 0, val type: ClickType) {
        var transactionId: Short = -1
        val tries = 0
    }
}