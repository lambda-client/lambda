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
    private val one = setting("1", false)
    private val two = setting("2", false)
    private val three = setting("3", false)
    private val four = setting("4", false)

    init {
        one.consumers.add { _, it ->
            if (it) {
                runSafe {
                    one()
                }
            }
            false
        }
        two.consumers.add { _, it ->
            if (it) {
                runSafe {
                    two()
                }
            }
            false
        }
        three.consumers.add { _, it ->
            if (it) {
                runSafe {
                    three()
                }
            }
            false
        }
        four.consumers.add { _, it ->
            if (it) {
                runSafe {
                    four()
                }
            }
            false
        }
    }

    private fun SafeClientEvent.one() {
        player.inventorySlots.firstBlock(Blocks.OBSIDIAN)?.let {
            addInventoryTask(
                PlayerInventoryManager.ClickInfo(0, it.slotNumber, type = ClickType.QUICK_MOVE)
            )
        }
    }

    private fun SafeClientEvent.two() {
        val moveList = mutableListOf<PlayerInventoryManager.ClickInfo>()
        player.inventorySlots.filterByBlock(Blocks.OBSIDIAN).forEach {
            moveList.add(PlayerInventoryManager.ClickInfo(0, it.slotNumber, type = ClickType.QUICK_MOVE))
        }
        addInventoryTask(*moveList.toTypedArray())
    }

    private fun SafeClientEvent.three() {
        val moveList = mutableListOf<PlayerInventoryManager.ClickInfo>()
        player.inventorySlots.filter { !it.stack.isEmpty }.forEach {
            moveList.add(PlayerInventoryManager.ClickInfo(0, it.slotNumber, type = ClickType.QUICK_MOVE))
        }
        addInventoryTask(*moveList.toTypedArray())
    }

    private fun SafeClientEvent.four() {
        player.inventorySlots.filter { !it.stack.isEmpty }.forEach {
            addInventoryTask(
                PlayerInventoryManager.ClickInfo(0, it.slotNumber, 0, type = ClickType.SWAP)
            )
        }
    }
}