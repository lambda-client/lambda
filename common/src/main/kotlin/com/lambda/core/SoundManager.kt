package com.lambda.core

import com.lambda.Lambda
import com.lambda.Lambda.mc
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier

object SoundManager : Loadable {

    override fun load(): String {
        LambdaSound.entries.forEach {
            Registry.register(Registries.SOUND_EVENT, it.id, it.event)
        }

        return "Loaded ${LambdaSound.entries.size} sounds"
    }

    fun playSound(event: SoundEvent, pitch: Float = 1.0f) {
        mc.soundManager.play(
            PositionedSoundInstance.master(event, pitch)
        )
    }

    fun String.toIdentifier() = Identifier(Lambda.MOD_ID, this)
}