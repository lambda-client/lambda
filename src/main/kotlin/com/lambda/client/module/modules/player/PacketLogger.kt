package com.lambda.client.module.modules.player

import com.lambda.client.LambdaMod
import com.lambda.client.commons.interfaces.DisplayEnum
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.mixin.extension.*
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.FolderUtils
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.minecraft.network.Packet
import net.minecraft.network.play.client.*
import net.minecraft.network.play.server.*
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextFormatting
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.io.File
import java.io.FileWriter
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object PacketLogger : Module(
    name = "PacketLogger",
    description = "Logs sent packets to a file or chat",
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
    private val ignorePlayerPosition by setting("Ignore Player Position", false, description = "Ignore sent position & rotation packets.")
    private val ignoreTimeUpdates by setting("Ignore Time Updates", false, description = "Ignore time update packets.")
    private val openLogFolder by setting("Open Log Folder...", false, consumer = { _, _ ->
        FolderUtils.openFolder(FolderUtils.packetLogFolder)
        false
    })

    private val fileTimeFormatter = DateTimeFormatter.ofPattern("HH-mm-ss_SSS")

    private var start = 0L
    private var last = 0L
    private var lastTick = 0L
    private val timer = TickTimer(TimeUnit.SECONDS)

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

            runSafe {
                MessageSendHelper.sendChatMessage("$chatName Log saved at ${TextFormatting.GREEN}${FolderUtils.packetLogFolder}${filename}")
            }
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
            when (packet) {
                is SPacketAdvancementInfo -> {
                    logServer(packet) {
                        "isFirstSync" to packet.isFirstSync
                        "advancementsToAdd" to buildString {
                            for (entry in packet.advancementsToAdd) {
                                append("> ")

                                append(" key: ")
                                append(entry.key)

                                append(" value: ")
                                append(entry.value)

                                append(' ')
                            }
                        }
                        "advancementsToRemove" to buildString {
                            for (entry in packet.advancementsToRemove) {
                                append("> path: ")
                                append(entry.path)
                                append(", namespace:")
                                append(entry.namespace)
                                append(' ')
                            }
                        }
                        "progressUpdates" to buildString {
                            for (entry in packet.progressUpdates) {
                                append("> ")

                                append(" key: ")
                                append(entry.key)

                                append(" value: ")
                                append(entry.value)

                                append(' ')
                            }
                        }
                    }
                }
                is SPacketAnimation -> {
                    "entityID" to packet.entityID
                    "animationType" to packet.animationType
                }
                is SPacketBlockAction -> {
                    "blockPosition" to packet.blockPosition
                    "instrument" to packet.data1
                    "pitch" to packet.data2
                    "blockType" to packet.blockType
                }
                is SPacketBlockBreakAnim -> {
                    "breakerId" to packet.breakerId
                    "position" to packet.position
                    "progress" to packet.progress
                }
                is SPacketBlockChange -> {
                    logServer(packet) {
                        "blockPosition" to packet.blockPosition
                        "block" to packet.blockState.block.localizedName
                    }
                }
                is SPacketCamera -> {
                    "entityId" to packet.entityId
                }
                is SPacketChangeGameState -> {
                    "value" to packet.value
                    "gameState" to packet.gameState
                }
                is SPacketChat -> {
                    if (!ignoreChat) {
                        logServer(packet) {
                            "unformattedText" to packet.chatComponent.unformattedText
                            "type" to packet.type
                            "itSystem" to packet.isSystem
                        }
                    }
                }
                is SPacketChunkData -> {
                    logServer(packet) {
                        "chunkX" to packet.chunkX
                        "chunkZ" to packet.chunkZ
                        "extractedSize" to packet.extractedSize
                    }
                }
                is SPacketCloseWindow -> {
                    logServer(packet) {
                        +"Close current window."
                    }
                }
                is SPacketCollectItem -> {
                    logServer(packet) {
                        "amount" to packet.amount
                        "collectedItemEntityID" to packet.collectedItemEntityID
                        "entityID" to packet.entityID
                    }
                }
                is SPacketCombatEvent -> {
                    logServer(packet) {
                        "eventType" to packet.eventType.name
                        "playerId" to packet.playerId
                        "entityId" to packet.entityId
                        "duration" to packet.duration
                        "deathMessage" to packet.deathMessage.unformattedText
                    }
                }
                is SPacketConfirmTransaction -> {
                    logServer(packet) {
                        "windowId" to packet.windowId
                        "transactionID" to packet.actionNumber
                        "accepted" to packet.wasAccepted()
                    }
                }
                is SPacketCooldown -> {
                    logServer(packet) {
                        "item" to packet.item.registryName
                        "ticks" to packet.ticks
                    }
                }
                is SPacketCustomPayload -> {
                    logServer(packet) {
                        "channelName" to packet.channelName
                        "bufferData" to packet.bufferData
                    }
                }
                is SPacketCustomSound -> {
                    logServer(packet) {
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "pitch" to packet.pitch
                        "category" to packet.category.name
                        "soundName" to packet.soundName
                        "volume" to packet.volume
                    }
                }
                is SPacketDestroyEntities -> {
                    logServer(packet) {
                        "entityIDs" to buildString {
                            for (entry in packet.entityIDs) {
                                append("> ")
                                append(entry)
                                append(' ')
                            }
                        }
                    }
                }
                is SPacketDisconnect -> {
                    logServer(packet) {
                        "reason" to packet.reason.unformattedText
                    }
                }
                is SPacketDisplayObjective -> {
                    logServer(packet) {
                        "position" to packet.position
                        "name" to packet.name
                    }
                }
                is SPacketEffect -> {
                    logServer(packet) {
                        "soundData" to packet.soundData
                        "soundPos" to packet.soundPos
                        "soundType" to packet.soundType
                        "isSoundServerwide" to packet.isSoundServerwide
                    }
                }
                is SPacketEntity.S15PacketEntityRelMove -> {
                    logServer(packet) {
                        "entityId" to packet.entityId
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "onGround" to packet.onGround
                    }
                }
                is SPacketEntity.S16PacketEntityLook -> {
                    logServer(packet) {
                        "entityId" to packet.entityId
                        "yaw" to packet.yaw
                        "pitch" to packet.pitch
                        "isRotating" to packet.isRotating
                        "onGround" to packet.onGround
                    }
                }
                is SPacketEntity.S17PacketEntityLookMove -> {
                    logServer(packet) {
                        "entityId" to packet.entityId
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "yaw" to packet.yaw
                        "pitch" to packet.pitch
                        "isRotating" to packet.isRotating
                        "onGround" to packet.onGround
                    }
                }
                is SPacketEntity -> {
                    logServer(packet) {
                        "entityId" to packet.entityId
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "yaw" to packet.yaw
                        "pitch" to packet.pitch
                        "isRotating" to packet.isRotating
                        "onGround" to packet.onGround
                    }
                }
                is SPacketEntityAttach -> {
                    logServer(packet) {
                        "entityId" to packet.entityId
                        "vehicleEntityId" to packet.vehicleEntityId
                    }
                }
                is SPacketEntityEffect -> {
                    logServer(packet) {
                        "entityId" to packet.entityId
                        "duration" to packet.duration
                        "amplifier" to packet.amplifier
                        "effectId" to packet.effectId
                        "isAmbient" to packet.isAmbient
                        "isMaxDuration" to packet.isMaxDuration
                    }
                }
                is SPacketEntityEquipment -> {
                    logServer(packet) {
                        "entityId" to packet.entityID
                        "duration" to packet.equipmentSlot
                        "amplifier" to packet.itemStack.displayName
                    }
                }
                is SPacketEntityHeadLook -> {
                    logServer(packet) {
                        "entityId" to packet.entityHeadLookEntityId
                        "yaw" to packet.yaw
                    }
                }
                is SPacketEntityMetadata -> {
                    logServer(packet) {
                        "dataEntries" to buildString {
                            for (entry in packet.dataManagerEntries) {
                                append("> isDirty: ")
                                append(entry.isDirty)

                                append(" key: ")
                                append(entry.key)

                                append(" value: ")
                                append(entry.value)

                                append(' ')
                            }
                        }
                    }
                }
                is SPacketEntityProperties -> {
                    logServer(packet) {
                        "entityId" to packet.entityId
                        "snapshots" to packet.snapshots
                    }
                }
                is SPacketEntityStatus -> {
                    logServer(packet) {
                        "opCode" to packet.opCode
                    }
                }
                is SPacketEntityTeleport -> {
                    logServer(packet) {
                        "entityID" to packet.entityId
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "yaw" to packet.yaw
                        "pitch" to packet.pitch
                    }
                }
                is SPacketEntityVelocity -> {
                    logServer(packet) {
                        "entityID" to packet.entityID
                        "motionX" to packet.motionX
                        "motionY" to packet.motionY
                        "motionZ" to packet.motionZ
                    }
                }
                is SPacketExplosion -> {
                    logServer(packet) {
                        "strength" to packet.strength
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "motionX" to packet.motionX
                        "motionY" to packet.motionY
                        "motionZ" to packet.motionZ
                        "affectedBlockPositions" to buildString {
                            for (block in packet.affectedBlockPositions) {
                                append("> x: ")
                                append(block.x)

                                append("y: ")
                                append(block.y)

                                append("z: ")
                                append(block.z)

                                append(' ')
                            }
                        }
                    }
                }
                is SPacketHeldItemChange -> {
                    logServer(packet) {
                        "heldItemHotbarIndex" to packet.heldItemHotbarIndex
                    }
                }
                is SPacketJoinGame -> {
                    logServer(packet) {
                        "playerId" to packet.playerId
                        "difficulty" to packet.difficulty.name
                        "dimension" to packet.dimension
                        "gameType" to packet.gameType.name
                        "isHardcoreMode" to packet.isHardcoreMode
                        "isReducedDebugInfo" to packet.isReducedDebugInfo
                        "maxPlayers" to packet.maxPlayers
                        "worldType" to packet.worldType
                    }
                }
                is SPacketKeepAlive -> {
                    if (!ignoreKeepAlive) {
                        logServer(packet) {
                            "id" to packet.id
                        }
                    }
                }
                is SPacketMaps -> {
                    logServer(packet) {
                        "mapId" to packet.mapId
                    }
                }
                is SPacketMoveVehicle -> {
                    logServer(packet) {
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "yaw" to packet.yaw
                        "pitch" to packet.pitch
                    }
                }
                is SPacketMultiBlockChange -> {
                    logServer(packet) {
                        "changedBlocks" to buildString {
                            for (changedBlock in packet.changedBlocks) {
                                append("> x: ")
                                append(changedBlock.pos.x)

                                append("y: ")
                                append(changedBlock.pos.y)

                                append("z: ")
                                append(changedBlock.pos.z)

                                append(' ')
                            }
                        }
                    }
                }
                is SPacketOpenWindow -> {
                    logServer(packet) {
                        "entityId" to packet.entityId
                        "windowTitle" to packet.windowTitle
                        "guiId" to packet.guiId
                        "windowId" to packet.windowId
                        "slotCount" to packet.slotCount
                    }
                }
                is SPacketParticles -> {
                    logServer(packet) {
                        "particleType" to packet.particleType.name
                        "isLongDistance" to packet.isLongDistance
                        "particleType" to packet.particleType.name
                        "particleCount" to packet.particleCount
                        "particleSpeed" to packet.particleSpeed
                        "xCoordinate" to packet.xCoordinate
                        "yCoordinate" to packet.yCoordinate
                        "zCoordinate" to packet.zCoordinate
                        "xOffset" to packet.xOffset
                        "yOffset" to packet.yOffset
                        "zOffset" to packet.zOffset
                        "particleArgs" to packet.particleArgs
                    }
                }
                is SPacketPlayerPosLook -> {
                    logServer(packet) {
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "yaw" to packet.yaw
                        "pitch" to packet.pitch
                        "teleportID" to packet.teleportId
                        "flags" to buildString {
                            for (entry in packet.flags) {
                                append("> ")
                                append(entry.name)
                                append(' ')
                            }
                        }
                    }
                }
                is SPacketPlayerListItem -> {
                    logServer(packet) {
                        "action" to packet.action.name
                        "entries" to buildString {
                            for (entry in packet.entries) {
                                append("> displayName: ")
                                append(entry.displayName)
                                append(" gameMode: ")
                                append(entry.gameMode?.name)
                                append(" ping: ")
                                append(entry.ping)
                                append(" profile.id: ")
                                append(entry.profile?.id)
                                append(" profile.name: ")
                                append(entry.profile?.name)
                                append(" profile.properties: ")
                                append(entry.profile?.properties)
                                append(' ')
                            }
                        }
                    }
                }
                is SPacketSoundEffect -> {
                    logServer(packet) {
                        "sound" to packet.sound.soundName
                        "category" to packet.category
                        "posX" to packet.x
                        "posY" to packet.y
                        "posZ" to packet.z
                        "volume" to packet.volume
                        "pitch" to packet.pitch
                    }
                }
                is SPacketSpawnObject -> {
                    logServer(packet) {
                        "entityID" to packet.entityID
                        "data" to packet.data
                    }
                }
                is SPacketSpawnPlayer -> {
                    logServer(packet) {
                        "entityID" to packet.entityID
                        "uniqueID" to packet.uniqueId
                        "x" to packet.x
                        "y" to packet.y
                        "z" to packet.z
                        "yaw" to packet.yaw
                        "pitch" to packet.pitch
                    }
                }
                is SPacketTeams -> {
                    logServer(packet) {
                        "action" to packet.action
                        "type" to packet.displayName
                        "itSystem" to packet.color
                    }
                }
                is SPacketTimeUpdate -> {
                    if (!ignoreTimeUpdates) {
                        logServer(packet) {
                            "totalWorldTime" to packet.totalWorldTime
                            "worldTime" to packet.worldTime
                        }
                    }
                }
                is SPacketUnloadChunk -> {
                    if (!ignoreChunkLoading) {
                        logServer(packet) {
                            "x" to packet.x
                            "z" to packet.z
                        }
                    }
                }
                is SPacketUpdateTileEntity -> {
                    logServer(packet) {
                        "x" to packet.pos.x
                        "y" to packet.pos.y
                        "z" to packet.pos.z
                    }
                }
                is SPacketWindowItems -> {
                    logServer(packet) {
                        "windowId" to packet.windowId
                        "itemStacks" to buildString {
                            for (entry in packet.itemStacks) {
                                append("> ")
                                append(entry.displayName)
                                append(' ')
                            }
                        }
                    }
                }
                is SPacketSetSlot -> {
                    logServer(packet) {
                        "slot" to packet.slot
                        "stack" to packet.stack.displayName
                        "windowId" to packet.windowId
                    }
                }
                else -> {
                    if (!ignoreUnknown) {
                        logServer(packet) {
                            +"Not Registered in PacketLogger"
                        }
                    }
                }
            }
        }
    }

    private fun sendPacket(packet: Packet<*>) {
        if (packetSide == PacketSide.CLIENT || packetSide == PacketSide.BOTH) {
            when (packet) {
                is CPacketAnimation -> {
                    logClient(packet) {
                        "hand" to packet.hand
                    }
                }
                is CPacketChatMessage -> {
                    if (!ignoreChat) {
                        logClient(packet) {
                            "message" to packet.message
                        }
                    }
                }
                is CPacketClickWindow -> {
                    logClient(packet) {
                        "windowId" to packet.windowId
                        "slotID" to packet.slotId
                        "mouseButton" to packet.usedButton
                        "clickType" to packet.clickType
                        "transactionID" to packet.actionNumber
                        "clickedItem" to packet.clickedItem
                    }
                }
                is CPacketConfirmTeleport -> {
                    logClient(packet) {
                        "teleportID" to packet.teleportId
                    }
                }
                is CPacketEntityAction -> {
                    logClient(packet) {
                        "action" to packet.action.name
                        "auxData" to packet.auxData
                    }
                }
                is CPacketHeldItemChange -> {
                    logClient(packet) {
                        "slotID" to packet.slotId
                    }
                }
                is CPacketKeepAlive -> {
                    if (!ignoreKeepAlive) {
                        logClient(packet) {
                            "ket" to packet.key
                        }
                    }
                }
                is CPacketPlayer.Rotation -> {
                    if (!ignorePlayerPosition) {
                        logClient(packet) {
                            "yaw" to packet.playerYaw
                            "pitch" to packet.playerPitch
                            "onGround" to packet.isOnGround
                        }
                    }
                }
                is CPacketPlayer.Position -> {
                    if (!ignorePlayerPosition) {
                        logClient(packet) {
                            "x" to packet.playerX
                            "y" to packet.playerY
                            "z" to packet.playerZ
                            "onGround" to packet.isOnGround
                        }
                    }
                }
                is CPacketPlayer.PositionRotation -> {
                    if (!ignorePlayerPosition) {
                        logClient(packet) {
                            "x" to packet.playerX
                            "y" to packet.playerY
                            "z" to packet.playerZ
                            "yaw" to packet.playerYaw
                            "pitch" to packet.playerPitch
                            "onGround" to packet.isOnGround
                        }
                    }
                }
                is CPacketPlayer -> {
                    if (!ignorePlayerPosition) {
                        logClient(packet) {
                            "onGround" to packet.isOnGround
                        }
                    }
                }
                is CPacketPlayerDigging -> {
                    logClient(packet) {
                        "x" to packet.position.x
                        "y" to packet.position.y
                        "z" to packet.position.z
                        "facing" to packet.facing
                        "action" to packet.action
                    }
                }
                is CPacketPlayerTryUseItem -> {
                    logClient(packet) {
                        "hand" to packet.hand
                    }
                }
                is CPacketPlayerTryUseItemOnBlock -> {
                    logClient(packet) {
                        "x" to packet.pos.x
                        "y" to packet.pos.y
                        "z" to packet.pos.z
                        "side" to packet.direction
                        "hitVecX" to packet.facingX
                        "hitVecY" to packet.facingY
                        "hitVecZ" to packet.facingZ
                    }
                }
                is CPacketUseEntity -> {
                    @Suppress("UNNECESSARY_SAFE_CALL")
                    logClient(packet) {
                        "action" to packet.action
                        "action" to packet.hand
                        "hitVecX" to packet.hitVec?.x
                        "hitVecX" to packet.hitVec?.y
                        "hitVecX" to packet.hitVec?.z
                    }
                }
                is CPacketCloseWindow -> {
                    logClient(packet) {
                        "windowID" to packet.windowID
                    }
                }
                else -> {
                    if (!ignoreUnknown) {
                        logClient(packet) {
                            +"Not Registered in PacketLogger"
                        }
                    }
                }
            }
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
                with(File(FolderUtils.packetLogFolder)) {
                    if (!exists()) mkdir()
                }

                FileWriter("${FolderUtils.packetLogFolder}${filename}", true).buffered().use {
                    for (line in lines) it.write(line)
                }
            } catch (e: Exception) {
                LambdaMod.LOG.warn("$chatName Failed saving packet log!", e)
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

        infix fun String.to(value: BlockPos?) {
            if (value != null) {
                add("x", value.x.toString())
                add("y", value.y.toString())
                add("z", value.z.toString())
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
