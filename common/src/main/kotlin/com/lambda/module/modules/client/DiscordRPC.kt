package com.lambda.module.modules.client

import com.lambda.Lambda
import com.lambda.Lambda.mc
import com.lambda.event.EventFlow
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.onShutdown
import com.lambda.threading.runConcurrent
import com.lambda.util.Communication.info
import com.lambda.util.Nameable
import dev.cbyrne.kdiscordipc.KDiscordIPC
import dev.cbyrne.kdiscordipc.core.event.DiscordEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ActivityInviteEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ActivityJoinEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ErrorEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ReadyEvent
import dev.cbyrne.kdiscordipc.core.packet.inbound.impl.SetActivityPacket
import dev.cbyrne.kdiscordipc.data.activity.*
import dev.cbyrne.kdiscordipc.data.user.User
import kotlinx.coroutines.Job
import java.util.*

object DiscordRPC : Module(
    name = "DiscordRPC",
    description = "Discord Rich Presence configuration",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    private val playDetails by setting("Details", "Playing on Lambda")
    private val playState by setting("Play State", "Playing")

    private var joinSecret by setting("Join Secret", UUID.randomUUID().toString())
    private var partyId by setting("Party ID", UUID.randomUUID().toString())
    private var partySize by setting("Party Size", 1, 1..16, 1)
    private var partyMax by setting("Party Max Size", 16, 2..16, 1)

    private val confirmCoordinates by setting("Show Coordinates", false)
    private val confirmServer by setting("Show Server", false)

    private val line1Left by setting("Line 1 Left", LineInfo.VERSION)
    private val line1Right by setting("Line 1 Right", LineInfo.USERNAME)
    private val line2Left by setting("Line 2 Left", LineInfo.DIMENSION)
    private val line2Right by setting("Line 2 Right", LineInfo.HEALTH)
    private val delay by setting("Update Delay", 200, 200..2000, 1, unit = "ms")

    private val rpc = KDiscordIPC("1221289599427416127", scope = EventFlow.lambdaScope)
    private var lastInviter: User? = null
    private var job: Job? = null

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
        lock(runConcurrent {
            setup()
            connect()
        }) // Works

        onEnableUnsafe {
            lock(runConcurrent {
                setup()
                connect()
            }) // Doesn't work
        }

        onDisableUnsafe(::disconnect)
        onShutdown(::disconnect)
    }

    private fun lock(rpc: Job?) {
        if (job?.isActive == true) job?.cancel()
        job = rpc
    }

    private suspend fun setup() {
        rpc.on<ReadyEvent> {
            Lambda.LOG.info("Discord RPC connected to ${data.user.username}.")

            rpc.activityManager.setActivity {
                details = playDetails
                state = playState

                largeImage("lambda", Lambda.VERSION)
                smallImage("https://mc-heads.net/avatar/${mc.gameProfile.id}/nohelm", mc.gameProfile.name)

                party(partyId, partySize, partyMax)
                secrets(joinSecret)
                timestamps(System.currentTimeMillis())
            }

            rpc.subscribe(DiscordEvent.ActivityJoinRequest)
            rpc.subscribe(DiscordEvent.ActivityJoin)
            rpc.subscribe(DiscordEvent.ActivityInvite)
        }

        rpc.on<ActivityInviteEvent> {
            lastInviter = data.user
            info("${lastInviter?.username} has invited you to play")
            rpc.activityManager.acceptInvite(data) // TODO: Click button to join
        }

        rpc.on<ActivityJoinEvent> {
            joinSecret = data.secret
            info("Joined ${lastInviter?.username}'s party.") // TODO: Join server button
        }

        rpc.on<SetActivityPacket> {
            partyId = data?.party?.id ?: partyId
            partySize = data?.party?.size?.currentSize ?: partySize
            partyMax = data?.party?.size?.maxSize ?: partyMax
        }

        rpc.on<ErrorEvent> {
            Lambda.LOG.error("Discord RPC error: ${data.message}")
        }
    }

    private suspend fun connect() {
        if (!rpc.connected) {
            Lambda.LOG.info("Connecting to Discord RPC.")
            rpc.connect()
        }
    }

    private fun disconnect() {
        if (rpc.connected) {
            Lambda.LOG.info("Gracefully disconnecting from Discord RPC.")
            rpc.disconnect()
            lock(null)
        }
    }
}
