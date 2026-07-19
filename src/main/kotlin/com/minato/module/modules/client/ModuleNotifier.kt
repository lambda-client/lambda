
package com.minato.module.modules.client

import com.minato.event.events.ModuleEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.CommunicationUtils.log
import com.minato.util.Describable
import com.minato.util.NamedEnum
import com.minato.util.text.TextBuilder
import com.minato.util.text.buildText
import com.minato.util.text.color
import com.minato.util.text.literal
import net.minecraft.util.Colors
import java.awt.Color

@Suppress("unused")
object ModuleNotifier : Module(
	name = "ModuleNotifier",
	description = "Notifies you when a module is enabled or disabled",
	tag = ModuleTag.CLIENT,
	enabledByDefault = true
) {
	var notifyTarget by setting("Notify Target", setOf<NotifyTarget>(NotifyTarget.ActionBar), NotifyTarget.entries.toSet(), description = "Where to send notifications when modules are toggled")

	enum class NotifyTarget(override val displayName: String, override val description: String) : Describable, NamedEnum {
		Chat("Chat", "Sends a message to chat when a module is toggled"),
		ActionBar("Action Bar", "Sends a message to the action bar when a module is toggled"), ;
	}

	init {
		listen<ModuleEvent.Enabled> { event ->
			logToTargets(event.module) {
				color(Color(Colors.GREEN)) {
					literal("on")
				}
			}
		}

		listen<ModuleEvent.Disabled> { event ->
			logToTargets(event.module) {
				color(Color(Colors.RED)) {
					literal("off")
				}
			}
		}
	}

	private fun logToTargets(module: Module, action: TextBuilder.() -> Unit) {
		if (NotifyTarget.Chat in notifyTarget) {
			module.log(buildText(action))
		}
		if (NotifyTarget.ActionBar in notifyTarget) {
			module.log(buildText(action), inGameOverlay = true)
		}
	}
}