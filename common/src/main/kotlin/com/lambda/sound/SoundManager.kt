/*
 * Copyright 2025 Lambda
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
import com.lambda.util.math.random
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.sound.SoundEvent

object SoundManager {
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
}
