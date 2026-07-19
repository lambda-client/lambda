
package com.minato.task.tasks

import com.minato.config.blocks.EatConfig
import com.minato.config.blocks.EatConfig.Companion.reasonEating
import com.minato.context.Automated
import com.minato.context.SafeContext
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.hotbar.HotbarRequest
import com.minato.interaction.material.container.containers.HotbarContainer
import com.minato.interaction.material.container.containers.InventoryContainer
import com.minato.task.Task
import com.minato.threading.runSafeAutomated
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand

class EatTask @Ta5kBuilder constructor(
    automated: Automated
) : Task<Unit>(), Automated by automated {
    override val name: String
        get() = reason.message(eatStack ?: ItemStack.EMPTY)

    private var eatStack: ItemStack? = null
    private var reason = EatConfig.Reason.None
    private var holdingUse = false

    override fun SafeContext.onStart() {
        reason = runSafeAutomated { reasonEating() }
    }

    init {
        listen<TickEvent.Input.Post> {
            if (holdingUse && !reason.shouldKeepEating(eatStack)) {
                mc.options.useKey.isPressed = false
                holdingUse = false
                interaction.stopUsingItem(player)
                success()
                return@listen
            }

            val foodFinder = reason.selector()
            val hotbarSlot = foodFinder.filterSlots(HotbarContainer.slots).firstOrNull()
            if (hotbarSlot != null) {
                val request = HotbarRequest(
                    hotbarSlot.index,
                    this@EatTask,
                    keepTicks = hotbarConfig.keepTicks.coerceAtLeast(1),
                    nowOrNothing = false
                ).submit()
                if (!request.done) return@listen
            } else {
                val inventorySlot = foodFinder.filterSlots(InventoryContainer.slots).firstOrNull()
                if (inventorySlot != null) runSafeAutomated {
                    InventoryContainer.transfer(foodFinder, HotbarContainer)
                }
                if (holdingUse) {
                    mc.options.useKey.isPressed = false
                    holdingUse = false
                }
                return@listen
            }

            if (player.isUsingItem) {
                if (!holdingUse) {
                    mc.options.useKey.isPressed = true
                    holdingUse = true
                }
                return@listen
            }

            eatStack = player.mainHandStack

            (interaction.interactItem(player, Hand.MAIN_HAND) as? ActionResult.Success)?.let {
                if (it.swingSource == ActionResult.SwingSource.CLIENT) player.swingHand(Hand.MAIN_HAND)
                mc.gameRenderer.firstPersonRenderer.resetEquipProgress(Hand.MAIN_HAND)
                mc.options.useKey.isPressed = true
                holdingUse = true
            }
        }
    }

    companion object {
        @Ta5kBuilder
        context(automated: Automated)
        fun eat() = EatTask(automated)
    }
}
