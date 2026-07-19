
package com.minato.sound

import com.minato.Minato.mc
import com.minato.core.Loadable
import com.minato.util.math.random
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.SimpleRegistry
import net.minecraft.sound.SoundEvent

object SoundHandler : Loadable {
    fun playSound(event: SoundEvent, pitch: Double = 1.0) {
        mc.soundManager.play(
            PositionedSoundInstance.master(event, pitch.toFloat())
        )
    }

    fun playSoundRandomly(event: SoundEvent, pitch: Double = 1.0, pitchRange: Double = 0.05) {
        val actualPitch = (pitch - pitchRange..pitch + pitchRange).random()

        mc.soundManager.play(
            PositionedSoundInstance.master(event, actualPitch.toFloat())
        )
    }

    fun MinatoSound.play() = playSoundRandomly(event)

    override fun load(): String {
        (Registries.SOUND_EVENT as SimpleRegistry)
            .frozen = false // fuck you

        MinatoSound.entries.forEach {
            Registry.register(Registries.SOUND_EVENT, it.id, it.event)
        }

        (Registries.SOUND_EVENT as SimpleRegistry)
            .frozen = true // fuck you


        return "Loaded ${MinatoSound.entries.size} sounds"
    }
}
