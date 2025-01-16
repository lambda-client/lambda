/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.module.modules.client

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.Lambda.mc
import com.lambda.event.EventFlow
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.http.api.rpc.v1.endpoints.*
import com.lambda.http.api.rpc.v1.models.Authentication
import com.lambda.http.api.rpc.v1.models.Party
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runConcurrent
import com.lambda.util.Communication.logError
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
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.encryption.NetworkEncryptionUtils
import java.math.BigInteger

object DiscordRPC : Module(
    name = "DiscordRPC",
    description = "Discord Rich Presence configuration",
    defaultTags = setOf(ModuleTag.CLIENT),
//    enabledByDefault = true, // ToDo: Bring this back on beta release
) {
    private val page by setting("Page", Page.General)

    /* General settings */
    private val showTime by setting("Show Time", true, description = "Show how long you have been playing for.") { page == Page.General }
    private val line1Left by setting("Line 1 Left", LineInfo.WORLD) { page == Page.General }
    private val line1Right by setting("Line 1 Right", LineInfo.USERNAME) { page == Page.General }
    private val line2Left by setting("Line 2 Left", LineInfo.DIMENSION) { page == Page.General }
    private val line2Right by setting("Line 2 Right", LineInfo.FPS) { page == Page.General }
    private val confirmCoordinates by setting("Show Coordinates", false, description = "Confirm display the player coordinates") { page == Page.General }
    private val confirmServer by setting("Show Server IP", false, description = "Confirm display the server IP") { page == Page.General }

    /* Technical settings */
    private var rpcServer by setting("RPC Server", "https://api.lambda-client.org") { page == Page.Settings }
    private var apiVersion by setting("API Version", ApiVersion.V1) { page == Page.Settings }
    private val delay by setting("Update Delay", 15000L, 15000L..30000L, 100L, unit = "ms") { page == Page.Settings }

    /* Party settings */
    private val enableParty by setting("Enable Party", true, description = "Allows you to create parties.") { page == Page.Party }
    private val maxPlayers by setting("Max Players", 10, 2..20) { page == Page.Party }
        .apply { onValueChange { _, _ -> if (player.isPartyOwner) edit() } }

    private val rpc = KDiscordIPC(Lambda.APP_ID, scope = EventFlow.lambdaScope)
    private var startup = System.currentTimeMillis()
    private val dimensionRegex = Regex("""\b\w+_\w+\b""")

    private var ready: ReadyEvent? = null
    private var keyEvent: ConnectionEvent.Connect.Login.EncryptionResponse? = null

    private var discordAuth: AuthenticatePacket.Data? = null
    private var rpcAuth: Authentication? = null
    private var currentParty: Party? = null
    private var connectionTime: Long = 0
    private var serverId: String? = null

    private val isPartyInteractionAllowed: Boolean
        get() = rpcAuth != null && discordAuth != null

    private val PlayerEntity.isPartyOwner
        get() = uuid == currentParty?.leader?.uuid

    private val PlayerEntity.isInParty
        get() = currentParty?.players?.any { it.uuid == this.uuid }

    init {
        listenUnsafe<ConnectionEvent.Connect.Login.EncryptionRequest> {
            connectionTime = System.currentTimeMillis()
            serverId = it.serverId
        }

        listenUnsafe<ConnectionEvent.Connect.Login.EncryptionResponse> {
            if (it.secretKey.isDestroyed)
                return@listenUnsafe logError(
                    "Error during the login process",
                    "The client secret key was destroyed by another listener"
                )

            keyEvent = it
        }

        listenUnsafe<ConnectionEvent.Connect.Post> { connect() }

        // TODO: Exponential backoff up to 25 seconds
        onEnable { connect() }
        onDisable { disconnect() }
    }

    fun createParty() {
        if (!isPartyInteractionAllowed) return

        createParty(rpcServer, apiVersion.value, rpcAuth?.accessToken ?: return, maxPlayers, true)
            .also { response ->
                if (response.error != null) warn(response.toString())
                currentParty = response.data
            }
    }

    // Join a party using the ID
    fun join(id: String) {
        if (!isPartyInteractionAllowed) return

        joinParty(rpcServer, apiVersion.value, rpcAuth?.accessToken ?: return, id)
            .also { response ->
                response.error?.let { return@also warn("Failed to join the party", it.toString()) }
                currentParty = response.data
            }
    }

    // Edit the current party if you are the owner
    private fun edit() {
        if (!isPartyInteractionAllowed) return

        editParty(rpcServer, apiVersion.value, rpcAuth?.accessToken ?: return, maxPlayers)
            .also { response ->
                response.error?.let { return@also warn("Failed to edit the party", it.toString()) }
                currentParty = response.data
            }
    }

    private fun connect() {
        runConcurrent { rpc.connect() }

        runConcurrent {
            keyEvent?.let { rpc.register(it) }
        }

        runConcurrent {
            while (rpc.connected) {
                updateActivity()
                delay(delay)
            }
        }
    }

    private fun disconnect() {
        if (rpc.connected) {
            LOG.info("Gracefully disconnecting from Discord RPC.")
            leaveParty(rpcServer, apiVersion.value, rpcAuth?.accessToken ?: return)
            rpc.disconnect()
        }

        ready = null
        discordAuth = null
        rpcAuth = null
        currentParty = null
        keyEvent = null
    }

    private suspend fun updateActivity() {
        val party = currentParty

        rpc.activityManager.setActivity {
            details = "${line1Left.value()} | ${line1Right.value()}".take(128)
            state = "${line2Left.value()} | ${line2Right.value()}".take(128)

            largeImage("lambda", Lambda.VERSION)
            smallImage("https://mc-heads.net/avatar/${mc.gameProfile.id}/nohelm", mc.gameProfile.name)

            if (isPartyInteractionAllowed && party != null) {
                party(party.id.toString(), party.players.size, party.settings.maxPlayers)
                secrets(party.joinSecret)
            } else {
                button("Download", "https://github.com/lambda-client/lambda")
            }

            if (showTime) timestamps(startup)
        }
    }

    private suspend fun KDiscordIPC.register(auth: ConnectionEvent.Connect.Login.EncryptionResponse) {
        on<ReadyEvent> {
            ready = this

            // Party features
            subscribe(DiscordEvent.ActivityJoinRequest)
            subscribe(DiscordEvent.ActivityJoin)

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
                    response.error?.let { warn("Failed to authenticate with the RPC server: ${it.message}") }
                    rpcAuth = response.data
                }

            if (enableParty) createParty()
        }

        // Event when someone would like to join your party
        on<ActivityJoinRequestEvent> {
            LOG.info("The user ${data.userId} has invited you")
            rpc.activityManager.acceptJoinRequest(data.userId)
        }

        // Event when someone joins your party
        on<ActivityJoinEvent> {
            LOG.info("Someone has joined")
        }

        on<ErrorEvent> {
            LOG.error("Discord RPC error: ${data.message}")
        }
    }

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
}
