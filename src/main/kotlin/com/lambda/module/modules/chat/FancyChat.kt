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

import com.lambda.command.CommandRegistry.prefix
import com.lambda.event.events.ChatEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.handler.handlers.BaritoneHandler
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import com.lambda.util.ChatUtils.toBlue
import com.lambda.util.ChatUtils.toGreen
import com.lambda.util.ChatUtils.toLeet
import com.lambda.util.ChatUtils.toUwu

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
			val isBaritone = BaritoneHandler.baritoneSettings?.prefix?.value
				?.let { setting -> it.message.startsWith(setting)}
				?: false

			val isLambda = it.message.startsWith(prefix)

			if (isLambda || isBaritone)
				return@listen

			if (uwu) it.message = it.message.toUwu
			if (leet) it.message = it.message.toLeet
			if (green) it.message = it.message.toGreen
			if (blue) it.message = it.message.toBlue
		}
	}
}