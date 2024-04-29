package com.lambda.module.modules.client

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.ioScope
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import com.lambda.http.api.rpc.v1.endpoints.createParty
import com.lambda.http.api.rpc.v1.endpoints.editParty
import com.lambda.http.api.rpc.v1.endpoints.joinParty
import com.lambda.http.api.rpc.v1.endpoints.login
import com.lambda.http.api.rpc.v1.models.Authentication
import com.lambda.http.api.rpc.v1.models.Party
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.onShutdown
import com.lambda.threading.runConcurrent
import com.lambda.util.Communication.info
import com.lambda.util.Communication.toast
import com.lambda.util.Communication.warn
import com.lambda.util.Nameable
import com.lambda.util.StringUtils.capitalize
import com.lambda.util.primitives.extension.isOffline
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
import dev.cbyrne.kdiscordipc.core.packet.inbound.impl.AuthenticatePacket
import dev.cbyrne.kdiscordipc.data.activity.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.network.encryption.NetworkEncryptionUtils
import net.minecraft.network.packet.s2c.login.LoginHelloS2CPacket
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicReference

object DiscordRPC : Module(
    name = "DiscordRPC",
    description = "Discord Rich Presence configuration",
    defaultTags = setOf(ModuleTag.CLIENT),
) {
    private val page by setting("Page", Page.General)

    /* General settings */
    private val line1Left by setting("Line 1 Left", LineInfo.WORLD) { page == Page.General }
    private val line1Right by setting("Line 1 Right", LineInfo.USERNAME) { page == Page.General }
    private val line2Left by setting("Line 2 Left", LineInfo.DIMENSION) { page == Page.General }
    private val line2Right by setting("Line 2 Right", LineInfo.FPS) { page == Page.General }

    private val confirmCoordinates by setting("Show Coordinates", false) { page == Page.General }
    private val confirmServer by setting("Expose server", false, description = "Allow to show what server you are on and allow to join parties.") { page == Page.General }
    private val showTime by setting("Show Time", true, description = "Show how long you have been playing for.") { page == Page.General }

    /* Technical settings */
    private var rpcServer by setting("RPC Server", "http://127.0.0.1:8080") { page == Page.Settings } // TODO: Change this in production
    private var apiVersion by setting("API Version", ApiVersion.V1) { page == Page.Settings }
    private val delay by setting("Update Delay", 4, 4..60, 1, unit = "ms", visibility = { page == Page.Settings })

    /* Party settings */
    private val enableParty by setting("Enable Party", true, description = "Allows you to create parties.") { page == Page.Party }
    private val createByDefault by setting("Create Party by Default", false, description = "Automatically create a party when you join a server.") { page == Page.Party && enableParty }
    private val maxPlayers by setting("Max Players", 10, 2..20, visibility = { page == Page.Party }).apply { listener { _, _ -> edit() }}
    private val public by setting("Public Party", false, description = "Allow anyone to join your party.") { page == Page.Party }.apply { listener { _, _ -> edit() }}
    private val listed by setting("Listed Party", false, description = "Allow your party to be listed for other players.") { page == Page.Party && public }.apply { listener { _, _ -> edit() }}

    private val rpc = KDiscordIPC(Lambda.APP_ID, scope = ioScope)
    private val startup = System.currentTimeMillis()
    private val cracked = mc.gameProfile.isOffline

    private var discordAuth: AuthenticatePacket.Data? = null
    private var rpcAuth: Authentication? = null
    private var currentParty: AtomicReference<Party?> = AtomicReference(null)

    private var lastInvite: ActivityInviteEventData? = null

    private var connectionTime: Long = 0
    private var serverId: String? = null

    /**
     * If the player can interact with the party system.
     */
    private val allowed: Boolean
        get() = rpcAuth != null && discordAuth != null && !cracked && enableParty

    private enum class Page {
        General, Settings, Party
    }

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

    private enum class ApiVersion(val value: String) {
        // We can use @Deprecated("Not supported") to remove old API versions in the future
        V1("v1"),
    }

    init {
        unsafeListener<PacketEvent.Receive.Pre> {
            if (it.packet !is LoginHelloS2CPacket) return@unsafeListener
            connectionTime = System.currentTimeMillis()
            serverId = it.packet.serverId
        }

        // Will not work in single player or cracked servers
        unsafeListener<ConnectionEvent.Connect.Login.Key> { event ->
            runConcurrent {
                connect(event)
            }
        }

        onEnableUnsafe {
            runConcurrent {
                connect()
            }
        }

        onDisableUnsafe { disconnect() }
        onShutdown { disconnect() }
    }

    private suspend fun connect(event: ConnectionEvent.Connect.Login.Key? = null) {
        if (!rpc.connected) {
            rpc.register()
            rpc.connect()
        }

        if (!cracked &&
            (rpcAuth == null ||
            discordAuth == null) &&
            event != null)
        {
            if (System.currentTimeMillis() - connectionTime > 300000) {
                warn("The authentication hash has expired, please reconnect to the server.")
                return
            }

            val hash = BigInteger(NetworkEncryptionUtils.computeServerId(serverId ?: return, event.publicKey, event.secretKey)).toString(16)

            // Prompt the user to authorize
            discordAuth = rpc.applicationManager.authenticate()
            rpcAuth = login(rpcServer, apiVersion.value, discordAuth?.accessToken ?: "", mc.session.username, hash)

            if (rpcAuth != null) {
                info("Successfully authenticated with the RPC server.")
                if (createByDefault) create()
            } else {
                warn("Failed to authenticate with the RPC server.")
            }
        } else {
            warn("You are using an offline account, please use a premium account to access all the RPC features.")
        }

        loop@ while (true) {
            if (rpc.connected) update() else break@loop
            delay(delay * 1000L)
        }
    }

    private fun disconnect() {
        if (rpc.connected) {
            LOG.info("Gracefully disconnecting from Discord RPC.")
            rpc.disconnect()
        }
    }

    // We won't need to specify non-null variables in kotlin 2.0
    fun join(id: String) {
        if (!allowed) return

        ioScope.launch {
            joinParty(rpcServer, apiVersion.value, rpcAuth!!.accessToken, id)
                .also { currentParty.lazySet(it) }
        }
    }

    fun accept() {
        if (!allowed) return

        ioScope.launch {
            lastInvite?.let {
                join(it.activity.party.id)
            }
        }
    }

    private fun create() {
        if (!allowed) return

        ioScope.launch {
            createParty(rpcServer, apiVersion.value, rpcAuth!!.accessToken, maxPlayers, public, listed)
                .also { currentParty.lazySet(it) }
        }
    }

    private fun edit() {
        if (!allowed) return

        ioScope.launch {
            currentParty.acquire?.let {
                editParty(rpcServer, apiVersion.value, rpcAuth!!.accessToken, maxPlayers, public, listed)
                    .also { currentParty.lazySet(it) }
            }
        }
    }

    private suspend fun update() {
        rpc.activityManager.setActivity {
            details = "${line1Left.value()} | ${line1Right.value()}".take(128)
            state = "${line2Left.value()} | ${line2Right.value()}".take(128)

            largeImage("lambda", Lambda.VERSION)
            smallImage("https://mc-heads.net/avatar/${mc.gameProfile.id}/nohelm", mc.gameProfile.name)

            val party = currentParty.acquire

            if (allowed && party != null) {
                party(party.id, party.players.size, party.settings.maxPlayers)
                secrets(party.joinSecret)
            } else {
                button("Download", "https://modrinth.com/") // ToDo: Add real link
            }

            if (showTime) timestamps(startup)
        }
    }

    private suspend fun KDiscordIPC.register() {
        on<ReadyEvent> {
            LOG.info("Discord RPC connected to ${data.user.username}.")

            if (!cracked) {
                // Party features
                subscribe(DiscordEvent.ActivityJoinRequest)
                subscribe(DiscordEvent.ActivityJoin)
                subscribe(DiscordEvent.ActivityInvite)
                subscribe(DiscordEvent.LobbyUpdate)
                subscribe(DiscordEvent.LobbyDelete)
                subscribe(DiscordEvent.LobbyMemberConnect)
                subscribe(DiscordEvent.LobbyMemberDisconnect)
                subscribe(DiscordEvent.LobbyMemberUpdate)

                // QOL features
                subscribe(DiscordEvent.SpeakingStart)
                subscribe(DiscordEvent.SpeakingStop)
            }
        }

        on<ActivityInviteEvent> {
            lastInvite = data

            info(buildText {
                clickEvent(ClickEvents.runCommand(";rpc accept")) { // TODO: Custom click events
                    literal("Click to join ${data.user.username}'s party.")
                }
            })

            toast("You have been invited to play by ${lastInvite?.user?.username}")
        }

        on<ActivityJoinEvent> {
            info("Joined ${lastInvite?.user?.username}'s party.")
        }

        on<ErrorEvent> {
            LOG.error("Discord RPC error: ${data.message}")
        }
    }
}
