package com.lambda.core

import com.lambda.core.SoundManager.toIdentifier
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier

enum class LambdaSound(val id: Identifier) {
    MODULE_TOGGLE("module_toggle".toIdentifier());

    val event: SoundEvent = SoundEvent.of(id)
}