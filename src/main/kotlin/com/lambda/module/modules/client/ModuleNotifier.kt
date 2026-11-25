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

package com.lambda.module.modules.client

import com.lambda.Lambda.mc
import com.lambda.config.settings.collections.collectionSetting
import com.lambda.event.events.ModuleEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.handler.handlers.NotificationHandler
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import com.lambda.util.CommunicationUtils.log
import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import com.lambda.util.text.TextBuilder
import com.lambda.util.text.buildText
import com.lambda.util.text.color
import com.lambda.util.text.literal
import net.minecraft.util.Colors
import java.awt.Color

@Suppress("unused")
object ModuleNotifier : Module(
	name = "ModuleNotifier",
	description = "Notifies you when a module is enabled or disabled",
	tag = ModuleTag.CLIENT,
	enabledByDefault = true
) {
	var notifyTarget by collectionSetting("Notify Target", setOf(NotifyTarget.Notification), NotifyTarget.entries.toSet(), description = "Where to send notifications when modules are toggled")

	enum class NotifyTarget(override val displayName: String, override val description: String) : Describable, NamedEnum {
		Chat("Chat", "Sends a message to chat when a module is toggled"),
		ActionBar("Action Bar", "Sends a message to the action bar when a module is toggled"),
		Notification("Notification", "Slides a notification in at the lower right of the screen when a module is toggled"), ;
	}

	init {
		listenUnsafe<ModuleEvent.Enabled> { event ->
			logToTargets(event.module, "Enabled", Color(Colors.GREEN)) {
				color(Color(Colors.GREEN)) {
					literal("on")
				}
			}
		}

		listenUnsafe<ModuleEvent.Disabled> { event ->
			logToTargets(event.module, "Disabled", Color(Colors.RED)) {
				color(Color(Colors.RED)) {
					literal("off")
				}
			}
		}
	}

	private fun logToTargets(module: Module, state: String, color: Color, action: TextBuilder.() -> Unit) {
		if (NotifyTarget.Notification in notifyTarget) {
			NotificationHandler.notify(module.name, state, color)
		}
		if (mc.player == null) return
		if (NotifyTarget.Chat in notifyTarget) {
			module.log(buildText(action))
		}
		if (NotifyTarget.ActionBar in notifyTarget) {
			module.log(buildText(action), inGameOverlay = true)
		}
	}
}