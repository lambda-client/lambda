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

package com.lambda.module.modules.client

import com.lambda.config.groups.BuildSettings
import com.lambda.config.groups.InteractionSettings
import com.lambda.config.groups.InventorySettings
import com.lambda.config.groups.RotationSettings
import com.lambda.core.PingManager
import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.result.Drawable
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.world.raycast.InteractionMask

object TaskFlowModule : Module(
    name = "TaskFlow",
    description = "Settings for task automation",
    defaultTags = setOf(ModuleTag.CLIENT, ModuleTag.AUTOMATION)
) {
    enum class Page {
        BUILD, ROTATION, INTERACTION, INVENTORY, DEBUG
    }

    private val page by setting("Page", Page.BUILD)
    val build = BuildSettings(this) { page == Page.BUILD }
    val rotation = RotationSettings(this) { page == Page.ROTATION }
    val interact = InteractionSettings(this, InteractionMask.BOTH) { page == Page.INTERACTION }

    // ToDo: remove
    // might be useless since grim has ping compensation things and shit
    // its better to make sure the interactions are 100% matching the minecraft's ones
    private val pingTimeout by setting("Ping Timeout", false, "Timeout on high ping") { page == Page.INTERACTION }
    private val inScopeThreshold by setting("Constant Timeout", 1, 0..20, 1, "How many ticks to wait after target box is in rotation scope"," ticks") {
        page == Page.INTERACTION && !pingTimeout
    }

    val scopeThreshold: Int
        get() =
            if (pingTimeout) PingManager.lastPing.toInt() / 50
            else inScopeThreshold

    val inventory = InventorySettings(this) { page == Page.INVENTORY }

    val showAllEntries by setting("Show All Entries", false, "Show all entries in the task tree") { page == Page.DEBUG }

    @Volatile
    var drawables = listOf<Drawable>()

    init {
        listen<RenderEvent.StaticESP> {
            drawables.toList().forEach { res ->
                with(res) { buildRenderer() }
            }
        }
    }


}
