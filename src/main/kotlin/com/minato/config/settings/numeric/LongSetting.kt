
package com.minato.config.settings.numeric

import com.minato.brigadier.argument.long
import com.minato.brigadier.argument.value
import com.minato.brigadier.execute
import com.minato.brigadier.required
import com.minato.config.Config
import com.minato.config.entries.SettingEntryLayer
import com.minato.config.settings.NumericSetting
import com.minato.gui.dsl.ImGuiBuilder
import com.lambda.imgui.type.ImInt
import com.minato.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess

/**
 * @see [com.minato.config.Config]
 */
class LongSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<NumericSetting<Long>, Long>,
	visibility: () -> Boolean,
	defaultValue: Long,
	override var range: ClosedRange<Long>,
	override var step: Long = 1,
	unit: String
) : NumericSetting<Long>(name, description, config, layer, defaultValue, visibility, range, step, unit) {
    override fun ImGuiBuilder.buildSlider() {
        // FixMe: No worky for super large numbers
        val maxIndex = ((range.endInclusive - range.start) / step).toInt()
        val currentIndex = ((value - range.start) / step).toInt()
        val imInt = ImInt(currentIndex)
        slider("##$name", imInt, 0, maxIndex, "") {
            value = (range.start + imInt.get() * step).coerceIn(range)
        }
    }

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(long(name, range.start, range.endInclusive)) { parameter ->
            execute {
                trySetValue(parameter().value())
            }
        }
    }
}