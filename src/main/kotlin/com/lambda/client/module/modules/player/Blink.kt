package com.lambda.client.module.modules.player

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.mixin.extension.playerX
import com.lambda.client.mixin.extension.playerY
import com.lambda.client.mixin.extension.playerZ
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.entity.EntityOtherPlayerMP
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*

object Blink : Module(
    name = "Blink",
    description = "Cancels server side packets",
    category = Category.PLAYER
) {
    private val cancelPacket by setting("Cancel Packets", false)
    private val autoReset by setting("Auto Reset", true)
    private val resetThreshold by setting("Reset Threshold", 20, 1..100, 5, { autoReset })

    private const val ENTITY_ID = -114514
    private val packets = ArrayDeque<CPacketPlayer>()
    private var clonedPlayer: EntityOtherPlayerMP? = null
    private var sending = false

    init {
        onEnable {
            runSafe {
                begin()
            }
        }

        onDisable {
            end()
        }

        listener<PacketEvent.Send> {
            if (!sending && it.packet is CPacketPlayer) {
                it.cancel()
                packets.add(it.packet)
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.END) return@safeListener
            if (autoReset && packets.size >= resetThreshold) {
                end()
                begin()
            }
        }

        listener<ConnectionEvent.Disconnect> {
            mc.addScheduledTask {
                packets.clear()
                clonedPlayer = null
            }
        }
    }

    private fun SafeClientEvent.begin() {
        clonedPlayer = EntityOtherPlayerMP(mc.world, mc.session.profile).apply {
            copyLocationAndAnglesFrom(mc.player)
            rotationYawHead = mc.player.rotationYawHead
            inventory.copyInventory(mc.player.inventory)
            noClip = true
        }.also {
            mc.world.addEntityToWorld(ENTITY_ID, it)
        }
    }

    private fun end() {
        mc.addScheduledTask {
            runSafe {
                if (cancelPacket) {
                    packets.peek()?.let { player.setPosition(it.playerX, it.playerY, it.playerZ) }
                    packets.clear()
                } else {
                    sending = true
                    while (packets.isNotEmpty()) connection.sendPacket(packets.poll())
                    sending = false
                }

                clonedPlayer?.setDead()
                world.removeEntityFromWorld(ENTITY_ID)
                clonedPlayer = null
            }

            packets.clear()
        }
    }

    override fun getHudInfo(): String {
        return packets.size.toString()
    }
}