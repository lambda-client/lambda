package com.lambda.module.modules

import com.lambda.Lambda
import com.lambda.Lambda.mc
import com.lambda.event.EventFlow.lambdaScope
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.UnsafeListener.Companion.unsafeConcurrentListener
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
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
import java.awt.Color
import java.io.File
import java.nio.file.Path
import java.time.format.DateTimeFormatter
import kotlin.io.path.pathString

object Packetlogger : Module(
    name = "Packetlogger",
    description = "Serializes network traffic and persists it for later analysis",
    defaultTags = setOf(ModuleTag.DEBUG)
) {
    private val logToChat by setting("Log To Chat", false, "Log packets to chat")
    // ToDo: Implement HUD logging when HUD is done
//    private val logToHUD by setting("Log To HUD", false, "Log packets to HUD")
    private val networkSide by setting("Network Side", NetworkSide.ANY, "Side of the network to log packets from")
    private val logTicks by setting("Log Ticks", true, "Show game ticks in the log")
    private val scope by setting("Scope", Scope.ANY, "Scope of packets to log")
    private val whitelist by setting("Whitelist Packets", emptyList<String>(), "Packets to whitelist") { scope == Scope.WHITELIST }
    private val blacklist by setting("Blacklist Packets", emptyList<String>(), "Packets to blacklist") { scope == Scope.BLACKLIST }
    private val maxRecursionDepth by setting("Max Recursion Depth", 6, 1..10, 1, "Maximum recursion depth for packet serialization")
    private val logConcurrent by setting("Build Data Concurrent", false, "Whether to serialize packets concurrently. Will not save packets in chronological order but wont lag the game.")

    private var file: File? = null
    private val entryFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSSS")
    private val fileFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss.SSSS")

    enum class NetworkSide {
        ANY, CLIENT, SERVER;

        fun shouldLog(networkSide: NetworkSide) =
            this == ANY || this == networkSide
    }

    enum class Scope {
        ANY, WHITELIST, BLACKLIST;

        fun shouldLog(packet: Packet<*>) = when (this) {
            ANY -> true
            WHITELIST -> packet::class.simpleName in whitelist
            BLACKLIST -> packet::class.simpleName !in blacklist
        }
    }

    private val storageFlow = MutableSharedFlow<String>(
        extraBufferCapacity = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val File.relativePath: Path get() = mc.runDirectory.toPath().relativize(toPath())

    init {
        lambdaScope.launch(Dispatchers.IO) {
            storageFlow.collect { entry ->
                file?.appendText(entry)
                if (logToChat) this@Packetlogger.info(entry)
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
                        color(Color.YELLOW) { literal(fileName) }
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
                        color(Color.YELLOW) { literal(it.relativePath.pathString) }
                        literal(" (click to open)")
                    }
                }
                this@Packetlogger.info(info)

                file = null
            }
        }

        unsafeListener<TickEvent.Pre> {
            if (!logTicks) return@unsafeListener

            storageFlow.tryEmit("Started tick at ${getTime(entryFormatter)}\n\n")
        }

        unsafeListener<PacketEvent.Receive.Pre> {
            if (logConcurrent
                || !scope.shouldLog(it.packet)
                || !networkSide.shouldLog(NetworkSide.SERVER)
            ) return@unsafeListener

            it.packet.logReceived()
        }

        unsafeListener<PacketEvent.Send.Pre> {
            if (logConcurrent
                || !scope.shouldLog(it.packet)
                || !networkSide.shouldLog(NetworkSide.CLIENT)
            ) return@unsafeListener


            it.packet.logSent()
        }

        unsafeConcurrentListener<PacketEvent.Receive.Pre> {
            if (!logConcurrent
                || !scope.shouldLog(it.packet)
                || !networkSide.shouldLog(NetworkSide.SERVER)
            ) return@unsafeConcurrentListener

            it.packet.logReceived()
        }

        unsafeConcurrentListener<PacketEvent.Send.Pre> {
            if (!logConcurrent
                || !scope.shouldLog(it.packet)
                || !networkSide.shouldLog(NetworkSide.CLIENT)
            ) return@unsafeConcurrentListener

            it.packet.logSent()
        }
    }

    private fun Packet<*>.logReceived() {
        storageFlow.tryEmit("Received at ${getTime(entryFormatter)}\n${dynamicString(maxRecursionDepth)}\n")
    }

    private fun Packet<*>.logSent() {
        storageFlow.tryEmit("Sent at ${getTime(entryFormatter)}\n${dynamicString(maxRecursionDepth)}\n")
    }
}