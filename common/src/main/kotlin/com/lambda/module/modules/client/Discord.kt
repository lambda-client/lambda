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
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.SafeListener.Companion.listenConcurrently
import com.lambda.network.api.v1.models.Party
import com.lambda.module.Module
import com.lambda.module.modules.client.Network.isDiscordLinked
import com.lambda.module.modules.client.Network.updateToken
import com.lambda.module.tag.ModuleTag
import com.lambda.network.api.v1.endpoints.createParty
import com.lambda.network.api.v1.endpoints.deleteParty
import com.lambda.network.api.v1.endpoints.joinParty
import com.lambda.network.api.v1.endpoints.leaveParty
import com.lambda.network.api.v1.endpoints.linkDiscord
import com.lambda.network.api.v1.endpoints.partyUpdates
import com.lambda.threading.runConcurrent
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

    val rpc = KDiscordIPC(Lambda.APP_ID, scope = EventFlow.lambdaScope)

    private var startup = System.currentTimeMillis()

    var discordAuth: AuthenticatePacket.Data? = null; private set
    var currentParty: Party? = null; private set

    val PlayerEntity.isPartyOwner
        get() = uuid == currentParty?.leader?.uuid

    val PlayerEntity.isInParty: Boolean
        get() = currentParty?.players?.any { it.uuid == uuid } ?: false

    init {
        rpc.subscribe()

        // ToDo: Nametag for friends when ref/ui is merged
        // listen<RenderEvent.World>()

        listenConcurrently<WorldEvent.Join> {
            // If the player is in a party and this most likely means that the `onEnable`
            // block ran and is already handling the activity
            if (player.isInParty) return@listenConcurrently
            handleLoop()
        }

        onEnable { runConcurrent { start(); handleLoop() } }
        onDisable { stop() }
    }

    /**
     * Creates a new party, leaves or delete the current party if there is one
     */
    fun SafeContext.partyCreate() {
        if (!isDiscordLinked) return warn("You did not link your discord account")
        if (player.isInParty) {
            if (player.isPartyOwner) deleteParty() else leaveParty()
            return
        }

        val (party, error) = createParty()
        if (error != null) warn("Failed to create a party: ${error.errorData}")

        currentParty = party
        partyUpdates { currentParty = it }
    }

    /**
     * Joins a new party with the invitation ID
     */
    fun SafeContext.partyJoin(id: String) {
        if (!isDiscordLinked) return warn("You did not link your discord account")

        val (party, error) = joinParty(id)
        if (error != null) warn("Failed to join the party: ${error.errorData}")

        currentParty = party
        partyUpdates { currentParty = it }
    }

    /**
     * Leaves the current party
     */
    fun SafeContext.partyLeave() {
        if (!isDiscordLinked) return warn("You did not link your discord account")
        if (!player.isInParty) return warn("You are not in a party")

        val (_, error) = leaveParty()
        if (error != null) return warn("Failed to leave the party: ${error.errorData}")

        currentParty = null
    }

    /**
     * Deletes the current party
     */
    fun SafeContext.partyDelete() {
        if (!isDiscordLinked) return warn("You did not link your discord account")
        if (!player.isInParty) return warn("You are not in a party")

        val (_, error) = deleteParty()
        if (error != null) return warn("Failed to delete the party: ${error.errorData}")

        currentParty = null

    }

    private suspend fun start() {
        if (rpc.connected) return

        runConcurrent { rpc.connect() } // TODO: Create a function that will wait until x seconds has passed or if the connection is successful
        delay(1000)

        val auth = rpc.applicationManager.authenticate()
        val (authResp, error) = linkDiscord(discordToken = auth.accessToken)
        if (error != null) {
            warn(error.message.toString())
            return toast("Failed to link the discord account to the minecraft auth")
        }

        updateToken(authResp)
        discordAuth = auth
    }

    private fun stop() {
        if (rpc.connected) rpc.disconnect()
    }

    private fun KDiscordIPC.subscribe() {
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
        if (isDiscordLinked) {
            if (createByDefault) createParty()
            partyUpdates { currentParty = it }
        }

        while (rpc.connected) {
            update()
            delay(delay)
        }

        if (isDiscordLinked) leaveParty()
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
        FPS({ "${mc.currentFps} FPS" });
    }
}
