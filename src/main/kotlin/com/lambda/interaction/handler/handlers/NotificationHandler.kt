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

package com.lambda.interaction.handler.handlers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lambda.Lambda.mc
import com.lambda.core.Loadable
import com.lambda.event.events.ComposeEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.gui.GuiConfig
import com.lambda.gui.Notifications
import com.lambda.gui.core.RenderThread
import java.awt.Color

object NotificationHandler : Loadable {
    private const val MAX_KEPT = 40

    private val kept = mutableStateListOf<Notification>()
    val notifications: List<Notification> get() = kept

    init {
        listenUnsafe<ComposeEvent.Hud.Post> { event ->
            if (kept.isNotEmpty()) event.content { Notifications() }
        }
    }

    override fun load() = "Loaded Notification Handler"

    fun notify(title: String, message: String = "", accent: Color? = null) =
        mc.execute {
            val notification = Notification(title, message, accent)
            kept += notification
            if (kept.size > MAX_KEPT) kept.removeRange(0, kept.size - MAX_KEPT)
            kept.filterNot { it.dismissed }
                .dropLast(GuiConfig.maxNotifications)
                .forEach { it.dismissed = true }
            RenderThread.after((GuiConfig.notificationSeconds * 1000).toLong()) {
                notification.dismissed = true
            }
        }

    fun remove(notification: Notification) {
        kept -= notification
    }

    class Notification(val title: String, val message: String, val accent: Color?) {
        var dismissed by mutableStateOf(false)
            internal set
    }
}
