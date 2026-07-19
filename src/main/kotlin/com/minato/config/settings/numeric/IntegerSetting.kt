
package com.minato.config.settings.numeric

import com.minato.brigadier.argument.integer
import com.minato.brigadier.argument.value
import com.minato.brigadier.execute
import com.minato.brigadier.required
import com.minato.config.Config
import com.minato.config.entries.SettingEntryLayer
import com.minato.config.settings.NumericSetting
import com.minato.gui.dsl.ImGuiBuilder
import com.minato.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess

/**
 * @see [com.minato.config.Config]
 */
class IntegerSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<NumericSetting<Int>, Int>,
	visibility: () -> Boolean,
	defaultValue: Int,
	override var range: ClosedRange<Int>,
	override var step: Int = 1,
	unit: String
) : NumericSetting<Int>(name, description, config, layer, defaultValue, visibility, range, step, unit) {
    override fun ImGuiBuilder.buildSlider() {
        slider("##$name", ::value, range.start, range.endInclusive, "")
    }

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(integer(name, range.start, range.endInclusive)) { parameter ->
            execute {
                trySetValue(parameter().value())
            }
        }
    }
}