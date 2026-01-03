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

package com.lambda.module.modules.render

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import java.awt.Color

object ExtraTab : Module(
    name = "ExtraTab",
    description = "Adds more tabs to the main menu",
    tag = ModuleTag.RENDER,
) {
    @JvmStatic val tabEntries by setting("Tab Entries", 80L, 1L..500L, 1L)
    @JvmStatic val rows by setting("Rows", 20, 1..100, 1)
    @JvmStatic val friendsOnly by setting("Friends Only", false)
    @JvmStatic val highlightFriends by setting("Highlight Friends", true)
    @JvmStatic val friendColor by setting("Friend Color", Color(120, 120, 255, 255), "The color friends will be highlighted") { highlightFriends }
}
