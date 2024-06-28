package com.lambda.core

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.command.CommandRegistry
import com.lambda.friend.FriendRegistry
import com.lambda.graphics.renderer.gui.font.LambdaFont
import com.lambda.graphics.renderer.gui.font.LambdaEmoji
import com.lambda.gui.GuiConfigurable
import com.lambda.gui.HudGuiConfigurable
import com.lambda.interaction.PlayerPacketManager
import com.lambda.interaction.RotationManager
import com.lambda.interaction.material.ContainerManager
import com.lambda.module.ModuleRegistry
import com.lambda.sound.SoundRegistry
import com.lambda.util.Communication.ascii
import kotlin.system.measureTimeMillis

object Loader {
    private val loadables = listOf(
        ModuleRegistry,
        CommandRegistry,
        RotationManager,
        PlayerPacketManager,
        LambdaFont.Loader,
        LambdaEmoji.Loader,
        GuiConfigurable,
        HudGuiConfigurable,
        FriendRegistry,
        SoundRegistry,
        TimerManager,
        PingManager,
        ContainerManager
    )

    fun initialize() {
        ascii.split("\n").forEach { LOG.info(it) }
        LOG.info("Initializing ${Lambda.MOD_NAME} ${Lambda.VERSION}")

        val initTime = measureTimeMillis {
            loadables.forEach { loadable ->
                val info: String
                val phaseTime = measureTimeMillis {
                    info = loadable.load()
                }

                LOG.info("$info in ${phaseTime}ms")
            }
        }

        LOG.info("${Lambda.MOD_NAME} ${Lambda.VERSION} was successfully initialized (${initTime}ms)")
    }
}
