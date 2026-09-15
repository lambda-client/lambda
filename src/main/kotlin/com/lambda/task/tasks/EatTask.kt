/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.task.tasks

import com.lambda.config.blocks.EatConfig
import com.lambda.config.blocks.EatConfig.Companion.reasonEating
import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.container.containers.HotbarContainer
import com.lambda.interaction.container.containers.InventoryContainer
import com.lambda.interaction.container.selection.select
import com.lambda.interaction.manager.managers.hotbar.HotbarRequestBuilder.Companion.hotbarRequest
import com.lambda.task.Task
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.threading.runSafeAutomated
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand

@Ta5kBuilder
context(automated: Automated)
fun eat() = EatTask(automated)

class EatTask @Ta5kBuilder internal constructor(
    automated: Automated
) : Task<Unit>(), Automated by automated {
    override val name: String
        get() = reason.message(eatStack ?: ItemStack.EMPTY)

    private var eatStack: ItemStack? = null
    private var reason = EatConfig.Reason.None
    private var holdingUse = false
    private var activeTransfer: ContainerTransferTask? = null
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

            val selection = reason.selector()
            val hotbarSlot = selection.bestMatch(HotbarContainer.slots)
            if (hotbarSlot != null) {
                val request =
                    hotbarRequest(hotbarSlot.index) {
                        keepTicks(hotbarConfig.keepTicks.coerceAtLeast(1))
                    }.submit()
                if (!request.done) return@listen
            } else {
                if (InventoryContainer.slots.any { selection.matches(it) }) {
                    runSafeAutomated {
                        if (activeTransfer == null || activeTransfer?.state in listOf(Task.State.Completed, Task.State.Failed, Task.State.Cancelled)) {
                            activeTransfer = transfer(
                                selection,
                                InventoryContainer.select(),
                                HotbarContainer.select()
                            ).apply {
                                onSuccess { activeTransfer = null }
                                onFailure { activeTransfer = null }
                                start()
                            }
                        }
                        return@listen
                    }
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
}
