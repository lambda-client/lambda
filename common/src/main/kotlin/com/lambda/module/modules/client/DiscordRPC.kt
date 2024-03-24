package com.lambda.module.modules.client

import com.lambda.Lambda
import com.lambda.Lambda.mc
import com.lambda.event.EventFlow
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.onShutdown
import com.lambda.threading.runConcurrent
import com.lambda.util.Nameable
import dev.cbyrne.kdiscordipc.KDiscordIPC
import dev.cbyrne.kdiscordipc.core.event.DiscordEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ActivityInviteEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ActivityJoinEvent
import dev.cbyrne.kdiscordipc.core.event.impl.DisconnectedEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ReadyEvent
import dev.cbyrne.kdiscordipc.core.packet.inbound.impl.ErrorPacket
import dev.cbyrne.kdiscordipc.data.activity.*
import java.util.*

object DiscordRPC : Module(
    name = "DiscordRPC",
    description = "Discord Rich Presence configuration",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    private val details by setting("Details", "Playing on Lambda")
    private val playState by setting("Play State", "Playing")

    private val partyId by setting("Party ID", UUID.randomUUID().toString())
    private val joinSecret by setting("Join Secret", UUID.randomUUID().toString())
    private val partySize by setting("Party Size", 1, 1..100, 1)
    private val partyMax by setting("Party Max", 2, 2..100, 1)

    private val confirmCoordinates by setting("Show Coordinates", false)
    private val confirmServer by setting("Show Server", false)

    private val line1Left by setting("Line 1 Left", LineInfo.VERSION)
    private val line1Right by setting("Line 1 Right", LineInfo.USERNAME)
    private val line2Left by setting("Line 2 Left", LineInfo.DIMENSION)
    private val line2Right by setting("Line 2 Right", LineInfo.HEALTH)
    private val delay by setting("Update Delay", 200, 200..2000, 1, unit = "ms")

    private val rpc = KDiscordIPC("1221289599427416127", scope = EventFlow.lambdaScope)

    private enum class LineInfo(val value: String) : Nameable {
        VERSION(Lambda.VERSION),
        WORLD(
            if (mc.currentServerEntry != null) "Multiplayer"
            else if (mc.isIntegratedServerRunning) "Singleplayer"
            else "Main Menu"
        ),
        USERNAME(mc.session.username),
        HEALTH("${mc.player?.health ?: 0} HP"),
        HUNGER("${mc.player?.hungerManager?.foodLevel ?: 0} Hunger"),
        DIMENSION(mc.world?.dimension?.toString() ?: "Unknown"),
        COORDINATES(if (confirmCoordinates) "${mc.player!!.blockPos}" else "[Redacted]"),
        SERVER(if (confirmServer) mc.currentServerEntry?.address ?: "Not Connected" else "[Redacted]"),
        FPS("${mc.currentFps} FPS"),
    }

    init {
        onEnableUnsafe {
            runConcurrent {
                rpc.connect()
            }
        }

        onDisableUnsafe(::shutdown)
        onShutdown(::shutdown)

        runConcurrent {
            rpc.on<ReadyEvent> {
                Lambda.LOG.info("Discord RPC connected.")

                rpc.activityManager.setActivity {
                    //timestamps(System.currentTimeMillis())
                    largeImage("lambda", Lambda.VERSION)

                    button("Download", "https://github.com/lambda-client/lambda/releases/latest")

                    //party(partyId, partySize, partyMax)
                    //secrets(joinSecret)
                }

                rpc.subscribe(DiscordEvent.CurrentUserUpdate)
                rpc.subscribe(DiscordEvent.ActivityJoinRequest)
                rpc.subscribe(DiscordEvent.ActivityJoin)
                rpc.subscribe(DiscordEvent.ActivityInvite)
            }

            rpc.on<ActivityInviteEvent> {
                Lambda.LOG.info("Discord RPC invite: $data")
            }

            rpc.on<ActivityJoinEvent> {
                Lambda.LOG.info("Discord RPC join: $data")
            }

            rpc.on<ErrorPacket> {
                Lambda.LOG.error("Discord RPC error: $message")
            }
        }
    }

    private fun shutdown() {
        if (rpc.connected) {
            Lambda.LOG.info("Gracefully disconnecting from Discord RPC.")
            rpc.disconnect()
        }
    }
}
