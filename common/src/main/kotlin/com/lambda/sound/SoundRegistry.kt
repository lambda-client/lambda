package com.lambda.sound

import com.lambda.core.Loadable
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry

object SoundRegistry : Loadable {
    override fun load(): String {
        LambdaSound.entries.forEach {
            Registry.register(Registries.SOUND_EVENT, it.id, it.event)
        }

        return "Loaded ${LambdaSound.entries.size} sounds"
    }
}
