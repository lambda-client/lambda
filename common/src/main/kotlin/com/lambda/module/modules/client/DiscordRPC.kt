package com.lambda.module.modules.client

import com.lambda.Lambda
import com.lambda.Lambda.mc
import com.lambda.event.EventFlow
import com.lambda.event.EventFlow.ioScope
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.onShutdown
import com.lambda.threading.runConcurrent
import com.lambda.util.Communication.info
import com.lambda.util.Nameable
import com.lambda.util.StringUtils.capitalize
import dev.cbyrne.kdiscordipc.KDiscordIPC
import dev.cbyrne.kdiscordipc.core.event.DiscordEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ActivityInviteEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ActivityJoinEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ErrorEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ReadyEvent
import dev.cbyrne.kdiscordipc.core.packet.inbound.impl.SetActivityPacket
import dev.cbyrne.kdiscordipc.data.activity.*
import dev.cbyrne.kdiscordipc.data.user.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

object DiscordRPC : Module(
    name = "DiscordRPC",
    description = "Discord Rich Presence configuration",
    defaultTags = setOf(ModuleTag.CLIENT),
//    enabledByDefault = true, // ToDo: Enable before release
) {
//    private val playDetails by setting("Details", "Playing on Lambda")
//    private val playState by setting("Play State", "Playing")

//    private var joinSecret by setting("Join Secret", UUID.randomUUID().toString())
//    private var partyId by setting("Party ID", UUID.randomUUID().toString())
//    private var partySize by setting("Party Size", 1, 1..16, 1)
//    private var partyMax by setting("Party Max Size", 16, 2..16, 1)

    private val confirmCoordinates by setting("Show Coordinates", false)
    private val confirmServer by setting("Show Server", false)

    private val line1Left by setting("Line 1 Left", LineInfo.WORLD)
    private val line1Right by setting("Line 1 Right", LineInfo.USERNAME)
    private val line2Left by setting("Line 2 Left", LineInfo.DIMENSION)
    private val line2Right by setting("Line 2 Right", LineInfo.FPS)
    // Max update delay is 5 per 20 seconds -> every 4 seconds
    private val delay by setting("Update Delay", 4, 4..60, 1, unit = "s")

    private val rpc = KDiscordIPC(Lambda.APP_ID, scope = ioScope)
    private val startup = System.currentTimeMillis()
//    private var lastInviter: User? = null

    private enum class LineInfo(val value: () -> String) : Nameable {
        VERSION({ Lambda.VERSION }),
        WORLD({
            if (mc.currentServerEntry != null) "Multiplayer"
            else if (mc.isIntegratedServerRunning) "Singleplayer"
            else "Main Menu"
        }),
        USERNAME({ mc.session.username }),
        HEALTH({ "${mc.player?.health ?: 0} HP" }),
        HUNGER({ "${mc.player?.hungerManager?.foodLevel ?: 0} Hunger" }),
        DIMENSION({ mc.world?.dimensionKey?.value?.path?.capitalize() ?: "Unknown" }),
        COORDINATES({ if (confirmCoordinates) "Coords: ${mc.player?.blockPos?.toShortString()}" else "[Redacted]" }),
        SERVER({ if (confirmServer) mc.currentServerEntry?.address ?: "Not Connected" else "[Redacted]" }),
        FPS({ "${mc.currentFps} FPS" }),
    }

    init {
        ioScope.launch {
            rpc.register()

            while (true) {
                if (rpc.connected) update()
                delay(delay * 1000L)
            }
        }

        onEnableUnsafe {
            connect()
        }

        onDisableUnsafe {
            disconnect()
        }

        onShutdown {
            disconnect()
        }
    }

    private fun connect() {
        ioScope.launch {
            if (!rpc.connected) {
                Lambda.LOG.info("Connecting to Discord RPC.")
                rpc.connect()
            }
        }
    }

    private fun disconnect() {
        ioScope.launch {
            if (rpc.connected) {
                Lambda.LOG.info("Gracefully disconnecting from Discord RPC.")
                rpc.disconnect()
            }
        }
    }

    private fun update() {
        ioScope.launch {
            rpc.activityManager.setActivity {
                details = "${line1Left.value()} ${line1Right.value()}".take(128)
                state = "${line2Left.value()} ${line2Right.value()}".take(128)

                largeImage("lambda", Lambda.VERSION)
                smallImage("https://mc-heads.net/avatar/${mc.gameProfile.id}/nohelm", mc.gameProfile.name)

//                party(partyId, partySize, partyMax)
//                secrets(joinSecret)
                button("Download", "https://modrinth.com/") // ToDo: Add real link

                timestamps(startup)
            }
        }
    }

    private suspend fun KDiscordIPC.register() {
        on<ReadyEvent> {
            Lambda.LOG.info("Discord RPC connected to ${data.user.username}.")

//            subscribe(DiscordEvent.ActivityJoinRequest)
//            subscribe(DiscordEvent.ActivityJoin)
//            subscribe(DiscordEvent.ActivityInvite)
        }

//        on<ActivityInviteEvent> {
//            lastInviter = data.user
//            info("${lastInviter?.username} has invited you to play")
//            activityManager.acceptInvite(data) // TODO: Click button to join
//        }
//
//        on<ActivityJoinEvent> {
//            joinSecret = data.secret
//            info("Joined ${lastInviter?.username}'s party.") // TODO: Join server button
//        }
//
//        on<SetActivityPacket> {
//            partyId = data?.party?.id ?: partyId
//            partySize = data?.party?.size?.currentSize ?: partySize
//            partyMax = data?.party?.size?.maxSize ?: partyMax
//        }

        on<ErrorEvent> {
            Lambda.LOG.error("Discord RPC error: ${data.message}")
        }
    }
}
