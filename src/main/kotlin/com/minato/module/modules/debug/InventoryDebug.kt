
package com.minato.module.modules.debug

import com.minato.Minato.LOG
import com.minato.event.events.InventoryEvent
import com.minato.event.events.PacketEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.CommunicationUtils.info
import com.minato.util.DynamicReflectionSerializer.dynamicString
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket
import net.minecraft.network.packet.c2s.play.CraftRequestC2SPacket
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket
import net.minecraft.network.packet.c2s.play.SlotChangedStateC2SPacket
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket

@Suppress("unused")
object InventoryDebug : Module(
    name = "InventoryDebug",
    description = "Debugs the inventory",
    tag = ModuleTag.DEBUG,
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
                is UpdateSelectedSlotC2SPacket,
                    -> LOG.info(System.currentTimeMillis().toString() + " " + it.packet.dynamicString())
            }
        }
    }
}
