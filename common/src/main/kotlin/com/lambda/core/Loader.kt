package com.lambda.core

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.command.CommandManager
import com.lambda.graphics.renderer.gui.font.LambdaFont
import com.lambda.gui.impl.clickgui.GuiConfigurable
import com.lambda.interaction.PlayerPacketManager
import com.lambda.interaction.RotationManager
import com.lambda.module.ModuleRegistry
import com.lambda.plugin.PluginRegistry
import com.lambda.util.Communication.ascii
import com.lambda.util.FolderRegister.plugins
import kotlin.system.measureTimeMillis

object Loader {
    private val loadables = listOf(
        ModuleRegistry,
        CommandManager,
        RotationManager,
        PlayerPacketManager,
        LambdaFont.Loader,
        GuiConfigurable,
        FriendManager,
    )

    fun initialize() {
        ascii.split("\n").forEach { LOG.info(it) }
        LOG.info("Initializing ${Lambda.MOD_NAME} ${Lambda.VERSION}")

        PluginRegistry.load(plugins) // TODO: Find something else

        val initTime = measureTimeMillis {
            loadables.forEach { loadable ->
                var info: String
                val phaseTime = measureTimeMillis {
                    info = loadable.load()
                }

                LOG.info("$info in ${phaseTime}ms")
            }
        }

        LOG.info("${Lambda.MOD_NAME} ${Lambda.VERSION} was successfully initialized (${initTime}ms)")
    }
}
