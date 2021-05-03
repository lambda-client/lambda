package com.lambda.client.module.modules.misc

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.audio.MusicTicker.MusicType
import net.minecraftforge.fml.common.gameevent.TickEvent


internal object MusicBox : Module(
    name = "MusicBox",
    category = Category.MISC,
    description = "Play music from creative mode, and other dimensions"
) {
    private val type by setting("Audio", Music.Creative)
    private var currentMusic = type
    private var setMusic = Music.Creative
    init {
        onEnable {
            mc.musicTicker.update()
        }

        onDisable {
            mc.musicTicker.update()
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (currentMusic == setMusic) return@safeListener
            when (type) {
                Music.Creative -> {
                    mc.musicTicker.playMusic(MusicType.CREATIVE)
                    mc.musicTicker.update()
                    setMusic = Music.Creative
                }
                Music.End -> {
                    mc.musicTicker.playMusic(MusicType.END)
                    mc.musicTicker.update()
                    setMusic = Music.End
                }
                Music.MainMenu -> {
                    mc.musicTicker.playMusic(MusicType.MENU)
                    mc.musicTicker.update()
                    setMusic = Music.MainMenu
                }
                Music.Nether -> {
                    mc.musicTicker.playMusic(MusicType.NETHER)
                    mc.musicTicker.update()
                    setMusic = Music.Nether
                }
                Music.Survival -> {
                    mc.musicTicker.playMusic(MusicType.GAME)
                    mc.musicTicker.update()
                    setMusic = Music.Survival
                }
            }
        }
    }

    private enum class Music {
        Creative, Survival, Nether, End, MainMenu
    }
}