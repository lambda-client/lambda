/*
 * Copyright 2025 Lambda
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

package com.lambda.interaction.request.inventory

import com.lambda.context.AutomatedSafeContext
import com.lambda.context.AutomationConfig
import com.lambda.context.AutomationConfig.avoidDesync
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.post
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.request.Logger
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.inventory.InventoryManager.actions
import com.lambda.interaction.request.inventory.InventoryManager.alteredSlots
import com.lambda.interaction.request.placing.PlaceManager
import com.lambda.module.hud.ManagerDebugLoggers.inventoryManagerLogger
import com.lambda.threading.runSafe
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.item.ItemStackUtils.equal
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket
import net.minecraft.screen.PlayerScreenHandler
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot

/**
 * Manager designed to handle inventory actions. One of the key features being the inventory change detection to
 * avoid accepting old information from the server in cases where ping is high. This helps to prevent desync.
 */
object InventoryManager : RequestHandler<InventoryRequest>(
    1,
    TickEvent.Pre,
    TickEvent.Input.Pre,
    TickEvent.Input.Post,
    TickEvent.Player.Post
), Logger {
    private var actions = mutableListOf<InventoryAction>()

    private var slots = listOf<ItemStack>()
    private var alteredSlots = LimitedDecayQueue<Pair<Int, Pair<ItemStack, ItemStack>>>(
        AutomationConfig.maxDesyncCache, AutomationConfig.desyncTimeout * 50L
    )

    private var screenHandler: ScreenHandler? = null
        set(value) {
            if (value != null && field?.syncId != value.syncId)
                slots = getStacks(value.slots)
            field = value
        }

    private var maxActionsThisSecond = 0
    private var actionsThisSecond = 0
    private var secondCounter = 0
    private var actionsThisTick = 0

    override val logger = inventoryManagerLogger

    override fun load(): String {
        super.load()

        listen<TickEvent.Post>(priority = Int.MIN_VALUE) {
            if (avoidDesync) indexInventoryChanges()
            if (++secondCounter >= 20) {
                secondCounter = 0
                actionsThisSecond = 0
            }
            actionsThisTick = 0
            actions.clear()
        }

        return "Loaded Inventory Manager"
    }

    /**
     * Attempts to accept the request and perform the actions. If, for example, the tick stage isn't valid, or
     * not all the actions can be performed and [InventoryRequest.settleForLess] is set to false, the request is rejected.
     * All checks aside from tick stage are ignored if the request has [InventoryRequest.mustPerform] set to true.
     * This is typically used in dangerous situations where typical rules are worth breaking. For example, if the player
     * needs to equip a totem of undying.
     */
    override fun AutomatedSafeContext.handleRequest(request: InventoryRequest) {
        val inventoryActionCount = request.actions.count { it is InventoryAction.Inventory }
        if (inventoryActionCount >= request.inventoryConfig.actionsPerSecond - actionsThisSecond &&
            !request.settleForLess &&
            !request.mustPerform) return
        if (tickStage !in inventoryConfig.tickStageMask) return

        if (request.fresh) populateFrom(request)

        PlaceManager.logger.debug("Processing request", request)

        screenHandler = player.currentScreenHandler
        val iterator = actions.iterator()
        while (iterator.hasNext()) {
            if (actionsThisSecond + 1 > maxActionsThisSecond && !request.mustPerform) break
            iterator.next().action(this)
            if (avoidDesync) indexInventoryChanges()
            actionsThisTick++
            actionsThisSecond++
            iterator.remove()
        }

        if (actions.isEmpty()) {
            request.done = true
            request.onComplete?.invoke(this)
        }

        if (actionsThisTick > 0) activeThisTick = true
    }

    /**
     * Populates the [actions] collection and sets some configurations.
     */
    private fun populateFrom(request: InventoryRequest) {
        PlaceManager.logger.debug("Populating from request", request)
        actions = request.actions.toMutableList()
        maxActionsThisSecond = request.inventoryConfig.actionsPerSecond
        alteredSlots.setDecayTime(AutomationConfig.desyncTimeout * 50L)
    }

    /**
     * Detects changes in item stacks between now and the last time slots were cached and
     * adds them all to the [alteredSlots] collection where they can be compared to
     * incoming [InventoryS2CPacket] and [ScreenHandlerSlotUpdateS2CPacket] packets to decide whether to
     * block certain updates.
     */
    private fun SafeContext.indexInventoryChanges() {
        if (player.currentScreenHandler.syncId != screenHandler?.syncId) return
        val changes = screenHandler?.slots
            ?.filter { !it.stack.equal(slots[it.id]) }
            ?.map { Pair(it.id, Pair(slots[it.id], it.stack.copy())) }
            ?: emptyList()
        alteredSlots.addAll(changes)
        slots = getStacks(player.currentScreenHandler.slots)
    }

    private fun getStacks(slots: Collection<Slot>) = slots.map { it.stack.copy() }

    /**
     * A modified version of the minecraft onInventory method
     *
     * @see net.minecraft.client.network.ClientPlayNetworkHandler.onInventory
     */
    @JvmStatic
    fun onInventoryUpdate(packet: InventoryS2CPacket, original: Operation<Void>){
        runSafe {
            if (!mc.isOnThread || !avoidDesync) {
                original.call(packet)
                return
            }
            screenHandler = player.currentScreenHandler
            val packetScreenHandler =
                when (packet.syncId) {
                    0 -> player.playerScreenHandler
                    screenHandler?.syncId -> player.currentScreenHandler
                    else -> return@runSafe
                }
            val alteredContents = mutableListOf<ItemStack>()
            packet.contents.forEachIndexed { index, incomingStack ->
                val matches = alteredSlots.removeIf { cached ->
                    incomingStack.equal(cached.second.second)
                }
                if (matches) alteredContents.add(packetScreenHandler.slots[index].stack)
                else alteredContents.add(incomingStack)
            }
            packetScreenHandler.updateSlotStacks(packet.revision(), alteredContents, packet.cursorStack())
            return
        }
        original.call(packet)
    }

    /**
     * A modified version of the vanilla [net.minecraft.client.network.ClientPlayNetworkHandler.onScreenHandlerSlotUpdate] method
     */
    @JvmStatic
    fun onSlotUpdate(packet: ScreenHandlerSlotUpdateS2CPacket, original: Operation<Void>) {
        runSafe {
            screenHandler = player.currentScreenHandler
            if (!mc.isOnThread || !avoidDesync) {
                original.call(packet)
                return
            }
            val itemStack = packet.stack
            mc.tutorialManager.onSlotUpdate(itemStack)

            val bl = (mc.currentScreen as? CreativeInventoryScreen)?.let {
                !it.isInventoryTabSelected
            } ?: false

            val matches = alteredSlots.removeIf {
                it.first == packet.slot && it.second.second.equal(itemStack)
            }

            if (packet.syncId == 0) {
                if (PlayerScreenHandler.isInHotbar(packet.slot) && !itemStack.isEmpty) {
                    val itemStack2 = player.playerScreenHandler.getSlot(packet.slot).stack
                    if (itemStack2.isEmpty || itemStack2.count < itemStack.count) {
                        itemStack.bobbingAnimationTime = 5
                    }
                }

                if (!matches) player.playerScreenHandler.setStackInSlot(packet.slot, packet.revision, itemStack)
            } else if (packet.syncId == player.currentScreenHandler.syncId && (packet.syncId != 0 || !bl))
                if (!matches) player.currentScreenHandler.setStackInSlot(packet.slot, packet.revision, itemStack)

            if (mc.currentScreen is CreativeInventoryScreen) {
                player.playerScreenHandler.setReceivedStack(packet.slot, itemStack)
                player.playerScreenHandler.sendContentUpdates()
            }
            return
        }
        original.call(packet)
    }

    override fun preEvent() = UpdateManagerEvent.Inventory.post()
}
