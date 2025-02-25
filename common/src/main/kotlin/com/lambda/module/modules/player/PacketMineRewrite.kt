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

package com.lambda.module.modules.player

import com.lambda.config.groups.BuildSettings
import com.lambda.event.events.PlayerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.blueprint.StaticBlueprint
import com.lambda.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.tasks.BuildTask
import com.lambda.task.tasks.BuildTask.Companion.build

object PacketMineRewrite : Module(
    "Packet Mine Rewrite",
    "automatically breaks blocks, and does it faster",
    setOf(ModuleTag.PLAYER)
) {
    private val buildConfig = BuildSettings(this)

    var blueprint: StaticBlueprint? = null
    var task: BuildTask? = null

    init {
        listen<PlayerEvent.Attack.Block> {
            blueprint = setOf(player.blockPos.add(1, 0, 0), player.blockPos.add(1, 1, 0)).associateWith { TargetState.Air }.toBlueprint()
            task?.cancel()

            task = blueprint?.build(build = buildConfig)?.run()
        }
    }
}