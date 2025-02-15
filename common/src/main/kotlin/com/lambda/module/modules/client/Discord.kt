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
import com.lambda.context.SafeContext
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafeConcurrently
import com.lambda.network.api.v1.models.Party
import com.lambda.module.Module
import com.lambda.module.modules.client.Network.discordAuth
import com.lambda.module.modules.client.Network.isAuthenticated
import com.lambda.module.modules.client.Network.rpc
import com.lambda.module.modules.client.Network.apiAuth
import com.lambda.module.tag.ModuleTag
import com.lambda.network.api.v1.endpoints.createParty
import com.lambda.network.api.v1.endpoints.editParty
import com.lambda.network.api.v1.endpoints.joinParty
import com.lambda.network.api.v1.endpoints.leaveParty
import com.lambda.threading.runConcurrent
import com.lambda.threading.runSafe
import com.lambda.util.Communication
import com.lambda.util.Communication.toast
import com.lambda.util.Nameable
import com.lambda.util.StringUtils.capitalize
import dev.cbyrne.kdiscordipc.KDiscordIPC
import dev.cbyrne.kdiscordipc.core.event.DiscordEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ActivityJoinEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ActivityJoinRequestEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ErrorEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ReadyEvent
import dev.cbyrne.kdiscordipc.data.activity.*
import kotlinx.coroutines.delay
import net.minecraft.entity.player.PlayerEntity

object Discord : Module(
    name = "Discord",
    description = "Discord Rich Presence configuration",
    defaultTags = setOf(ModuleTag.CLIENT),
//    enabledByDefault = true, // ToDo: Bring this back on beta release
) {
    private val page by setting("Page", Page.General)

    /* General settings */
    private val delay by setting("Update Delay", 15000L, 15000L..30000L, 100L, unit = "ms") { page == Page.General }
    private val showTime by setting("Show Time", true, description = "Show how long you have been playing for.") { page == Page.General }
    private val line1Left by setting("Line 1 Left", LineInfo.WORLD) { page == Page.General }
    private val line1Right by setting("Line 1 Right", LineInfo.USERNAME) { page == Page.General }
    private val line2Left by setting("Line 2 Left", LineInfo.DIMENSION) { page == Page.General }
    private val line2Right by setting("Line 2 Right", LineInfo.FPS) { page == Page.General }
    private val confirmCoordinates by setting("Show Coordinates", false, description = "Confirm display the player coordinates") { page == Page.General }
    private val confirmServer by setting("Show Server IP", false, description = "Confirm display the server IP") { page == Page.General }

    /* Party settings */
    private val enableParty by setting("Enable Party", true, description = "Allows you to create parties.") { page == Page.Party } // ToDo: Change this for create by default instead
    private val maxPlayers by setting("Max Players", 10, 2..20) { page == Page.Party }.onValueChange { _, _ -> if (player.isPartyOwner) edit() } // ToDo: Avoid spam requests

    private var startup = System.currentTimeMillis()
    private val dimensionRegex = Regex("""\b\w+_\w+\b""") // ToDo: Change this when combat is merged

    private var ready: ReadyEvent? = null
    private var currentParty: Party? = null

    private val isPartyInteractionAllowed: Boolean
        get() = apiAuth != null && discordAuth != null

    val PlayerEntity.isPartyOwner
        get() = uuid == currentParty?.leader?.uuid

    val PlayerEntity.isInParty
        get() = currentParty?.players?.any { it.uuid == this.uuid }

    init {
        listenUnsafeConcurrently<ConnectionEvent.Connect.Post> {
            // FixMe: We have to wait even though this is the last event until toSafe() != null
            //  because of timing
            delay(3000)
            runSafe { connect() }
        }

        // TODO: Exponential backoff up to 25 seconds to avoid being rate limited by discord
        onEnable { connect() }
        onDisable { disconnect() }
    }

    fun createParty() {
        if (!isPartyInteractionAllowed) return

        val (party, error) = createParty(maxPlayers, true)
        if (error != null) toast("Failed to create a party: ${error.message}", Communication.LogLevel.WARN)

        currentParty = party
    }

    // Join a party using the ID
    fun join(id: String) {
        if (!isPartyInteractionAllowed) return

        val (party, error) = joinParty(id)
        if (error != null) toast("Failed to join the party: ${error.message}", Communication.LogLevel.WARN)

        currentParty = party
    }

    // Edit the current party if you are the owner
    private fun edit() {
        if (!isPartyInteractionAllowed) return

        val (party, error) = editParty(maxPlayers)
        if (error != null) toast("Failed to edit the party: ${error.message}", Communication.LogLevel.WARN)

        currentParty = party
    }

    private fun SafeContext.connect() {
        // FixMe: Race condition
        runConcurrent { rpc.connect() } // ToDo: Duplicate rpc connection network and discord
        runConcurrent { rpc.register() }
        runConcurrent {
            while (rpc.connected) {
                update()
                delay(delay)
            }
        }
    }

    private fun disconnect() {
        if (rpc.connected) {
            LOG.info("Gracefully disconnecting from Discord RPC.")
            leaveParty()
            rpc.disconnect()
        }

        ready = null
        currentParty = null
    }

    private suspend fun SafeContext.update() {
        val party = currentParty

        rpc.activityManager.setActivity {
            details = "${line1Left.value(this@update)} | ${line1Right.value(this@update)}".take(128)
            state = "${line2Left.value(this@update)} | ${line2Right.value(this@update)}".take(128)

            largeImage("lambda", Lambda.VERSION)
            smallImage("https://mc-heads.net/avatar/${mc.gameProfile.id}/nohelm", mc.gameProfile.name)

            if (isAuthenticated && party != null) {
                party(party.id.toString(), party.players.size, party.settings.maxPlayers)
                secrets(party.joinSecret)
            } else {
                button("Download", "https://github.com/lambda-client/lambda")
            }

            if (showTime) timestamps(startup)
        }
    }

    private suspend fun KDiscordIPC.register() {
        on<ReadyEvent> {
            ready = this

            // Party features
            subscribe(DiscordEvent.ActivityJoinRequest)
            subscribe(DiscordEvent.ActivityJoin)

            if (enableParty) createParty()
        }

        // Event when someone would like to join your party
        on<ActivityJoinRequestEvent> {
            toast("The user ${data.userId} has invited you")
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
        General, Party
    }

    private enum class LineInfo(val value: SafeContext.() -> String) : Nameable {
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
}
