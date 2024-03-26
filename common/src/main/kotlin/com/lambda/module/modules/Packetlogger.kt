package com.lambda.module.modules

import com.lambda.Lambda.mc
import com.lambda.event.EventFlow.lambdaScope
import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.UnsafeListener.Companion.unsafeConcurrentListener
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import com.lambda.module.Module
import com.lambda.util.Communication.info
import com.lambda.util.DynamicReflectionSerializer.dynamicString
import com.lambda.util.FolderRegister
import com.lambda.util.Formatting.getTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import net.minecraft.network.packet.Packet
import java.io.File
import java.time.format.DateTimeFormatter

object Packetlogger : Module(
    name = "Packetlogger",
    description = "Logs packets",
    defaultTags = setOf()
) {
    private val logConcurrent by setting("Build String Concurrent", false, "Whether to serialize packets concurrently. Will not save packets in chronological order but wont lag the game.")

    private var file: File? = null
    private val entryFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSSS")
    private val fileFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss.SSSS")

    private val storageFlow = MutableSharedFlow<String>(
        extraBufferCapacity = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        lambdaScope.launch(Dispatchers.IO) {
            storageFlow.collect { entry ->
                file?.appendText(entry)
            }
        }

        onEnableUnsafe {
            val fileName = "packet-log-${getTime(fileFormatter)}.txt"
            file = FolderRegister.packetLogs.resolve(fileName).apply {
                if (!parentFile.exists()) {
                    parentFile.mkdirs()
                }
                if (!exists()) {
                    createNewFile()
                }
                this@Packetlogger.info("Logging packets to $absolutePath")
            }.apply {
                // ToDo: Add more rich and accurate data to the header
                StringBuilder().apply {
                    val playerName = mc.player?.name?.string ?: "Unknown"
                    val serverInfo = mc.networkHandler?.serverInfo?.toNbt() ?: "Singleplayer"
                    appendLine("Packet logger started at ${getTime()} by $playerName\n")
                    appendLine("Connected to $serverInfo\n")
                }.toString().let { header ->
                    storageFlow.tryEmit(header)
                }
            }
        }

        onDisableUnsafe {
            this@Packetlogger.info("Stopped logging packets. Saved to ${file?.absolutePath}")
            file = null
        }

        unsafeListener<PacketEvent.Receive.Pre> {
            if (logConcurrent) return@unsafeListener

            it.packet.logReceived()
        }

        unsafeListener<PacketEvent.Send.Pre> {
            if (logConcurrent) return@unsafeListener

            it.packet.logSent()
        }

        unsafeConcurrentListener<PacketEvent.Receive.Pre> {
            if (!logConcurrent) return@unsafeConcurrentListener

            it.packet.logReceived()
        }

        unsafeConcurrentListener<PacketEvent.Send.Pre> {
            if (!logConcurrent) return@unsafeConcurrentListener

            it.packet.logSent()
        }
    }

    private fun Packet<*>.logReceived() {
        storageFlow.tryEmit("Received at ${getTime(entryFormatter)}\n${dynamicString()}\n")
    }

    private fun Packet<*>.logSent() {
        storageFlow.tryEmit("Sent at ${getTime(entryFormatter)}\n${dynamicString()}\n")
    }
}