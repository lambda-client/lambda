
package com.minato.config.settings

import com.minato.brigadier.argument.greedyString
import com.minato.brigadier.argument.value
import com.minato.brigadier.execute
import com.minato.brigadier.required
import com.minato.config.Config
import com.minato.config.ConfigEditor
import com.minato.config.ConfigEditorD5l
import com.minato.config.entries.Setting
import com.minato.config.entries.SettingEntryLayer
import com.minato.gui.dsl.ImGuiBuilder
import com.lambda.imgui.flag.ImGuiInputTextFlags
import com.minato.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess

/**
 * @see [com.minato.config.Config]
 */
class StringSetting(
    name: String,
    description: String,
    config: Config,
    layer: SettingEntryLayer<StringSetting, String>,
    defaultValue: String,
    visibility: () -> Boolean,
    var multiline: Boolean = false,
    var flags: Int = ImGuiInputTextFlags.None,
) : Setting<String>(name, description, defaultValue, layer, config, visibility) {

    override fun ImGuiBuilder.buildLayout() {
        if (multiline) {
            inputTextMultiline(name, ::value, flags = flags)
        } else {
            inputText(name, ::value, flags)
        }
        minatoTooltip(description)
    }

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(greedyString(name)) { parameter ->
            execute {
                trySetValue(parameter().value())
            }
        }
    }

    @Suppress("unused", "unchecked_cast")
    companion object {
        @ConfigEditorD5l
        fun ConfigEditor.SettingEditBuilder<String>.multiline(multiline: Boolean) {
            (entries as Collection<StringSetting>).forEach { it.multiline = multiline }
        }

        @ConfigEditorD5l
        fun ConfigEditor.SettingEditBuilder<String>.flags(flags: Int) {
            (entries as Collection<StringSetting>).forEach { it.flags = flags }
        }
    }
}