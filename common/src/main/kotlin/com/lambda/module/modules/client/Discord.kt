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
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafeConcurrently
import com.lambda.network.api.v1.models.Party
import com.lambda.module.Module
import com.lambda.module.modules.client.Network.updateToken
import com.lambda.module.tag.ModuleTag
import com.lambda.network.api.v1.endpoints.createParty
import com.lambda.network.api.v1.endpoints.editParty
import com.lambda.network.api.v1.endpoints.joinParty
import com.lambda.network.api.v1.endpoints.leaveParty
import com.lambda.network.api.v1.endpoints.linkDiscord
import com.lambda.threading.runConcurrent
import com.lambda.threading.runSafe
import com.lambda.util.Communication
import com.lambda.util.Communication.toast
import com.lambda.util.Communication.warn
import com.lambda.util.Nameable
import com.lambda.util.extension.dimensionName
import dev.cbyrne.kdiscordipc.KDiscordIPC
import dev.cbyrne.kdiscordipc.core.event.DiscordEvent
import dev.cbyrne.kdiscordipc.core.event.impl.ReadyEvent
import dev.cbyrne.kdiscordipc.core.packet.inbound.impl.AuthenticatePacket
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

    /* Party settings */
    private val createByDefault by setting("Create By Default", true, description = "Create parties on") { page == Page.Party }
    private val maxPlayers by setting("Max Players", 10, 2..20) { page == Page.Party }.onValueChange { _, _ -> if (player.isPartyOwner) edit() } // ToDo: Avoid spam requests

    val rpc = KDiscordIPC(Lambda.APP_ID, scope = EventFlow.lambdaScope)

    private var startup = System.currentTimeMillis()

    var discordAuth: AuthenticatePacket.Data? = null; private set
    var currentParty: Party? = null; private set

    val PlayerEntity.isPartyOwner
        get() = uuid == currentParty?.leader?.uuid

    val PlayerEntity.isInParty
        get() = currentParty?.players?.any { it.uuid == uuid }

    init {
        rpc.subscribe()

        listenUnsafeConcurrently<ConnectionEvent.Connect.Post> {
            // FixMe: We have to wait even though this is the last event until toSafe() != null
            //  because of timing
            delay(3000)
            runSafe { handleLoop() }
        }

        onEnable { runConcurrent { startDiscord(); handleLoop() } }
        onDisable { stopDiscord() }
    }

    fun createParty() {
        if (discordAuth == null) return toast("Can not interact with the api (are you offline?)")

        val (party, error) = createParty(maxPlayers, true)
        if (error != null) toast("Failed to create a party: ${error.message}", Communication.LogLevel.WARN)

        currentParty = party
    }

    /**
     * Joins a new party with the invitation ID
     */
    fun join(id: String) {
        if (discordAuth == null) return toast("Can not interact with the api (are you offline?)")

        val (party, error) = joinParty(id)
        if (error != null) toast("Failed to join the party: ${error.message}", Communication.LogLevel.WARN)

        currentParty = party
    }

    /**
     * Triggers a party edit request if you are the owner
     */
    fun edit() {
        if (discordAuth == null) return toast("Can not interact with the api (are you offline?)")

        val (party, error) = editParty(maxPlayers)
        if (error != null) toast("Failed to edit the party: ${error.message}", Communication.LogLevel.WARN)

        currentParty = party
    }

    /**
     * Leaves the current party
     */
    fun leave() {
        if (discordAuth == null || currentParty == null) return toast("Can not interact with the api (are you offline?)")

        val (_, error) = leaveParty()
        if (error != null) return toast("Failed to edit the party: ${error.message}", Communication.LogLevel.WARN)

        currentParty = null
    }

    private suspend fun startDiscord() {
        if (rpc.connected) return

        rpc.subscribe()
        runConcurrent { rpc.connect() } // TODO: Create a function that will wait until x seconds has passed or if the connection is successful
        delay(1000)

        val auth = rpc.applicationManager.authenticate()
        val (authResp, error) = linkDiscord(discordToken = auth.accessToken)
        if (error != null) {
            warn(error.message.toString())
            return toast("Failed to link the discord account to the minecraft auth")
        }

        authResp?.let { updateToken(it) }
        discordAuth = auth
    }

    private fun stopDiscord() {
        if (!rpc.connected) return

        rpc.disconnect()
    }

    private fun KDiscordIPC.subscribe() {
        // ToDO: Get party on join event
        on<ReadyEvent> {
            subscribe(DiscordEvent.VoiceChannelSelect)
            subscribe(DiscordEvent.VoiceStateCreate)
            subscribe(DiscordEvent.VoiceStateUpdate)
            subscribe(DiscordEvent.VoiceStateDelete)
            subscribe(DiscordEvent.VoiceSettingsUpdate)
            subscribe(DiscordEvent.VoiceConnectionStatus)
            subscribe(DiscordEvent.SpeakingStart)
            subscribe(DiscordEvent.SpeakingStop)
            subscribe(DiscordEvent.ActivityJoin)
            subscribe(DiscordEvent.ActivityJoinRequest)
            subscribe(DiscordEvent.OverlayUpdate)
            // subscribe(DiscordEvent.ActivitySpectate) // Unsupported
        }
    }

    private suspend fun SafeContext.handleLoop() {
        if (createByDefault) createParty(maxPlayers)

        while (rpc.connected) {
            update()
            delay(delay)
        }

        leave()
    }

    private suspend fun SafeContext.update() {
        val party = currentParty

        rpc.activityManager.setActivity {
            details = "${line1Left.value(this@update)} | ${line1Right.value(this@update)}".take(128)
            state = "${line2Left.value(this@update)} | ${line2Right.value(this@update)}".take(128)

            largeImage("lambda", Lambda.VERSION)
            smallImage("https://mc-heads.net/avatar/${mc.gameProfile.id}/nohelm", mc.gameProfile.name)

            if (party != null) {
                party(party.id.toString(), party.players.size, party.settings.maxPlayers)
                secrets(party.joinSecret)
            } else {
                button("Download", "https://github.com/lambda-client/lambda")
            }

            if (showTime) timestamps(startup)
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
        DIMENSION({ dimensionName }),
        COORDINATES({ "Coords: ${player.blockPos.toShortString()}" }),
        SERVER({ mc.currentServerEntry?.address ?: "Not Connected" }),
        FPS({ "${mc.currentFps} FPS" });
    }
}
