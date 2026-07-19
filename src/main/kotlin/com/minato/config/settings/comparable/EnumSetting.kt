
package com.minato.config.settings.comparable

import com.minato.brigadier.CommandResult.Companion.failure
import com.minato.brigadier.CommandResult.Companion.success
import com.minato.brigadier.argument.value
import com.minato.brigadier.argument.word
import com.minato.brigadier.executeWithResult
import com.minato.brigadier.required
import com.minato.config.Config
import com.minato.config.entries.Setting
import com.minato.config.entries.SettingEntryLayer
import com.minato.gui.dsl.ImGuiBuilder
import com.minato.util.Describable
import com.minato.util.StringUtils.capitalize
import com.minato.util.extension.CommandBuilder
import com.minato.util.extension.displayValue
import net.minecraft.command.CommandRegistryAccess

class EnumSetting<T : Enum<T>>(
    name: String,
    description: String,
    config: Config,
    layer: SettingEntryLayer<EnumSetting<T>, T>,
    visibility: () -> Boolean,
    defaultValue: T
) : Setting<T>(name, description, defaultValue, layer, config, visibility) {
    override fun ImGuiBuilder.buildLayout() {
        val values = value.enumValues
        val currentDisplay = value.displayValue
        val currentIndex = value.ordinal

        combo("##$name", preview = "$name: $currentDisplay") {
            values.forEachIndexed { idx, v ->
                val isSelected = idx == currentIndex

                selectable(v.displayValue, isSelected) {
                    if (!isSelected) value = values[idx % values.size]
                }

                (v as? Describable)?.let { minatoTooltip(it.description) }
            }
        }

        minatoTooltip(description)
    }

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(word(name)) { parameter ->
            suggests { _, builder ->
                value.enumValues.forEach { builder.suggest(it.name.capitalize()) }
                builder.buildFuture()
            }
            executeWithResult {
                val newValue = value.enumValues.find { it.name.equals(parameter().value(), true) }
                    ?: return@executeWithResult failure("Invalid value")
                trySetValue(newValue)
                return@executeWithResult success()
            }
        }
    }

    companion object {
        val <T : Enum<T>> T.enumValues: Array<T> get() =
            declaringJavaClass.enumConstants
    }
}