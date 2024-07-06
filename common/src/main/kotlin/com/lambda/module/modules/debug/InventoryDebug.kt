package com.lambda.module.modules.debug

import com.lambda.Lambda.LOG
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.ScreenHandlerEvent
import com.lambda.event.listener.SafeListener.Companion.listener
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
        listener<ScreenHandlerEvent.Open> {
            info("Opened screen handler: ${it.screenHandler::class.simpleName}")
        }

        listener<ScreenHandlerEvent.Close> {
            info("Closed screen handler: ${it.screenHandler::class.simpleName}")
        }

        listener<ScreenHandlerEvent.Update> {
            info("Updated screen handler: ${it.revision}, ${it.stacks}, ${it.cursorStack}")
        }

        listener<PacketEvent.Receive.Pre> {
            when (val packet = it.packet) {
                is UpdateSelectedSlotS2CPacket, is InventoryS2CPacket -> {
                    this@InventoryDebug.info(packet.dynamicString())
                }
            }
        }

        listener<PacketEvent.Send.Pre> {
            when (it.packet) {
                is SlotChangedStateC2SPacket,
                is ClickSlotC2SPacket,
                is CloseHandledScreenC2SPacket,
                is CraftRequestC2SPacket,
                is CreativeInventoryActionC2SPacket,
                is PickFromInventoryC2SPacket,
                is UpdateSelectedSlotC2SPacket,
                -> LOG.info(it.packet.dynamicString())
            }
        }
    }
}