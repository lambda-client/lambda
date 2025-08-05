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
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.request.breaking.BreakRequest
import com.lambda.interaction.request.rotating.Rotation.Companion.rotation
import com.lambda.interaction.request.rotating.RotationRequest
import com.lambda.interaction.request.rotating.visibilty.lookAt
import com.lambda.module.Module
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.module.tag.ModuleTag
import com.lambda.util.BlockUtils.blockState
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import java.util.concurrent.ConcurrentLinkedQueue

object FastBreak : Module(
    name = "FastBreak",
    description = "Break blocks faster.",
    defaultTags = setOf(
        ModuleTag.PLAYER, ModuleTag.WORLD
    )
) {
    private val buildConfig = BuildSettings(this)

    private val pendingInteractions = ConcurrentLinkedQueue<BuildContext>()

    init {
        listen<PlayerEvent.Attack.Block> { it.cancel() }
        listen<PlayerEvent.Breaking.Update> { event ->
            event.cancel()
            player.swingHand(Hand.MAIN_HAND)

            val hitResult = mc.crosshairTarget as? BlockHitResult ?: return@listen
            val pos = event.pos
            val state = blockState(pos)

            val breakContext = BreakContext(
                hitResult,
                RotationRequest(lookAt(player.rotation), TaskFlowModule.rotation),
                player.inventory.selectedSlot,
                player.mainHandStack.select(),
                state.calcBlockBreakingDelta(player, world, pos) >= buildConfig.breaking.breakThreshold,
                state,
                buildConfig.breaking.sorter
            )

            BreakRequest(setOf(breakContext), pendingInteractions, buildConfig).submit()
        }
    }
}
