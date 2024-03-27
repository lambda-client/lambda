package com.lambda.module.modules

import com.lambda.Lambda
import com.lambda.Lambda.mc
import com.lambda.event.EventFlow.lambdaScope
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.UnsafeListener.Companion.unsafeConcurrentListener
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import com.lambda.module.Module
import com.lambda.util.Communication
import com.lambda.util.Communication.info
import com.lambda.util.DynamicReflectionSerializer.dynamicString
import com.lambda.util.FolderRegister
import com.lambda.util.Formatting.getTime
import com.lambda.util.text.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import net.minecraft.network.packet.Packet
import java.io.File
import java.nio.file.Path
import java.time.format.DateTimeFormatter
import kotlin.io.path.pathString

object Packetlogger : Module(
    name = "Packetlogger",
    description = "Logs packets",
    defaultTags = setOf()
) {
    private val logIncoming by setting("Log Incoming", true, "Log incoming packets")
    private val logOutgoing by setting("Log Outgoing", true, "Log outgoing packets")
    private val logTicks by setting("Log Ticks", true, "Show game ticks in the log")
    private val logWhitelist by setting("Whitelist", false, "Only log packets from the whitelist")
    private val whitelist by setting("Whitelist Packets", emptyList<String>(), "Packets to whitelist")
    private val logBlacklist by setting("Blacklist", false, "Log all packets except those from the blacklist")
    private val blacklist by setting("Blacklist Packets", emptyList<String>(), "Packets to blacklist")
    private val maxRecursionDepth by setting("Max Recursion Depth", 6, 1..10, 1, "Maximum recursion depth for packet serialization")
    private val logConcurrent by setting("Build Data Concurrent", false, "Whether to serialize packets concurrently. Will not save packets in chronological order but wont lag the game.")

    private var file: File? = null
    private val entryFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSSS")
    private val fileFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss.SSSS")

    private val storageFlow = MutableSharedFlow<String>(
        extraBufferCapacity = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val File.relativePath: Path get() = mc.runDirectory.toPath().relativize(toPath())

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
                val info = buildText {
                    clickEvent(ClickEvents.openFile(relativePath.pathString)) {
                        literal("Packet logger started: ")
                        color(Color.GOLD) { literal(fileName) }
                        literal(" (click to open)")
                    }
                }
                this@Packetlogger.info(info)
            }.apply {
                // ToDo: Add more rich and accurate data to the header
                StringBuilder().apply {
                    appendLine(Communication.ascii)
                    appendLine("${Lambda.SYMBOL} - Lambda ${Lambda.VERSION} - Packet Log")

                    val playerName = mc.player?.name?.string ?: "Unknown"
                    appendLine("Started at ${getTime()} by $playerName")
                    when {
                        mc.isIntegratedServerRunning -> {
                            appendLine("Integrated server running.")
                        }
                        mc.currentServerEntry != null -> {
                            appendLine("Connected to ${mc.currentServerEntry?.name} at ${mc.currentServerEntry?.address}.")
                        }
                        else -> {
                            appendLine("Started in Main Menu")
                        }
                    }
                    appendLine()
                }.toString().let { header ->
                    storageFlow.tryEmit(header)
                }
            }
        }

        onDisableUnsafe {
            file?.let {
                val info = buildText {
                    literal("Stopped logging packets to ")
                    clickEvent(ClickEvents.openFile(it.relativePath.pathString)) {
                        color(Color.GOLD) { literal(it.relativePath.pathString) }
                        literal(" (click to open)")
                    }
                }
                this@Packetlogger.info(info)

                file = null
            }
        }

        unsafeListener<TickEvent.Pre> {
            if (!logTicks) return@unsafeListener

            storageFlow.tryEmit("\nStarted tick at ${getTime(entryFormatter)}\n")
        }

        unsafeListener<PacketEvent.Receive.Pre> {
            if (logConcurrent || !logIncoming || it.packet.notLog()) return@unsafeListener

            it.packet.logReceived()
        }

        unsafeListener<PacketEvent.Send.Pre> {
            if (logConcurrent || !logOutgoing || it.packet.notLog()) return@unsafeListener

            it.packet.logSent()
        }

        unsafeConcurrentListener<PacketEvent.Receive.Pre> {
            if (!logConcurrent || !logIncoming || it.packet.notLog()) return@unsafeConcurrentListener

            it.packet.logReceived()
        }

        unsafeConcurrentListener<PacketEvent.Send.Pre> {
            if (!logConcurrent || !logOutgoing || it.packet.notLog()) return@unsafeConcurrentListener

            it.packet.logSent()
        }
    }

    private fun Packet<*>.logReceived() {
        storageFlow.tryEmit("Received at ${getTime(entryFormatter)}\n${dynamicString(maxRecursionDepth)}\n")
    }

    private fun Packet<*>.logSent() {
        storageFlow.tryEmit("Sent at ${getTime(entryFormatter)}\n${dynamicString(maxRecursionDepth)}\n")
    }

    private fun Packet<*>.notLog() =
        (logWhitelist && this::class.simpleName !in whitelist)
                || (logBlacklist && this::class.simpleName in blacklist)
}