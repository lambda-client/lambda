package com.lambda.client.buildtools

import com.lambda.client.buildtools.task.TaskProcessor
import com.lambda.client.buildtools.task.build.BreakTask
import com.lambda.client.buildtools.task.build.PlaceTask
import com.lambda.client.buildtools.task.build.RestockTask
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.items.hotbarSlots
import net.minecraft.network.Packet
import net.minecraft.network.play.server.*

object PacketReceiver {
    val rubberbandTimer = TickTimer(TimeUnit.TICKS)

    fun SafeClientEvent.handlePacket(packet: Packet<*>) {
        when (packet) {
            is SPacketBlockChange -> {
                TaskProcessor.tasks[packet.blockPosition]?.let {
                    with(it) {
                        if (currentBlockState != packet.getBlockState()) {
                            when (it) {
                                is PlaceTask -> it.acceptPacketState(packet.getBlockState())
                                is BreakTask -> it.acceptPacketState(packet.getBlockState())
                            }
                        }
                    }
                }
            }
            is SPacketPlayerPosLook -> {
                rubberbandTimer.reset()
            }
            is SPacketOpenWindow -> {
                TaskProcessor.getContainerTasks().filterIsInstance<RestockTask>().forEach {
                    with(it) {
                        acceptPacketOpen(packet)
                    }
                }
            }
            is SPacketWindowItems -> {
                TaskProcessor.getContainerTasks().filterIsInstance<RestockTask>().forEach {
                    with(it) {
                        acceptPacketLoaded()
                    }
                }
            }
            is SPacketSetSlot -> {
                val currentStack = player.hotbarSlots[player.inventory.currentItem].stack
                if (packet.slot == player.inventory.currentItem + 36
                    && packet.stack.item == currentStack.item
                    && packet.stack.itemDamage > currentStack.itemDamage
                ) {
                    Statistics.durabilityUsages += packet.stack.itemDamage - currentStack.itemDamage
                }
            }
            else -> { /* Ignored */
            }
        }
    }
}