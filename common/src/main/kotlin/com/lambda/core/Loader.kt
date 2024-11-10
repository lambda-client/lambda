package com.lambda.core

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.command.CommandRegistry
import com.lambda.friend.FriendRegistry
import com.lambda.graphics.renderer.gui.font.LambdaEmoji
import com.lambda.graphics.renderer.gui.font.LambdaFont
import com.lambda.gui.GuiConfigurable
import com.lambda.gui.HudGuiConfigurable
import com.lambda.interaction.PlayerPacketManager
import com.lambda.interaction.RotationManager
import com.lambda.interaction.construction.StructureRegistry
import com.lambda.interaction.material.ContainerManager
import com.lambda.module.ModuleRegistry
import com.lambda.sound.SoundRegistry
import com.lambda.util.Communication.ascii
import kotlin.system.measureTimeMillis
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object Loader {
    private val started = System.currentTimeMillis()

    val runtime: String
        get() = "${(System.currentTimeMillis() - started).toDuration(DurationUnit.MILLISECONDS)}"

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
        ContainerManager,
        StructureRegistry
    )

    fun initialize() {
        ascii.split("\n").forEach { LOG.info(it) }
        LOG.info("Initializing ${Lambda.MOD_NAME} ${Lambda.VERSION}")

        val initTime = measureTimeMillis {
            loadables.forEach { LOG.info(it.load()) }
        }

        LOG.info("${Lambda.MOD_NAME} ${Lambda.VERSION} was successfully initialized (${initTime}ms)")
    }
}
