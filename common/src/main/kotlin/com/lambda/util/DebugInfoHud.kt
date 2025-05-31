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

package com.lambda.util

import com.lambda.Lambda
import com.lambda.Lambda.mc
import com.lambda.command.CommandRegistry
import com.lambda.event.EventFlow
import com.lambda.module.ModuleRegistry
import com.lambda.util.Formatting.asString
import com.lambda.util.extension.tickDelta
import net.minecraft.util.Formatting
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult

object DebugInfoHud {
    @JvmStatic
    fun MutableList<String>.addDebugInfo() {
        add("")
        add("" + Formatting.UNDERLINE + "Lambda ${Lambda.VERSION}+${mc.versionType}")
        add("Modules: ${ModuleRegistry.modules.size} with ${ModuleRegistry.modules.sumOf { it.settings.size }} settings")
        add("Commands: ${CommandRegistry.commands.size}")
        add("Synchronous Listeners: ${EventFlow.syncListeners.size}")
        add("Concurrent Listeners: ${EventFlow.concurrentListeners.size}")

        when (val hit = mc.crosshairTarget) {
            is BlockHitResult -> {
                add("Crosshair Target: Block")
                add("  Vec3d: %.5f, %.5f, %.5f".format(hit.pos.x, hit.pos.y, hit.pos.z))
                add("  BlockPos: ${hit.blockPos.toShortString()}")
                add("  Side: ${hit.side}")
            }

            is EntityHitResult -> {
                add("Crosshair Target: Entity")
                add("  Vec3d: ${hit.pos}")
                add("  Entity: ${hit.entity}")
            }

            null -> add("Crosshair Target: None")
        }

        add("Eye Pos: ${mc.cameraEntity?.getCameraPosVec(mc.tickDelta.toFloat())?.asString(3)}")

        return
    }
}
