package com.lambda.client.module.modules.misc

import com.lambda.client.LambdaMod
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.manager.managers.PlayerInventoryManager
import com.lambda.client.manager.managers.PlayerInventoryManager.addInventoryTask
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.client.ClickGUI
import com.lambda.client.util.items.filterByBlock
import com.lambda.client.util.items.firstBlock
import com.lambda.client.util.items.inventorySlots
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import net.minecraft.block.BlockObsidian
import net.minecraft.entity.passive.AbstractChestHorse
import net.minecraft.init.Blocks
import net.minecraft.inventory.ClickType
import net.minecraft.network.play.client.CPacketUseEntity

object InventoryTester : Module(
    name = "InventoryTester",
    description = "",
    category = Category.MISC
) {
    private val makeTransaction = setting("GO", false)
    private val makeTransactionn = setting("shuffle", false)
    private val makeTransactionnn = setting("rid", false)

    init {
        makeTransaction.consumers.add { _, it ->
            if (it) {
                runSafe {
                    moveStuff()
                }
            }
            false
        }
        makeTransactionn.consumers.add { _, it ->
            if (it) {
                runSafe {
                    shuffle()
                }
            }
            false
        }
        makeTransactionnn.consumers.add { _, it ->
            if (it) {
                runSafe {
                    throwAll()
                }
            }
            false
        }
    }

    private fun SafeClientEvent.moveStuff() {
        player.inventorySlots.firstBlock(Blocks.OBSIDIAN)?.let {
            addInventoryTask(
                PlayerInventoryManager.ClickInfo(0, it.slotNumber, type = ClickType.QUICK_MOVE)
            )
        }
    }

    private fun SafeClientEvent.moveStufff() {
        val moveList = mutableListOf<PlayerInventoryManager.ClickInfo>()
        player.inventorySlots.filterByBlock(Blocks.OBSIDIAN).forEach {
            moveList.add(PlayerInventoryManager.ClickInfo(0, it.slotNumber, type = ClickType.QUICK_MOVE))
        }
        addInventoryTask(*moveList.toTypedArray())
    }

    private fun SafeClientEvent.shuffle() {
        val moveList = mutableListOf<PlayerInventoryManager.ClickInfo>()
        player.inventorySlots.filter { !it.stack.isEmpty }.forEach {
            moveList.add(PlayerInventoryManager.ClickInfo(0, it.slotNumber, type = ClickType.QUICK_MOVE))
        }
        addInventoryTask(*moveList.toTypedArray())
    }

    private fun SafeClientEvent.throwAll() {
        player.inventorySlots.filter { !it.stack.isEmpty }.forEach {
            addInventoryTask(
                PlayerInventoryManager.ClickInfo(0, it.slotNumber, 0, type = ClickType.SWAP)
            )
        }
    }
}