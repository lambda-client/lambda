
package com.minato.config.settings.complex

import com.minato.brigadier.argument.integer
import com.minato.brigadier.argument.value
import com.minato.brigadier.execute
import com.minato.brigadier.optional
import com.minato.brigadier.required
import com.minato.config.Config
import com.minato.config.entries.Setting
import com.minato.config.entries.SettingEntryLayer
import com.minato.gui.dsl.ImGuiBuilder
import com.minato.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess
import java.awt.Color

class ColorSetting(
    name: String,
    description: String,
    config: Config,
    layer: SettingEntryLayer<ColorSetting, Color>,
    visibility: () -> Boolean,
    defaultValue: Color
) : Setting<Color>(name, description, defaultValue, layer, config, visibility) {
    override fun ImGuiBuilder.buildLayout() {
        colorEdit(name, ::value)
        minatoTooltip(description)
    }

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(integer("Red", 0, 255)) { red ->
            required(integer("Green", 0, 255)) { green ->
                required(integer("Blue", 0, 255)) { blue ->
                    optional(integer("Alpha", 0, 255)) { alpha ->
                        execute {
                            val alphaValue = alpha?.let { it().value() } ?: 255
                            trySetValue(Color(red().value(), green().value(), blue().value(), alphaValue))
                        }
                    }
                }
            }
        }
    }
}