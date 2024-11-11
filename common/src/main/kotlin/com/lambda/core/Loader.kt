/*
 * Copyright 2024 Lambda
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
import com.lambda.util.FolderRegister
import kotlin.system.measureTimeMillis
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object Loader {
    private val started = System.currentTimeMillis()

    val runtime: String
        get() = "${(System.currentTimeMillis() - started).toDuration(DurationUnit.MILLISECONDS)}"

    private val loadables = listOf(
        FolderRegister,
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
