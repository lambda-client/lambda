package com.lambda.module.modules.client

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.mc
import com.lambda.event.EventFlow
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.UnsafeListener.Companion.unsafeListener
import com.lambda.http.api.rpc.v1.endpoints.*
import com.lambda.http.api.rpc.v1.models.Authentication
import com.lambda.http.api.rpc.v1.models.Party
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.onShutdown
import com.lambda.threading.runConcurrent
import com.lambda.util.Communication.warn
import com.lambda.util.Nameable
import com.lambda.util.StringUtils.capitalize
import dev.cbyrne.kdiscordipc.KDiscordIPC
import dev.cbyrne.kdiscordipc.core.event.DiscordEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ActivityJoinEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ActivityJoinRequestEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ErrorEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ReadyEvent
import dev.cbyrne.kdiscordipc.core.packet.inbound.impl.AuthenticatePacket
import dev.cbyrne.kdiscordipc.data.activity.*
import kotlinx.coroutines.delay
import net.minecraft.network.encryption.NetworkEncryptionUtils
import net.minecraft.network.packet.s2c.login.LoginHelloS2CPacket
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicReference

object DiscordRPC : Module(
    name = "DiscordRPC",
    description = "Discord Rich Presence configuration",
    defaultTags = setOf(ModuleTag.CLIENT),
    enabledByDefault = true,
) {
    private val page by setting("Page", Page.General)

    /* General settings */
    private val line1Left by setting("Line 1 Left", LineInfo.WORLD) { page == Page.General }
    private val line1Right by setting("Line 1 Right", LineInfo.USERNAME) { page == Page.General }
    private val line2Left by setting("Line 2 Left", LineInfo.DIMENSION) { page == Page.General }
    private val line2Right by setting("Line 2 Right", LineInfo.FPS) { page == Page.General }

    private val confirmCoordinates by setting("Show Coordinates", false, description = "Confirm display the player coordinates") { page == Page.General }
    private val confirmServer by setting("Expose server", false, description = "Confirm display the server IP") { page == Page.General }
    private val showTime by setting("Show Time", true, description = "Show how long you have been playing for.") { page == Page.General }

    /* Technical settings */
    private var rpcServer by setting("RPC Server", "http://127.0.0.1:8080") { page == Page.Settings } // TODO: Change this in production
    private var apiVersion by setting("API Version", ApiVersion.V1) { page == Page.Settings }
    private val delay by setting("Update Delay", 15000, 15000..30000, 100, unit = "ms", visibility = { page == Page.Settings })

    /* Party settings */
    private val enableParty by setting("Enable Party", true, description = "Allows you to create parties.") { page == Page.Party }
    private val createByDefault by setting("Create Party by Default", true, description = "Automatically create a party when you join a server.") { page == Page.Party && enableParty }
    private val maxPlayers by setting("Max Players", 10, 2..20, visibility = { page == Page.Party }).apply { onValueChange { _, _ -> edit() } }
    private val public by setting("Public Party", false, description = "Allow anyone to join your party.") { page == Page.Party }.apply { onValueChange { _, _ -> edit() } }

    private val rpc = KDiscordIPC(Lambda.APP_ID, scope = EventFlow.lambdaScope)
    private var startup = System.currentTimeMillis()
    private val dimensionRegex = Regex("""\b\w+_\w+\b""")

    private var ready: ReadyEvent? = null
    private var discordAuth: AuthenticatePacket.Data? = null
    private var rpcAuth: Authentication? = null
    private var currentParty: AtomicReference<Party?> = AtomicReference(null)

    private var connectionTime: Long = 0
    private var serverId: String? = null

    /**
     * If the player can interact with the party system.
     */
    private val allowed: Boolean
        get() = rpcAuth != null && discordAuth != null && enableParty

    private enum class Page {
        General, Settings, Party
    }

    private enum class LineInfo(val value: () -> String) : Nameable {
        VERSION({ Lambda.VERSION }),
        WORLD({
            when {
                mc.currentServerEntry != null -> "Multiplayer"
                mc.isIntegratedServerRunning -> "Singleplayer"
                else -> "Main Menu"
            }
        }),
        USERNAME({ mc.session.username }),
        HEALTH({ "${mc.player?.health ?: 0} HP" }),
        HUNGER({ "${mc.player?.hungerManager?.foodLevel ?: 0} Hunger" }),
        DIMENSION({
            mc.world?.registryKey?.value?.path?.replace(dimensionRegex) {
                it.value.split("_").joinToString(" ") { it.capitalize() }
            } ?: "Unknown"
        }),
        COORDINATES({
            if (confirmCoordinates) "Coords: ${mc.player?.blockPos?.toShortString()}"
            else "[Redacted]"
        }),
        SERVER({
            if (confirmServer) mc.currentServerEntry?.address ?: "Not Connected"
            else "[Redacted]"
        }),
        FPS({ "${mc.currentFps} FPS" });
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

        // Will not work if the player doesn't have a valid key pair
        // from Mojang's authentication server.
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
        startup = System.currentTimeMillis()

        if (event != null) rpc.register(event)
        if (!rpc.connected) runConcurrent { rpc.connect() }

        while (true) {
            if (rpc.connected) update()
            delay(delay.toLong())
        }
    }

    private fun disconnect() {
        if (rpc.connected) {
            LOG.info("Gracefully disconnecting from Discord RPC.")
            rpc.disconnect()
            leaveParty(rpcServer, apiVersion.value, rpcAuth?.accessToken ?: return)
        }

        ready = null
        discordAuth = null
        rpcAuth = null
        currentParty.lazySet(null)
    }

    fun createParty() {
        if (!allowed) return

        createParty(rpcServer, apiVersion.value, rpcAuth?.accessToken ?: return, maxPlayers, public)
            .also { response ->
                if (response.error != null) warn("Failed to create a party: ${response.error}")
                currentParty.lazySet(response.data)
            }
    }

    fun join(id: String = rpc.activityManager.activity?.party?.id ?: "") {
        if (!allowed) return

        joinParty(rpcServer, apiVersion.value, rpcAuth?.accessToken ?: return, id)
            .also { response ->
                if (response.error != null) warn("Failed to join the party: ${response.error}")
                currentParty.lazySet(response.data)
            }
    }

    private fun edit() {
        if (!allowed) return

        currentParty.acquire?.let {
            editParty(rpcServer, apiVersion.value, rpcAuth?.accessToken ?: return, maxPlayers, public)
                .also { response ->
                    if (response.error != null) warn("Failed to edit the party: ${response.error}")
                    currentParty.lazySet(response.data)
                }
        }
    }

    private suspend fun update() {
        val party = currentParty.acquire

        rpc.activityManager.setActivity {
            details = "${line1Left.value()} | ${line1Right.value()}".take(128)
            state = "${line2Left.value()} | ${line2Right.value()}".take(128)

            largeImage("lambda", Lambda.VERSION)
            smallImage("https://mc-heads.net/avatar/${mc.gameProfile.id}/nohelm", mc.gameProfile.name)

            if (allowed && party != null) {
                party(party.id, party.players.size, party.settings.maxPlayers)
                secrets(party.joinSecret)
            } else {
                button("Download", "https://github.com/lambda-client/lambda")
            }

            if (showTime) timestamps(startup)
        }
    }

    private suspend fun KDiscordIPC.register(auth: ConnectionEvent.Connect.Login.Key) {
        if (rpc.connected && ready != null) return

        on<ReadyEvent> {
            ready = this

            // Party features
            subscribe(DiscordEvent.ActivityJoinRequest)
            subscribe(DiscordEvent.ActivityJoin)
            //subscribe(DiscordEvent.LobbyUpdate)
            //subscribe(DiscordEvent.LobbyDelete)
            //subscribe(DiscordEvent.LobbyMemberConnect)
            //subscribe(DiscordEvent.LobbyMemberDisconnect)
            //subscribe(DiscordEvent.LobbyMemberUpdate)

            // QOL features
            //subscribe(DiscordEvent.SpeakingStart)
            //subscribe(DiscordEvent.SpeakingStop)

            if (System.currentTimeMillis() - connectionTime > 300000) {
                warn("The authentication hash has expired, reconnect to the server.")
                return@on
            }

            val hash = BigInteger(
                NetworkEncryptionUtils.computeServerId(serverId ?: return@on, auth.publicKey, auth.secretKey)
            ).toString(16)

            // Prompt the user to authorize
            discordAuth = rpc.applicationManager.authenticate()

            login(rpcServer, apiVersion.value, discordAuth?.accessToken ?: "", mc.session.username, hash)
                .also { response ->
                    if (response.error != null) warn("Failed to authenticate with the RPC server: ${response.error}")
                    rpcAuth = response.data
                }

            if (createByDefault) createParty()
        }

        // Event when someone would like to join your party
        on<ActivityJoinRequestEvent> {
            LOG.info("Received a party join request from ${data.userId}.")
            rpc.activityManager.acceptJoinRequest(data.userId)
            // TODO: Implement a GUI for this
        }

        // Event when someone joins your party
        on<ActivityJoinEvent> {
            LOG.info("TEst invite join")
        }

        on<ErrorEvent> {
            LOG.error("Discord RPC error: ${data.message}")
        }
    }
}
