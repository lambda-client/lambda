/*
 * Copyright 2026 Lambda
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

package com.lambda.module.tag

import com.lambda.util.Nameable

/**
 * The [ModuleTag] class represents a tag, that can be associated in any cardinality with a [Module].
 *
 * Tags are used to categorize and organize modules, making them easier to find.
 * They can be custom created as per the user's needs.
 *
 * Additionally, [ModuleTag] can be used to create groups of tags, which can be useful for creating new GUI windows.
 *
 * The companion object provides a set of predefined `ModuleTag` instances for common categories like "Combat",
 * "Movement", "Render", etc.
 *
 * @param name The name of the tag.
 */
data class ModuleTag(override val name: String) : Nameable {
    // Totally needs to be reworked
    // ToDo: Add registry for tags
    companion object {
        val Combat = ModuleTag("Combat")
        val Movement = ModuleTag("Movement")
        val Render = ModuleTag("Render")
        val Player = ModuleTag("Player")
        val World = ModuleTag("World")
        val Chat = ModuleTag("Chat")
        val Client = ModuleTag("Client")
        val Network = ModuleTag("Network")
        val Debug = ModuleTag("Debug")
        val Hud = ModuleTag("Hud")

        val defaults = setOf(Combat, Movement, Render, Player, World, Network, Chat, Client, Hud)

        val shownTags = defaults.toMutableSet()

        fun toggleTag(tag: ModuleTag) {
            if (shownTags.contains(tag)) {
                shownTags.remove(tag)
            } else {
                shownTags.add(tag)
            }
        }

        fun isTagShown(tag: ModuleTag) = shownTags.contains(tag)
    }
}
