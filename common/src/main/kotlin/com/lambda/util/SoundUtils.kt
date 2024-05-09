package com.lambda.util

import com.lambda.context.SafeContext
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.sound.SoundEvent

object SoundUtils {
    fun SafeContext.playSound(event: SoundEvent, pitch: Float = 1.0f) {
        mc.soundManager.play(
            PositionedSoundInstance.master(event, pitch)
        )
    }
}