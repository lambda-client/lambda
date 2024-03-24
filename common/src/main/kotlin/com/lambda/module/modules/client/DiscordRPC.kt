package com.lambda.module.modules.client

import com.lambda.Lambda
import com.lambda.Lambda.mc
import com.lambda.event.EventFlow
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runConcurrent
import dev.cbyrne.kdiscordipc.KDiscordIPC
import dev.cbyrne.kdiscordipc.core.event.DiscordEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ActivityInviteEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ActivityJoinEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ReadyEvent
import dev.cbyrne.kdiscordipc.data.activity.*
import java.util.*

object DiscordRPC : Module(
    name = "DiscordRPC",
    description = "Discord Rich Presence configuration",
    defaultTags = setOf(ModuleTag.CLIENT)
) {
    private val line1Left by setting("Line 1 Left", LineInfo.VERSION)
    private val line1Right by setting("Line 1 Right", LineInfo.USERNAME)
    private val line2Left by setting("Line 2 Left", LineInfo.DIMENSION)
    private val line2Right by setting("Line 2 Right", LineInfo.HEALTH)
    private val confirmCoordinates by setting("Show Coordinates", false)
    private val confirmServer by setting("Show Server", false)
    private val delay by setting("Update Delay", 200, 200..2000, 1, unit = "ms")

    private val rpc = KDiscordIPC("835368493150502923", scope = EventFlow.lambdaScope)

    private enum class LineInfo(val value: String) {
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
        //COORDINATES(if (confirmCoordinates) "${mc.player!!.blockPos}" else "[Redacted]"),
        //SERVER(if (confirmServer) mc.currentServerEntry?.address ?: "Not Connected" else "[Redacted]"),
        FPS("${mc.currentFps} FPS"),
    }

    init {
        onEnableUnsafe {
            runConcurrent(rpc::connect)
        }

        onDisableUnsafe(rpc::disconnect)

        runConcurrent {
            rpc.on<ReadyEvent> {
                Lambda.LOG.info("Discord RPC connected.")

                rpc.subscribe(DiscordEvent.CurrentUserUpdate)
                rpc.subscribe(DiscordEvent.ActivityJoinRequest)
                rpc.subscribe(DiscordEvent.ActivityJoin)
                rpc.subscribe(DiscordEvent.ActivityInvite)
                rpc.subscribe(DiscordEvent.ActivitySpectate)

                rpc.activityManager.setActivity {
                    largeImage("https://avatars.githubusercontent.com/u/71222289?v=4", "KDiscordIPC")
                    smallImage("https://avatars.githubusercontent.com/u/71222289?v=4", "Testing")
                    button("a", "https://google.com")

                    party(UUID.randomUUID().toString(), 1, 2)
                    secrets(UUID.randomUUID().toString(), UUID.randomUUID().toString())
                    timestamps(System.currentTimeMillis())
                }
            }

            rpc.on<ActivityInviteEvent> {
                Lambda.LOG.info("Discord RPC invite: $data")
            }

            rpc.on<ActivityJoinEvent> {
                Lambda.LOG.info("Discord RPC join: $data")
            }
        }
    }
}
