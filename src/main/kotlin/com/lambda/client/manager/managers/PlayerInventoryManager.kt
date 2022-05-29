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
import com.lambda.client.util.threads.safeListener
import com.lambda.client.event.listener.listener
import com.lambda.client.module.modules.player.LagNotifier
import com.lambda.client.module.modules.player.NoGhostItems
import com.lambda.client.process.PauseProcess.pauseBaritone
import com.lambda.client.process.PauseProcess.unpauseBaritone
import com.lambda.client.util.threads.onMainThreadSafe
import kotlinx.coroutines.runBlocking
import net.minecraft.inventory.ClickType
import net.minecraft.inventory.Container
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.CPacketClickWindow
import net.minecraft.network.play.server.SPacketConfirmTransaction
import java.util.*
import java.util.concurrent.ConcurrentSkipListSet

/**
 * @see NoGhostItems
 */
object PlayerInventoryManager : Manager {
    val timer = TickTimer()
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
                    if (!packet.wasAccepted()) {
                        LambdaMod.LOG.error("Transaction ${clickInfo.transactionId} was denied. Skipping task. (id=${packet.actionNumber}, window=${packet.windowId})")
                        next()
                        return@safeListener
                    }

                    LambdaMod.LOG.info("Transaction accepted. (id=${packet.actionNumber}, window=${packet.windowId})")
                    getContainerOrNull(packet.windowId)?.let { container ->
                        container.slotClick(clickInfo.slot, clickInfo.mouseButton, clickInfo.type, player)

                        currentTask.nextInfo()
                        if (currentTask.isDone) transactionQueue.pollFirst()
                    } ?: run {
                        LambdaMod.LOG.error("Container outdated in window: ${player.openContainer}. Skipping task. (id=${packet.actionNumber}, window=${packet.windowId})")
                        next()
                    }
                }
            }
        }

        safeListener<RenderOverlayEvent>(0) {
//            if (!player.inventory.itemStack.isEmpty) { ToDo: Rewrite idea
//                if (mc.currentScreen is GuiContainer) timer.reset(250L) // Wait for 5 extra ticks if player is moving item
//                else removeHoldingItem()
//                return@safeListener
//            }

            if (LagNotifier.paused) return@safeListener

            if (transactionQueue.isEmpty()) {
                currentId = 0
                if (NoGhostItems.baritoneSync) NoGhostItems.unpauseBaritone()
                return@safeListener
            }

            transactionQueue.firstOrNull()?.let { currentTask ->
                currentTask.currentInfo()?.let { currentInfo ->
                    if (currentInfo.transactionId < 0 || timer.tick(NoGhostItems.timeout, false)) {
                        if (currentInfo.tries > NoGhostItems.maxRetries) {
                            LambdaMod.LOG.error("Max inventory transaction tries exceeded. Skipping task.")
                            next()
                        }

                        deployWindowClick(currentInfo)
                        timer.reset()
                    }
                }
            }
        }

        listener<ConnectionEvent.Disconnect> {
            reset()
        }
    }

    private fun SafeClientEvent.deployWindowClick(currentInfo: ClickInfo) {
        val transactionId = clickSlotServerSide(currentInfo)
        if (transactionId > -1) {
            currentInfo.transactionId = transactionId
            currentInfo.tries++
            LambdaMod.LOG.info("Transaction successfully initiated. ${transactionQueue.size} left. $currentInfo")
        } else {
            LambdaMod.LOG.error("Container outdated. Skipping task. $currentInfo")
            next()
        }
    }

    /**
     * Sends transaction of inventory clicking in specific window, slot, mouseButton, and click type without performing client side changes.
     *
     * @return Transaction id
     */
    private fun SafeClientEvent.clickSlotServerSide(currentInfo: ClickInfo): Short {
        var transactionID: Short = -1

        getContainerOrNull(currentInfo.windowId)?.let { activeContainer ->
            player.inventory?.let { inventory ->
                transactionID = activeContainer.getNextTransactionID(inventory)

                val itemStack = if (currentInfo.type == ClickType.PICKUP && currentInfo.slot != -999) {
                    activeContainer.inventorySlots?.getOrNull(currentInfo.slot)?.stack ?: ItemStack.EMPTY
                } else {
                    ItemStack.EMPTY
                }

                connection.sendPacket(CPacketClickWindow(
                    currentInfo.windowId,
                    currentInfo.slot,
                    currentInfo.mouseButton,
                    currentInfo.type,
                    itemStack,
                    transactionID
                ))

                runBlocking {
                    onMainThreadSafe { playerController.updateController() }
                }
            }
        } ?: run {
            LambdaMod.LOG.error("Container outdated. Skipping task. $currentInfo")
            next()
        }

        return transactionID
    }

    private fun SafeClientEvent.getContainerOrNull(windowId: Int): Container? =
        if (windowId == player.openContainer.windowId) {
            player.openContainer
        } else {
            null
        }

    fun next() {
        transactionQueue.pollFirst()
        timer.skipTime(NoGhostItems.timeout)
    }

    fun reset() {
        transactionQueue.clear()
        currentId = 0
    }

    fun isDone() = transactionQueue.isEmpty()

    /**
     * Adds a new task to the inventory manager
     *
     * @param clickInfo group of the click info in this task
     *
     * @return [TaskState] representing the state of this task
     */
    fun AbstractModule.addInventoryTask(vararg clickInfo: ClickInfo): TaskState {
        if (NoGhostItems.baritoneSync) NoGhostItems.pauseBaritone()
        return InventoryTask(currentId++, modulePriority, clickInfo).let {
            transactionQueue.add(it)
            it.taskState
        }
    }

    fun addInventoryTask(vararg clickInfo: ClickInfo) =
        InventoryTask(currentId++, 0, clickInfo).let {
            transactionQueue.add(it)
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
        var tries = 0

        override fun toString(): String {
            return "(id=$transactionId, tries=$tries, windowId=$windowId, slot=$slot, mouseButton=$mouseButton, type=$type)"
        }
    }
}