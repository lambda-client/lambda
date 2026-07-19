
package com.minato.module.modules.player

import com.minato.config.ConfigEditor.hideAllExcept
import com.minato.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.minato.config.withEdits
import com.minato.event.events.InventoryEvent
import com.minato.event.events.PlayerEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.task.RootTask.run
import com.minato.task.Task
import com.minato.task.tasks.BuildTask.Companion.breakAndCollectBlock
import com.minato.task.tasks.OpenContainerTask
import com.minato.task.tasks.PlaceContainerTask
import com.minato.util.item.ItemUtils.shulkerBoxes
import net.minecraft.item.Items
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.math.BlockPos

object InventoryTweaks : Module(
    name = "InventoryTweaks",
    tag = ModuleTag.PLAYER,
) {
    private val instantShulker by setting("Instant Shulker", true, description = "Right-click shulker boxes in your inventory to instantly place them and open them.")
    private val instantEChest by setting("Instant Ender-Chest", true, description = "Right-click ender chests in your inventory to instantly place them and open them.")

    private var placedPos: BlockPos? = null
    private var placeAndOpen: Task<*>? = null
    private var lastBreak: Task<*>? = null
    private var lastOpenScreen: ScreenHandler? = null

    init {
        setDefaultAutomationConfig()
            .withEdits {
                hideAllExcept(::breakConfig, ::interactConfig, ::inventoryConfig, ::hotbarConfig)
            }

        listen<PlayerEvent.SlotClick> {
            if (it.action != SlotActionType.PICKUP || it.button != 1) return@listen
            val slot = it.screenHandler.getSlot(it.slot)
            if (!(instantShulker && slot.stack.item in shulkerBoxes) && !(instantEChest && slot.stack.item == Items.ENDER_CHEST)) return@listen
            it.cancel()
            lastOpenScreen = null
            placeAndOpen = PlaceContainerTask(slot, this@InventoryTweaks).then { placePos ->
                placedPos = placePos
                OpenContainerTask(placePos, this@InventoryTweaks).finally { screenHandler ->
                    lastOpenScreen = screenHandler
                }
            }.run()
        }

        listen<InventoryEvent.Close> { event ->
            if (event.screenHandler != lastOpenScreen) return@listen
            lastOpenScreen = null
            placedPos?.let {
                lastBreak = breakAndCollectBlock(it).run()
                placedPos = null
            }
        }

        onDisable {
            placeAndOpen?.cancel()
            lastBreak?.cancel()
        }
    }
}
