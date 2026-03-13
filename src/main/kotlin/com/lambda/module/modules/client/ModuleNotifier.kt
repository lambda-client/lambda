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

import com.lambda.event.events.ModuleEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.threading.runSafe
import com.lambda.util.Communication.log
import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import net.minecraft.text.Text
import net.minecraft.util.Colors

object ModuleNotifier : Module(
	name = "ModuleNotifier",
	description = "Notifies you when a module is enabled or disabled",
	tag = com.lambda.module.tag.ModuleTag.CLIENT
) {
	var notifyTarget by setting("Notify Target", setOf<NotifyTarget>(NotifyTarget.ActionBar), NotifyTarget.entries.toSet(), description = "Where to send notifications when modules are toggled")

	enum class NotifyTarget(override val displayName: String, override val description: String) : Describable, NamedEnum {
		Chat("Chat", "Sends a message to chat when a module is toggled"),
		ActionBar("Action Bar", "Sends a message to the action bar when a module is toggled"),;
	}

	init {
		listen<ModuleEvent.Enabled> {
			runSafe {
				logToTargets(Text.literal("on").withColor(Colors.GREEN))
			}
		}

		listen<ModuleEvent.Disabled> {
			runSafe {
				logToTargets(Text.literal("off").withColor(Colors.RED))
			}
		}

		listen<ModuleEvent.Toggle> {
			runSafe {
				val newState = if (it.newValue) "on" else "off"
				val color = if (it.newValue) Colors.GREEN else Colors.RED
				val message = Text.literal(newState).withColor(color)
				logToTargets(message)
			}
		}
	}

	private fun logToTargets(message: Text) {
		if (notifyTarget.contains(NotifyTarget.Chat)) {
			log(message, source = name)
		}
		if (notifyTarget.contains(NotifyTarget.ActionBar)) {
			log(message, source = name, inGameOverlay = true)
		}
	}
}