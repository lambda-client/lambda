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

package com.lambda.module.modules.chat

import com.google.common.collect.Comparators.min
import com.lambda.command.CommandRegistry.prefix
import com.lambda.event.events.ChatEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.handler.handlers.BaritoneHandler
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.NamedEnum

@Suppress("unused")
object CustomChat : Module(
	name = "CustomChat",
	description = "Adds a custom ending to your message",
	tag = ModuleTag.CHAT,
) {
	private val decoration by setting("Decoration", Decoration.Separator)
	private val text by setting("Text", Text.Lambda)
	private val customText by setting("Custom Text", "") { text == Text.Custom }

	init {
		listen<ChatEvent.Send> {
			val isBaritone = BaritoneHandler.baritoneSettings?.prefix?.value
				?.let { setting -> it.message.startsWith(setting)}
				?: false

			val isLambda = it.message.startsWith(prefix)

			if (isLambda || isBaritone)
				return@listen
			
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
		Lambda("Lambda", { "ʟᴀᴍʙᴅᴀ" }),
		LambdaOnTop("Lambda On Top", { "ʟᴀᴍʙᴅᴀ ᴏɴ ᴛᴏᴘ" }),
		KamiBlue("Kami Blue", { "ᴋᴀᴍɪ ʙʟᴜᴇ" }),
		LambdaWebsite("Lambda Website", { "ｌａｍｂｄａ－ｃｌｉｅｎｔ．ｏｒｇ" }),
		Custom("Custom", { customText })
	}
}