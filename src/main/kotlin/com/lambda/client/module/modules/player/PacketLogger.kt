package com.lambda.client.module.modules.player

import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.mixin.extension.*
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.safeListener
import com.lambda.commons.interfaces.DisplayEnum
import com.lambda.event.listener.listener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.network.Packet
import net.minecraft.network.play.client.*
import net.minecraft.network.play.server.*
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.io.File
import java.io.FileWriter
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal object PacketLogger : Module(
    name = "PacketLogger",
    description = "Logs sent packets to a file",
    category = Category.PLAYER
) {
    private val showClientTicks by setting("Show Client Ticks", true, description = "Show timestamps of client ticks.")
    private val logInChat by setting("Log In Chat", false, description = "Print packets in the chat.")
    private val captureTiming by setting("Capture Timing", CaptureTiming.POST, description = "Sets point of time for scan event.")
    private val packetSide by setting("Packet Side", PacketSide.BOTH, description = "Log packets from the server, from the client, or both.")
    private val ignoreKeepAlive by setting("Ignore Keep Alive", true, description = "Ignore both incoming and outgoing KeepAlive packets.")
    private val ignoreChunkLoading by setting("Ignore Chunk Loading", true, description = "Ignore chunk loading and unloading packets.")
    private val ignoreUnknown by setting("Ignore Unknown Packets", false, description = "Ignore packets that aren't explicitly handled.")
    private val ignoreChat by setting("Ignore Chat", true, description = "Ignore chat packets.")
    private val ignoreCancelled by setting("Ignore Cancelled", true, description = "Ignore cancelled packets.")

    private val fileTimeFormatter = DateTimeFormatter.ofPattern("HH-mm-ss_SSS")

    private var start = 0L
    private var last = 0L
    private var lastTick = 0L
    private val timer = TickTimer(TimeUnit.SECONDS)

    private const val directory = "${com.lambda.client.LambdaMod.DIRECTORY}packetLogs"
    private var filename = ""
    private var lines = ArrayList<String>()

    private enum class PacketSide(override val displayName: String) : DisplayEnum {
        CLIENT("Client"),
        SERVER("Server"),
        BOTH("Both")
    }

    private enum class CaptureTiming {
        PRE, POST
    }

    init {
        onEnable {
            start = System.currentTimeMillis()
            filename = "${fileTimeFormatter.format(LocalTime.now())}.csv"

            synchronized(this) {
                lines.add("From,Packet Name,Time Since Start (ms),Time Since Last (ms),Data\n")
            }
        }

        onDisable {
            write()
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            if (showClientTicks) {
                synchronized(this@PacketLogger) {
                    val current = System.currentTimeMillis()
                    lines.add("Tick Pulse,,${current - start},${current - lastTick}\n")
                    lastTick = current
                }
            }

            /* Don't let lines get too big, write periodically to the file */
            if (lines.size >= 500 || timer.tick(15L)) {
                write()
            }
        }

        safeListener<ConnectionEvent.Disconnect> {
            disable()
        }

        listener<PacketEvent.Receive>(Int.MAX_VALUE) {
            if (captureTiming != CaptureTiming.PRE || ignoreCancelled && it.cancelled) {
                return@listener
            }

            receivePacket(it.packet)
        }

        listener<PacketEvent.PostReceive>(Int.MIN_VALUE) {
            if (captureTiming != CaptureTiming.POST || ignoreCancelled && it.cancelled) {
                return@listener
            }

            receivePacket(it.packet)
        }

        listener<PacketEvent.Send>(Int.MAX_VALUE) {
            if (captureTiming != CaptureTiming.PRE || ignoreCancelled && it.cancelled) {
                return@listener
            }

            sendPacket(it.packet)
        }

        listener<PacketEvent.PostSend>(Int.MIN_VALUE) {
            if (captureTiming != CaptureTiming.POST || ignoreCancelled && it.cancelled) {
                return@listener
            }

            sendPacket(it.packet)
        }
    }

    private fun receivePacket(packet: Packet<*>) {
        if (packetSide == PacketSide.SERVER || packetSide == PacketSide.BOTH) {
            logServer(packet) {}
        }
    }

    private fun sendPacket(packet: Packet<*>) {
        if (packetSide == PacketSide.CLIENT || packetSide == PacketSide.BOTH) {
            logClient(packet) {}
        }
    }

    private fun write() {
        val lines = synchronized(this) {
            val cache = lines
            lines = ArrayList()
            cache
        }

        defaultScope.launch(Dispatchers.IO) {
            try {
                with(File(directory)) {
                    if (!exists()) mkdir()
                }

                FileWriter("$directory/${filename}", true).buffered().use {
                    for (line in lines) it.write(line)
                }
            } catch (e: Exception) {
                com.lambda.client.LambdaMod.LOG.warn("$chatName Failed saving packet log!", e)
            }
        }
    }

    private inline fun logClient(packet: Packet<*>, block: PacketLogBuilder.() -> Unit) {
        PacketLogBuilder(PacketSide.CLIENT, packet).apply(block).build()
    }

    private inline fun logServer(packet: Packet<*>, block: PacketLogBuilder.() -> Unit) {
        PacketLogBuilder(PacketSide.SERVER, packet).apply(block).build()
    }

    private class PacketLogBuilder(val side: PacketSide, val packet: Packet<*>) {
        private val stringBuilder = StringBuilder()

        init {
            stringBuilder.apply {
                append(side.displayName)
                append(',')

                append(packet.javaClass.simpleName)
                append(',')

                append(System.currentTimeMillis() - start)
                append(',')

                append(System.currentTimeMillis() - last)
                append(',')

                // Get all fields using reflection
                // First we get all the fields from the class, then get the parent class
                // Repeat until no more fields are available
                var clazz: Class<*>? = packet.javaClass
                while (clazz != null) {
                    for (field in packet.javaClass.declaredFields) {
                        field.isAccessible = true
                        try {
                            append(field.name + ": " + field.get(packet).toString().replace(",", " ") + " ")
                        } catch (e: NullPointerException) {
                            /* In this case the field was not set, so this is expected */
                        }
                    }
                    clazz = clazz.superclass
                }
            }
        }

        operator fun String.unaryPlus() {
            stringBuilder.append(this)
        }

        infix fun String.to(value: Any?) {
            if (value != null) {
                add(this, value.toString())
            }
        }

        infix fun String.to(value: String?) {
            if (value != null) {
                add(this, value)
            }
        }

        fun add(key: String, value: String) {
            stringBuilder.apply {
                append(key)
                append(": ")
                append(value)
                append(' ')
            }
        }

        fun build() {
            val string = stringBuilder.run {
                append('\n')
                toString()
            }

            synchronized(PacketLogger) {
                lines.add(string)
                last = System.currentTimeMillis()
            }

            if (logInChat) {
                MessageSendHelper.sendChatMessage(string)
            }
        }
    }
}
