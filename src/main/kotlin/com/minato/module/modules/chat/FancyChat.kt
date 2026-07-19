
package com.minato.module.modules.chat

import com.minato.command.CommandRegistry.prefix
import com.minato.event.events.ChatEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.ChatUtils.toBlue
import com.minato.util.ChatUtils.toGreen
import com.minato.util.ChatUtils.toLeet
import com.minato.util.ChatUtils.toUwu

@Suppress("unused")
object FancyChat : Module(
	name = "FancyChat",
	description = "Makes messages you send - fancy",
	tag = ModuleTag.CHAT,
) {
	private val uwu by setting("uwu", false)
	private val leet by setting("1337", false)
	private val green by setting(">", true)
	private val blue by setting("`", false)

	init {
		listen<ChatEvent.Send> {
if (it.message.startsWith(prefix)) return@listen

			if (uwu) it.message = it.message.toUwu
			if (leet) it.message = it.message.toLeet
			if (green) it.message = it.message.toGreen
			if (blue) it.message = it.message.toBlue
		}
	}
}