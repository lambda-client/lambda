
package com.minato.sound

import com.minato.util.StringUtils.asIdentifier
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier

enum class MinatoSound(val id: Identifier) {
    ButtonClick("button_click".asIdentifier),

    BooleanSettingOn("bool_on".asIdentifier),
    BooleanSettingOff("bool_off".asIdentifier),

    ModuleOn("module_on".asIdentifier),
    ModuleOff("module_off".asIdentifier),

    SettingsOpen("settings_open".asIdentifier),
    SettingsClose("settings_close".asIdentifier);

    val event: SoundEvent = SoundEvent.of(id)
}
