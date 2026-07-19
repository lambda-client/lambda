
package com.minato.module.modules.chat

import com.google.common.collect.Comparators.min
import com.minato.command.CommandRegistry.prefix
import com.minato.event.events.ChatEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.NamedEnum

@Suppress("unused")
object CustomChat : Module(
	name = "CustomChat",
	description = "Adds a custom ending to your message",
	tag = ModuleTag.CHAT,
) {
	private val decoration by setting("Decoration", Decoration.Separator)
	private val text by setting("Text", Text.Minato)
	private val customText by setting("Custom Text", "") { text == Text.Custom }

	init {
		listen<ChatEvent.Send> {
if (it.message.startsWith(prefix)) return@listen
			
			val message = "${it.message} ${decoration.block(text.block())}"
			it.message = message.take(min(256, message.length))
		}
	}

	enum class Decoration(val block: (String) -> String) {
		Separator({ "| $it" }),
		Classic({ "\u00ab $it \u00bb" }),
		None({ it })
	}

	private enum class Text(override val displayName: String, val block: () -> String) : NamedEnum {
		Minato("Minato", { "ʟᴀᴍʙᴅᴀ" }),
		MinatoOnTop("Minato On Top", { "ʟᴀᴍʙᴅᴀ ᴏɴ ᴛᴏᴘ" }),
		KamiBlue("Kami Blue", { "ᴋᴀᴍɪ ʙʟᴜᴇ" }),
		MinatoWebsite("Minato Website", { "ｌａｍｂｄａ－ｃｌｉｅｎｔ．ｏｒｇ" }),
		Custom("Custom", { customText })
	}
}