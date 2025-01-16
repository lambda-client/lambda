/*
 * Copyright 2024 Lambda
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

package com.lambda.module.modules.debug

import com.lambda.Lambda.LOG
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Communication.info
import com.lambda.util.DynamicReflectionSerializer.dynamicString
import net.minecraft.network.packet.c2s.play.*
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket

object InventoryDebug : Module(
    name = "InventoryDebug",
    description = "Debugs the inventory",
    defaultTags = setOf(ModuleTag.DEBUG)
) {
    init {
        listen<InventoryEvent.Open> { event ->
            info("Opened screen handler: ${event.screenHandler::class.simpleName}")

            LOG.info("\n" + event.screenHandler.slots.joinToString("\n") {
                "${it.inventory::class.simpleName} ${it.index} ${it.x} ${it.y}"
            })
        }

        listen<InventoryEvent.Close> {
            info("Closed screen handler: ${it.screenHandler::class.simpleName}")
        }

        listen<InventoryEvent.FullUpdate> {
            info("Updated screen handler: ${it.revision}, ${it.stacks}, ${it.cursorStack}")
        }

        listen<PacketEvent.Receive.Pre> {
            when (it.packet) {
                is UpdateSelectedSlotS2CPacket,
                is InventoryS2CPacket,
                    -> {
                    LOG.info(it.packet.dynamicString())
                }
            }
            when (val packet = it.packet) {
                is UpdateSelectedSlotS2CPacket -> this@InventoryDebug.info("Updated selected slot: ${packet.slot}")
                is InventoryS2CPacket -> this@InventoryDebug.info("Received inventory update: syncId: ${packet.syncId} | revision: ${packet.revision} | cursorStack ${packet.cursorStack}")
            }
        }

        listen<PacketEvent.Send.Pre> {
            when (it.packet) {
                is SlotChangedStateC2SPacket,
                is ClickSlotC2SPacket,
                is CloseHandledScreenC2SPacket,
                is CraftRequestC2SPacket,
                is CreativeInventoryActionC2SPacket,
                is PickFromInventoryC2SPacket,
                is UpdateSelectedSlotC2SPacket,
                    -> LOG.info(System.currentTimeMillis().toString() + " " + it.packet.dynamicString())
            }
        }
    }
}
