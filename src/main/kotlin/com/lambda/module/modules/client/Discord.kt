/*
 * Copyright 2025 Lambda
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
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listenOnce
import com.lambda.event.listener.UnsafeListener.Companion.listenOnceUnsafe
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runConcurrent
import com.lambda.util.Nameable
import com.lambda.util.extension.dimensionName
import com.lambda.util.extension.fullHealth
import com.lambda.util.extension.worldName
import dev.firstdark.rpc.DiscordRpc
import dev.firstdark.rpc.handlers.RPCEventHandler
import dev.firstdark.rpc.models.DiscordRichPresence
import kotlinx.coroutines.delay

object Discord : Module(
    name = "Discord",
    description = "Discord Rich Presence configuration",
    tag = ModuleTag.CLIENT,
    enabledByDefault = true,
) {
    private val delay by setting("Update Delay", 5000L, 5000L..30000L, 100L, unit = "ms")
    private val showTime by setting("Show Time", true, description = "Show how long you have been playing for.")
    private val line1Left by setting("Line 1 Left", LineInfo.World)
    private val line1Right by setting("Line 1 Right", LineInfo.Username)
    private val line2Left by setting("Line 2 Left", LineInfo.Version)
    private val line2Right by setting("Line 2 Right", LineInfo.Fps)

    val rpc = DiscordRpc()
    private var startup = System.currentTimeMillis()

//    var discordAuth: AuthenticatePacket.Data? = null; private set

    init {
        listenOnce<WorldEvent.Join> {
            runConcurrent {
                start()
                update()
            }

            return@listenOnce true
        }

        onEnable { runConcurrent { start(); update() } }
        onDisable { stop() }
    }

    private fun start() {
        rpc.init(Lambda.APP_ID, RPCEventHandler(), true)
//        val auth = rpc.applicationManager.authenticate()
//
//        linkDiscord(discordToken = auth.accessToken)
//            .onSuccess { updateToken(it); discordAuth = auth }
//            .onFailure { LOG.error(it); warn("Failed to link your discord account") }
    }

    private fun stop() {
        rpc.shutdown()
    }

    private suspend fun SafeContext.update() {
        val presence = DiscordRichPresence
            .builder()
            .details("${line1Left.value(this)} | ${line1Right.value(this)}".take(128))
            .state("${line2Left.value(this)} | ${line2Right.value(this)}".take(128))
            .largeImageKey("lambda")
            .largeImageText(Lambda.VERSION)
            .smallUrl("https://mc-heads.net/avatar/${mc.gameProfile.id}/nohelm")
            .smallImageText(mc.gameProfile.name)
            .button(DiscordRichPresence.RPCButton.of("Download", "https://github.com/lambda-client/lambda"))

        if (showTime)
            presence.startTimestamp(startup)

        rpc.updatePresence(presence.build())

        delay(delay)
    }

    private enum class LineInfo(val value: SafeContext.() -> String) : Nameable {
        Version({ Lambda.VERSION }),
        World({ worldName }),
        Username({ mc.session.username }),
        Health({ "${player.fullHealth} HP" }),
        Hunger({ "${player.hungerManager.foodLevel} Hunger" }),
        Dimension({ world.dimensionName }),
        Fps({ "${mc.currentFps} FPS" });
    }
}
