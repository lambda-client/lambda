package com.lambda.client.module.modules.misc

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.managers.PlayerInventoryManager
import com.lambda.client.manager.managers.PlayerInventoryManager.addInventoryTask
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.player.NoGhostItems
import com.lambda.client.util.items.countEmpty
import com.lambda.client.util.items.filterByBlock
import com.lambda.client.util.items.firstBlock
import com.lambda.client.util.items.inventorySlots
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.runSafe
import net.minecraft.init.Blocks
import net.minecraft.inventory.ClickType
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.min

object InventoryTester : Module(
    name = "InventoryTester",
    description = "",
    category = Category.MISC
) {
    private val one = setting("1", false)
    private val two = setting("2", false)
    private val three = setting("3", false)
    private val four = setting("4", false)
    private val five = setting("5", false)
    private val six = setting("6", false)
    private val seven = setting("7", false)
    private val r = setting("r", false)
    private val rp = setting("rp", false)

    init {
        listener<TickEvent.ClientTickEvent> {
            if (it.phase == TickEvent.Phase.END) return@listener

            MessageSendHelper.sendChatMessage("Running")
        }

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
        five.consumers.add { _, it ->
            if (it) {
                runSafe {
                    five()
                }
            }
            false
        }
        six.consumers.add { _, it ->
            if (it) {
                runSafe {
                    six()
                }
            }
            false
        }
        seven.consumers.add { _, it ->
            if (it) {
                runSafe {
                    seven()
                }
            }
            false
        }
        r.consumers.add { _, it ->
            if (it) PlayerInventoryManager.timer.reset()
            false
        }
        rp.consumers.add { _, it ->
            if (it) PlayerInventoryManager.timer.skipTime(NoGhostItems.timeout)
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

    private fun SafeClientEvent.five() {
        val moveList = mutableListOf<PlayerInventoryManager.ClickInfo>()
        player.inventorySlots.filter { !it.stack.isEmpty }.forEach {
            moveList.add(PlayerInventoryManager.ClickInfo(0, it.slotNumber, 0, type = ClickType.SWAP))
        }
        moveList.shuffle()
        addInventoryTask(*moveList.toTypedArray())
    }

    private fun SafeClientEvent.six() {
        val moveList = mutableListOf<PlayerInventoryManager.ClickInfo>()
        val emptySlots = player.inventorySlots.countEmpty()
        var count = 0
        var start = 0

        player.inventorySlots.sortedBy { it.stack.count }.reversed().firstOrNull()?.let {
            count = it.stack.count
            start = it.slotNumber
            moveList.add(PlayerInventoryManager.ClickInfo(0, it.slotNumber, 0, type = ClickType.PICKUP))
        }

        moveList.add(PlayerInventoryManager.ClickInfo(0, -999, 4, type = ClickType.QUICK_CRAFT))
        player.inventorySlots.filter { it.stack.isEmpty }.take(min(count, emptySlots)).forEach {
            moveList.add(PlayerInventoryManager.ClickInfo(0, it.slotNumber, 5, type = ClickType.QUICK_CRAFT))
        }
        moveList.add(PlayerInventoryManager.ClickInfo(0, -999, 6, type = ClickType.QUICK_CRAFT))

        moveList.add(PlayerInventoryManager.ClickInfo(0, start, 0, type = ClickType.PICKUP))

        addInventoryTask(*moveList.toTypedArray())
    }

    private fun SafeClientEvent.seven() {
        addInventoryTask(PlayerInventoryManager.ClickInfo(0, -999, 0, type = ClickType.PICKUP))
        addInventoryTask(PlayerInventoryManager.ClickInfo(1, 0, 0, type = ClickType.QUICK_MOVE))
        addInventoryTask(PlayerInventoryManager.ClickInfo(0, 36, 0, type = ClickType.CLONE))
    }
}