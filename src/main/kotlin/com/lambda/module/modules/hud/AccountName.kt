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

package com.lambda.module.modules.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.lambda.gui.core.observeSafe
import com.lambda.gui.hud.HudText
import com.lambda.module.HudModule
import com.lambda.module.ModuleTag

@Suppress("unused")
object AccountName : HudModule(
    name = "AccountName",
    description = "Displays the current accounts name",
    tag = ModuleTag.HUD
) {
    @Composable
    override fun Content() {
        val name by observeSafe { player.name.string }
        name?.let { HudText(it) }
    }
}