package com.lambda.client.module.modules.misc

import com.lambda.client.LambdaMod
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.text.MessageSendHelper
import net.minecraft.client.audio.SoundHandler
import net.minecraft.client.audio.SoundManager
import net.minecraftforge.fml.relauncher.ReflectionHelper


internal object AntiSoundBug : Module(
    name = "AntiSoundBug",
    category = Category.MISC,
    description = "Attempts to fix sounds not playing"
) {
    private val sndManager = ReflectionHelper.getPrivateValue<SoundManager, SoundHandler>(SoundHandler::class.java, mc.soundHandler, "sndManager", "field_147694_f")

    init {
        onEnable {
            if (mc.player == null) disable()

            try {
                sndManager.reloadSoundSystem()
            } catch (e: Exception) {
                println("[ReloadSounds] Failed to reload sounds.")
                LambdaMod.LOG.error(e)
                disable()
            }
            MessageSendHelper.sendChatMessage("[ReloadSounds] Reloaded!")
        }
    }
}