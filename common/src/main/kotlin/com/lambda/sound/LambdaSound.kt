package com.lambda.sound

import com.lambda.sound.SoundManager.toIdentifier
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier

enum class LambdaSound(val id: Identifier) {
    BUTTON_CLICK("button_click".toIdentifier()),

    BOOLEAN_SETTING_ON("bool_on".toIdentifier()),
    BOOLEAN_SETTING_OFF("bool_off".toIdentifier()),

    MODULE_ON("module_on".toIdentifier()),
    MODULE_OFF("module_off".toIdentifier()),

    SETTINGS_OPEN("settings_open".toIdentifier()),
    SETTINGS_CLOSE("settings_close".toIdentifier());

    val event: SoundEvent = SoundEvent.of(id)
}
