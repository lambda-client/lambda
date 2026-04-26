/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.sound

import com.lambda.Lambda.mc
import com.lambda.core.Loadable
import com.lambda.util.math.random
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

    fun LambdaSound.play() = playSoundRandomly(event)

    override fun load(): String {
        (Registries.SOUND_EVENT as SimpleRegistry)
            .frozen = false // fuck you

        LambdaSound.entries.forEach {
            Registry.register(Registries.SOUND_EVENT, it.id, it.event)
        }

        (Registries.SOUND_EVENT as SimpleRegistry)
            .frozen = true // fuck you


        return "Loaded ${LambdaSound.entries.size} sounds"
    }
}
