/*
 * Copyright 2026 Lambda
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

package com.lambda.module.modules.network

import com.lambda.Lambda.mc
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen
import net.minecraft.client.gui.screen.world.SelectWorldScreen
import net.minecraft.client.network.CookieStorage
import net.minecraft.client.network.ServerAddress
import net.minecraft.client.network.ServerInfo

@Suppress("unused")
object AutoReconnect : Module(
    name = "AutoReconnect",
    description = "Automatically reconnects to the last server or world after disconnecting",
    tag = ModuleTag.NETWORK,
) {
    val delay by setting("Delay", 5.0, 0.5..60.0, 0.5, unit = "s", description = "Time in seconds before automatically reconnecting.")
    val singleplayer by setting("Singleplayer", false, description = "Whether to auto-reconnect to singleplayer worlds.")

    @JvmStatic
    var lastReconnectTarget: ReconnectTarget? = null

    @JvmStatic
    fun canAutoReconnect(): Boolean {
        if (!isEnabled) return false
        val target = lastReconnectTarget ?: return false
        return when (target) {
            is MultiplayerReconnectTarget -> true
            is SingleplayerReconnectTarget -> singleplayer
        }
    }

    @JvmStatic
    fun reconnect(parent: Screen) {
        when (val target = lastReconnectTarget) {
            is MultiplayerReconnectTarget -> {
                ConnectScreen.connect(parent, mc, target.address, target.info, false, target.cookieStorage)
            }

            is SingleplayerReconnectTarget -> {
                mc.createIntegratedServerLoader().start(target.levelName) {
                    mc.setScreen(SelectWorldScreen(parent))
                }
            }

            null -> return
        }
    }
}

sealed interface ReconnectTarget

data class MultiplayerReconnectTarget(
    val address: ServerAddress,
    val info: ServerInfo,
    val cookieStorage: CookieStorage?
) : ReconnectTarget

data class SingleplayerReconnectTarget(
    val levelName: String
) : ReconnectTarget