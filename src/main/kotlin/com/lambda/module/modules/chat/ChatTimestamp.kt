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

import com.lambda.config.applyEdits
import com.lambda.config.groups.FormatterConfig
import com.lambda.config.groups.FormatterSettings
import com.lambda.event.events.ChatEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.FormattingUtils.format
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import com.lambda.util.text.styled
import com.lambda.util.text.text
import net.minecraft.util.Formatting
import java.awt.Color
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@Suppress("unused")
object ChatTimestamp : Module(
	name = "ChatTimestamp",
	description = "Displays the time a message was sent next to it",
	tag = ModuleTag.CHAT,
) {
	private var color: Formatting by setting("Color", Formatting.GRAY)
		.onValueChange { from, to -> if (to.colorIndex !in 0..15) color = from }
	private val javaColor: Color get() = Color(color.colorValue!! and 16777215)

	val formatter = settingBlock(FormatterSettings(this)) {
		applyEdits {
			hide(::localeEnum, ::sep, ::customSep, ::floatingPrecision)
			editTyped(::timeFormat) { defaultValue(FormatterConfig.Time.IsoLocalTime) }
		}
	}

	private val currentTime get() =
		ZonedDateTime.of(LocalDateTime.now(), ZoneId.systemDefault())
			.truncatedTo(ChronoUnit.SECONDS)

	init {
		listen<ChatEvent.Receive> {
			it.message = buildText {
				text(it.message)
				literal(" ")
				styled(javaColor, italic = true) { literal(currentTime.format(formatter)) }
			}
		}
	}
}