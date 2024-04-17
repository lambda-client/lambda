package com.lambda.module.modules.render

import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.util.Communication.info
import com.lambda.util.DynamicReflectionSerializer.dynamicString
import net.minecraft.network.packet.c2s.play.*
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket

object InventoryDebug : Module(
    name = "InventoryDebug",
    description = "Debugs the inventory",
    defaultTags = setOf()
) {
    init {
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
                is UpdateSelectedSlotC2SPacket -> {
                    this@InventoryDebug.info(it.packet.dynamicString())
                }
            }
        }
    }
}