
package com.minato.config.settings.complex

import com.minato.brigadier.argument.double
import com.minato.brigadier.argument.value
import com.minato.brigadier.execute
import com.minato.brigadier.required
import com.minato.config.Config
import com.minato.config.entries.Setting
import com.minato.config.entries.SettingEntryLayer
import com.minato.gui.dsl.ImGuiBuilder
import com.minato.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.util.math.Vec3d

class Vec3dSetting(
    name: String,
    description: String,
    config: Config,
    layer: SettingEntryLayer<Vec3dSetting, Vec3d>,
    visibility: () -> Boolean,
    defaultValue: Vec3d
) : Setting<Vec3d>(name, description, defaultValue, layer, config, visibility) {
    override fun ImGuiBuilder.buildLayout() {
        inputVec3d(name, ::value as Vec3d) // FixMe: what the fuck
        minatoTooltip(description)
    }

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(double("X", -30000000.0, 30000000.0)) { x ->
            required(double("Y", -64.0, 255.0)) { y ->
                required(double("Z", -30000000.0, 30000000.0)) { z ->
                    execute {
                        trySetValue(Vec3d(x().value(), y().value(), z().value()))
                    }
                }
            }
        }
    }
}