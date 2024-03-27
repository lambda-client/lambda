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
import com.lambda.util.Communication.toast
import com.lambda.util.Nameable
import com.lambda.util.StringUtils.capitalize
import com.lambda.util.text.ClickEvents
import com.lambda.util.text.buildText
import com.lambda.util.text.clickEvent
import com.lambda.util.text.literal
import dev.cbyrne.kdiscordipc.KDiscordIPC
import dev.cbyrne.kdiscordipc.core.event.DiscordEvent
import dev.cbyrne.kdiscordipc.core.event.data.ActivityInviteEventData
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
    //enabledByDefault = true, // ToDo: Enable before release
) {

    private var joinSecret by setting("Join Secret", UUID.randomUUID().toString()) // TODO: Server to generate this
    private var partyId by setting("Party ID", UUID.randomUUID().toString())
    private var partySize by setting("Party Size", 1, 1..16, 1)
    private var partyMax by setting("Party Max Size", 16, 2..16, 1)

    private val confirmCoordinates by setting("Show Coordinates", false)
    private val confirmServer by setting("Expose server", false, description = "Allow to show what server you are on and allow to join parties.")
    private val enableParty by setting("Enable Party", true, description = "Will allow you to create and join parties but cannot have buttons.")
    private val showTime by setting("Show Time", true, description = "Show how long you have been playing for.")

    private val line1Left by setting("Line 1 Left", LineInfo.WORLD)
    private val line1Right by setting("Line 1 Right", LineInfo.USERNAME)
    private val line2Left by setting("Line 2 Left", LineInfo.DIMENSION)
    private val line2Right by setting("Line 2 Right", LineInfo.FPS)

    // Max update delay is 5 per 20 seconds -> every 4 seconds
    private val delay by setting("Update Delay", 4, 4..60, 1, unit = "s")

    private val rpc = KDiscordIPC(Lambda.APP_ID, scope = ioScope)
    private val startup = System.currentTimeMillis()
    private var lastInviter: User? = null
    private var lastInvite: ActivityInviteEventData? = null

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

                if (enableParty && false) { // TODO: Implement parties
                    // TODO: Only enable if the player is in a server
                    party(partyId, partySize, partyMax)
                    secrets(joinSecret)
                } else {
                    button("Download", "https://modrinth.com/") // ToDo: Add real link
                }

                if (showTime) timestamps(startup)
            }
        }
    }

    private suspend fun KDiscordIPC.register() {
        on<ReadyEvent> {
            Lambda.LOG.info("Discord RPC connected to ${data.user.username}.")

            subscribe(DiscordEvent.ActivityJoinRequest)
            subscribe(DiscordEvent.ActivityJoin)
            subscribe(DiscordEvent.ActivityInvite)
        }

        on<ActivityInviteEvent> {
            lastInviter = data.user

            info(buildText {
                clickEvent(ClickEvents.runCommand(";rpc accept")) {
                    literal("Click to join ${data.user.username}'s party.")
                }
            }) // TODO: Possibility to block users

            toast("You have been invited to play by ${lastInviter?.username}") // TODO: Custom toast ?
            lastInvite = data
        }

        on<ActivityJoinEvent> {
            joinSecret = data.secret
            info("Joined ${lastInviter?.username}'s party.")
        }

        on<SetActivityPacket> {
            partyId = data?.party?.id ?: partyId
            partySize = data?.party?.size?.currentSize ?: partySize
            partyMax = data?.party?.size?.maxSize ?: partyMax
        }

        on<ErrorEvent> {
            Lambda.LOG.error("Discord RPC error: ${data.message}")
        }
    }
}
