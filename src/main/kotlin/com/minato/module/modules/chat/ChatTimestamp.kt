
package com.minato.module.modules.chat

import com.minato.config.ConfigEditor.editTypedSettings
import com.minato.config.ConfigEditor.hide
import com.minato.config.blocks.FormatterConfig
import com.minato.config.blocks.FormatterSettings
import com.minato.config.entries.Setting.Companion.onValueChange
import com.minato.config.withEdits
import com.minato.event.events.ChatEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.FormattingUtils.format
import com.minato.util.text.buildText
import com.minato.util.text.literal
import com.minato.util.text.styled
import com.minato.util.text.text
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

	val formatter by configBlock(FormatterSettings(this))
		.withEdits {
			hide(::localeEnum, ::sep, ::customSep, ::floatingPrecision)
			editTypedSettings(::timeFormat) { defaultValue(FormatterConfig.Time.IsoLocalTime) }
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