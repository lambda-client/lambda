package com.lambda.sound

import com.lambda.core.Loadable
import com.lambda.core.registry.RegistryController
import net.minecraft.registry.Registries

object SoundRegistry : Loadable {
    override fun load(): String {
        LambdaSound.entries.forEach {
            RegistryController.register(Registries.SOUND_EVENT, it.id, it.event)
        }

        return "Loaded ${LambdaSound.entries.size} sounds"
    }
}
